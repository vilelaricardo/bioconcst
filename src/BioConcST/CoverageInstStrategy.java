package BioConcST;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import CoverageInst.ClassScanner;
import CoverageInst.CoverageInstRun;
import CoverageInst.PcfgVisualizer;
import CoverageInst.ProcessInstance;
import CoverageInst.RequiredEdge;
import CoverageInst.RequiredElementsGenerator;
import CoverageInst.RoleLink;
import CoverageInst.Topology;
import io.jenetics.Chromosome;
import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;
import io.jenetics.Mutator;
import io.jenetics.Phenotype;
import io.jenetics.Selector;
import io.jenetics.SinglePointCrossover;
import io.jenetics.SwapMutator;
import io.jenetics.engine.Codec;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionInterceptor;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.engine.Limits;
import io.jenetics.engine.Problem;
import io.jenetics.util.ISeq;

/**
 * Runs the same Engine/FuzzySelector/HallOfFame wiring as
 * GeneticAlgorithmStrategy and StubGeneticAlgorithmStrategy, but against a
 * real, native, ValiPar-free execution (CoverageInstFitnessFunction) instead
 * of ValiPar's Docker/container-pool path or the in-memory stub - see
 * project_valipar_spurious_sync_edges memory for why this exists: ValiPar's
 * static analysis structurally over-generates required sync edges for
 * benchmarks with duplicate-class process instances or runtime-resolved
 * socket destinations, capping quorum-handshake/combined-handshake's real
 * achievable coverage below 100% no matter how the search is tuned.
 * CoverageInst resolves sync-edge identity from the real running program
 * instead, at the cost of only supporting the small set of primitives
 * ClassInstrumenter recognizes (see its own javadoc for the current scope).
 *
 * Wired up for any benchmark that declares a "coverageInst" block in its
 * config (see CoverageInstConfig) - nothing here is specific to any one
 * benchmark; a future benchmark needs only that config block, never a
 * change to this class or to the CoverageInst package.
 */
public class CoverageInstStrategy implements SearchStrategy {

	@Override
	public SolutionResult run(ExperimentConfig config, BenchmarkConfig benchmark, File filesPath,
			ProcessBuilder instrumentation, String[] testSetup) {
		GAConfig ga = config.ga;

		try {
			CoverageInstConfig ci = benchmark.coverageInst;
			if (ci == null) {
				throw new IllegalStateException(
						"benchmark.coverageInst is required for strategy GA_COVINST (benchmark: " + benchmark.name
								+ ")");
			}
			List<RoleLink> roleLinks = new ArrayList<>();
			if (ci.roleLinks != null) {
				for (RoleLinkSpec link : ci.roleLinks) {
					roleLinks.add(new RoleLink(link.from, link.to));
				}
			}
			Topology topology = new Topology(roleLinks,
					ci.fixedMessageTargets != null ? ci.fixedMessageTargets : Map.of(),
					ci.fixedMessageSources != null ? ci.fixedMessageSources : Map.of(),
					ci.identityGroups != null ? ci.identityGroups : Map.of());

			File workDir = new File("./cov-experiment-" + benchmark.name);
			org.apache.commons.io.FileUtils.deleteQuietly(workDir);
			CoverageInstRun run = CoverageInstRun.prepare(filesPath, workDir);

			List<ProcessInstance> processes = new ArrayList<>();
			List<Integer> processIds = new ArrayList<>();
			for (ProcessSpec spec : benchmark.testSetupProcesses) {
				List<String> classNames = ci.extraClassesByRole != null
						&& ci.extraClassesByRole.containsKey(spec.className) ? ci.extraClassesByRole.get(spec.className)
								: List.of(spec.className);
				processes.add(ClassScanner.scanProcess(run.instrumentedDir(), spec.id, spec.className, classNames));
				processIds.add(spec.id);
			}

			List<RequiredEdge> required = RequiredElementsGenerator.generate(processes, topology);

			int execTimeLimitMs = benchmark.execTimeLimitMs != null ? benchmark.execTimeLimitMs
					: BenchmarkConfig.DEFAULT_EXEC_TIME_LIMIT_MS;
			CoverageInstFitnessFunction fitnessFn = new CoverageInstFitnessFunction(run,
					benchmark.testSetupProcesses, required, processIds, execTimeLimitMs);

			Genotype<IntegerGene> genotype;
			if (benchmark.argumentRanges != null && !benchmark.argumentRanges.isEmpty()) {
				IntegerChromosome[] chromosomes = new IntegerChromosome[benchmark.argumentRanges.size()];
				for (int i = 0; i < benchmark.argumentRanges.size(); i++) {
					chromosomes[i] = IntegerChromosome.of(benchmark.argumentRanges.get(i).min,
							benchmark.argumentRanges.get(i).max, 1);
				}
				genotype = Genotype.of(chromosomes[0],
						java.util.Arrays.copyOfRange(chromosomes, 1, chromosomes.length));
			} else {
				genotype = Genotype.of(IntegerChromosome.of(ga.min, ga.max, benchmark.argumentsLength));
			}

			Problem<Genotype<IntegerGene>, IntegerGene, TestFitness> problem = Problem.of(fitnessFn::evaluate,
					Codec.of(genotype, gt -> gt));

			Selector<IntegerGene, TestFitness> survivorsSelector = new FuzzySelector<>();
			Selector<IntegerGene, TestFitness> offspringSelector = new FuzzySelector<>();

			// Hall-of-fame first, dedup second - see BioConcSTCore.generatorEvolution()
			// for why the order matters.
			EvolutionInterceptor<IntegerGene, TestFitness> uniqueInterceptor = EvolutionResult.toUniquePopulation();
			EvolutionInterceptor<IntegerGene, TestFitness> hallOfFameInterceptor = HallOfFame.interceptor();
			EvolutionInterceptor<IntegerGene, TestFitness> combinedInterceptor = EvolutionInterceptor
					.ofAfter(result -> uniqueInterceptor.after(hallOfFameInterceptor.after(result)));

			final ExecutorService executor = Executors.newFixedThreadPool(ga.threadExecutors);

			EvolutionStatistics<TestFitness, ?> statistics = EvolutionStatistics.ofNumber();
			List<Double> syncCoverageHistory = new ArrayList<>();
			double[] bestCoverageSoFar = { -1.0 };
			AtomicReference<ISeq<Phenotype<IntegerGene, TestFitness>>> bestPopulation = new AtomicReference<>(
					ISeq.empty());

			// Live PCFG progress: overwritten every generation with the
			// population's cumulative union so far (which sync edges some
			// individual has proven coverable) - open pcfg.svg in a viewer
			// that auto-reloads (most browsers/image viewers do on file
			// change) to watch the diagram's dashed sync edges turn green as
			// the search runs, instead of only seeing a final snapshot.
			Set<String> coveredEdgeKeysSoFar = new HashSet<>();
			File progressDir = new File(workDir, "pcfg-progress");
			ObjectMapper mapper = new ObjectMapper();

			ISeq<Phenotype<IntegerGene, TestFitness>> results;
			try {
				Engine<IntegerGene, TestFitness> engine = Engine.builder(problem).minimizing()
						.survivorsFraction(ga.survivorsFraction).offspringFraction(ga.offspringFraction)
						.survivorsSelector(survivorsSelector).offspringSelector(offspringSelector)
						.populationSize(ga.populationSize)
						// SwapMutator alone is a documented no-op on any
						// length-1 chromosome (Jenetics' own source:
						// SwapMutator.mutate() short-circuits to "0
						// mutations" whenever chromosome.length() <= 1) -
						// and benchmark.argumentRanges (used whenever
						// arguments need per-argument bounds, e.g.
						// quorum-handshake's two independent [0,1000]
						// values) builds ONE single-gene chromosome PER
						// ARGUMENT. Without Mutator here, the population's
						// achievable numeric values are permanently fixed at
						// whatever random values existed in the initial
						// population - crossover can only recombine them,
						// never introduce a new one - which silently caps
						// how well the search can do on any narrow-window
						// requirement (confirmed empirically: quorum-
						// handshake's celebrate branch, gated on both
						// peers' values landing in a 4%-wide window,
						// depended entirely on whether generation 0's
						// random seed happened to already contain a lucky
						// value in each slot, independent of how good the
						// fitness gradient - see GraphDistance/BranchDistance
						// - actually is).
						.alterers(new SwapMutator<>(ga.mutationRate), new Mutator<>(ga.mutationRate),
								new SinglePointCrossover<>(ga.crossoverRate))
						.executor(executor).interceptor(combinedInterceptor).build();

				results = engine.stream().limit(Limits.byFixedGeneration(ga.generations)).peek(statistics)
						.peek(result -> {
							double genCoverage = SuiteCoverage.unionCoveragePercent(result.population());
							syncCoverageHistory.add(genCoverage);
							if (genCoverage > bestCoverageSoFar[0]) {
								bestCoverageSoFar[0] = genCoverage;
								bestPopulation.set(result.population());
							}
							updateProgressVisualization(result.population(), required, processes, run, mapper,
									coveredEdgeKeysSoFar, progressDir);
						}).map(EvolutionResult::bestPhenotype).collect(ISeq.toISeq());
			} finally {
				executor.shutdown();
			}

			SolutionResult solutionResult = new SolutionResult(syncCoverageHistory, statistics, results,
					bestPopulation.get());
			solutionResult.setReplayBundles(
					captureReplayBundles(bestPopulation.get(), benchmark.testSetupProcesses, required, mapper));
			return solutionResult;
		} catch (IOException e) {
			throw new RuntimeException("CoverageInstStrategy failed to prepare " + benchmark.name, e);
		}
	}

	// Folds this generation's population into the running "covered so far"
	// edge-key set and re-renders the combined PCFG progress view - same
	// JSON shape and per-individual iteration as
	// SuiteCoverage.unionCoveragePercent, just collecting which
	// RequiredEdge.toString() keys are covered instead of counting them.
	// Never lets a rendering failure (e.g. no Graphviz installed, or a
	// transient file-write race) interrupt the actual search - this view is
	// a convenience, not part of the result.
	private static void updateProgressVisualization(Iterable<Phenotype<IntegerGene, TestFitness>> population,
			List<RequiredEdge> required, List<ProcessInstance> processes, CoverageInstRun run, ObjectMapper mapper,
			Set<String> coveredEdgeKeysSoFar, File progressDir) {
		try {
			for (Phenotype<IntegerGene, TestFitness> individual : population) {
				JsonNode elements;
				try {
					elements = mapper.readTree(individual.fitness().getSyncEdgeRequirements());
				} catch (Exception e) {
					continue;
				}
				for (int i = 0; i < elements.size() && i < required.size(); i++) {
					if ("COVERED".equals(elements.get(i).get("state").asText())) {
						coveredEdgeKeysSoFar.add(required.get(i).toString());
					}
				}
			}
			PcfgVisualizer.renderCombined(progressDir, processes, run.flowGraphs(), run.syncEdgeBlocks(),
					run.branchPredicates(), required, coveredEdgeKeysSoFar);
		} catch (Exception e) {
			System.err.println("CoverageInstStrategy: PCFG progress view update failed (non-fatal): " + e);
		}
	}

	// Builds each champion's bundle straight from the TestFitness Jenetics
	// already cached for it during the actual search - NOT by re-running the
	// individual again now. An earlier version did re-run each champion here
	// to capture "a fresh trace"; that was wrong in exactly the way the user
	// caught: a surviving individual is only ever executed once (Jenetics
	// never re-evaluates an unchanged genotype), so a second, later execution
	// of a real concurrent program is an entirely different, independently-
	// raced run - its trace has nothing to do with the one that actually
	// earned the individual its fitness. CoverageInstFitnessFunction now
	// captures the ReplaySchedule at the only moment it's valid to: inside
	// evaluate(), from that same call's own trace - see TestFitness's
	// javadoc. This is CoverageInst's answer to "temos o cromossomo com
	// caminho e com entrada de teste?": pairing each champion's test input
	// with the schedule that reproduces the EXACT execution CoverageInst
	// measured its coverage from (see CoverageInst.ReplayMain).
	private static List<ReplayBundle> captureReplayBundles(ISeq<Phenotype<IntegerGene, TestFitness>> population,
			List<ProcessSpec> testSetupProcesses, List<RequiredEdge> required, ObjectMapper mapper) {
		List<ReplayBundle> bundles = new ArrayList<>();
		for (Phenotype<IntegerGene, TestFitness> phenotype : population) {
			Genotype<IntegerGene> genotype = phenotype.genotype();
			TestFitness fitness = phenotype.fitness();
			List<CoverageInstRun.ProcessLaunchSpec> launchSpecs = CoverageInstFitnessFunction
					.buildLaunchSpecs(genotype, testSetupProcesses);

			int coveredCount = 0;
			try {
				JsonNode elements = mapper.readTree(fitness.getSyncEdgeRequirements());
				for (JsonNode element : elements) {
					if ("COVERED".equals(element.get("state").asText())) {
						coveredCount++;
					}
				}
			} catch (Exception e) {
				// Malformed/empty JSON only happens for maxDistanceFitness()
				// (a timed-out individual, "[]") - 0 covered is correct there.
			}

			int[] genotypeValues = genotypeToArray(genotype);
			List<ReplayBundle.ProcessLaunch> processLaunches = new ArrayList<>();
			for (CoverageInstRun.ProcessLaunchSpec spec : launchSpecs) {
				processLaunches.add(new ReplayBundle.ProcessLaunch(spec.processId, spec.className, spec.args));
			}
			bundles.add(new ReplayBundle(genotypeValues, processLaunches, fitness.getCoverage(), coveredCount,
					required.size(), fitness.getReplaySchedule()));
		}
		return bundles;
	}

	private static int[] genotypeToArray(Genotype<IntegerGene> genotype) {
		List<Integer> values = new ArrayList<>();
		for (Chromosome<IntegerGene> chromosome : genotype) {
			for (IntegerGene gene : chromosome) {
				values.add(gene.allele());
			}
		}
		int[] array = new int[values.size()];
		for (int i = 0; i < array.length; i++) {
			array[i] = values.get(i);
		}
		return array;
	}
}
