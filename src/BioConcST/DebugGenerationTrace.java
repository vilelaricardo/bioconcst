package BioConcST;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.random.RandomGenerator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jenetics.Alterer;
import io.jenetics.Chromosome;
import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;
import io.jenetics.MutatorResult;
import io.jenetics.Optimize;
import io.jenetics.Phenotype;
import io.jenetics.Selector;
import io.jenetics.SinglePointCrossover;
import io.jenetics.SwapMutator;
import io.jenetics.engine.Codec;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.Limits;
import io.jenetics.engine.Problem;
import io.jenetics.util.ISeq;
import io.jenetics.util.MSeq;
import io.jenetics.util.Seq;

import CoverageInst.ClassScanner;
import CoverageInst.CoverageEvaluator;
import CoverageInst.CoverageInstRun;
import CoverageInst.GraphDistance;
import CoverageInst.ProcessInstance;
import CoverageInst.RequiredEdge;
import CoverageInst.RequiredElementsGenerator;
import CoverageInst.RoleLink;
import CoverageInst.Topology;

/**
 * One-off diagnostic driver (matches the DebugXxx.java precedent already in
 * this package) - NOT wired into any production strategy. Runs a real,
 * tiny (populationSize small on purpose, so the trace stays readable)
 * GA_COVINST search on quorum-handshake, with every operator (both
 * mutators, crossover, both selectors) wrapped in a logging decorator that
 * records the exact before/after state - genotype values, which parents
 * crossed to produce which children, which individuals actually mutated,
 * and which were selected as survivors vs. offspring parents - then writes
 * the whole thing as a readable Markdown report.
 *
 * java BioConcST.DebugGenerationTrace <config.json> <generations> <outputMdPath>
 */
public class DebugGenerationTrace {

	private static final StringBuilder OUT = new StringBuilder();
	private static int generationCounter = 0;
	// Keyed by the genotype's own gene-value string (genesOf(...)) - good
	// enough here since a debug run's tiny population rarely produces two
	// DIFFERENT individuals with identical genes at the same moment, and
	// even if it did, they'd have the identical real breakdown anyway
	// (same test data -> same distance-to-target computation, modulo the
	// genuinely-racy MESSAGE-kind edges, which is exactly the kind of
	// subtlety this whole exercise is trying to make visible, not hide).
	private static final java.util.Map<String, String> BREAKDOWN_BY_GENES = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Mirrors CoverageInstFitnessFunction.evaluate()/toTestFitness() EXACTLY
	 * (same run.runTestCase call, same CoverageEvaluator.evaluate call, same
	 * distance formula) so the returned TestFitness is byte-for-byte what
	 * production would compute - but ALSO records, per uncovered required
	 * edge, that edge's own individual GraphDistance.compute() contribution
	 * (not just the final sum), stashed by genotype for logGeneration to
	 * print later. Deliberately NOT added to CoverageInstFitnessFunction
	 * itself - this is debug-only instrumentation, kept out of the
	 * production fitness path entirely.
	 */
	private static TestFitness evaluateWithBreakdown(Genotype<IntegerGene> genotype, CoverageInstRun run,
			List<ProcessSpec> testSetupProcesses, List<RequiredEdge> required, List<Integer> processIds,
			int execTimeLimitMs, int inputGeneCount,
			java.util.Map<String, List<GraphDistance.CausalSource>> causalDistance) {
		String genesKey = genesOf(genotype);
		List<CoverageInstRun.ProcessLaunchSpec> launchSpecs = CoverageInstFitnessFunction.buildLaunchSpecs(genotype,
				testSetupProcesses, inputGeneCount);
		try {
			CoverageInstRun.TestCaseResult result = run.runTestCase(nextTestId(), launchSpecs, execTimeLimitMs);
			if (!result.allProcessesCompleted) {
				BREAKDOWN_BY_GENES.put(genesKey, "(processo não completou - distância máxima)");
				return new TestFitness(1.0, 0.0, "[]");
			}
			CoverageEvaluator.Result evalResult = CoverageEvaluator.evaluate(required, result.coverageDir, processIds);

			StringBuilder breakdown = new StringBuilder();
			double sum = 0.0;
			for (RequiredEdge edge : evalResult.uncovered) {
				double d = GraphDistance.compute(edge, run.flowGraphs(), run.syncEdgeBlocks(), run.branchPredicates(),
						evalResult.observedNodesByProcess, evalResult.observedOperandsByProcess,
						evalResult.observedSenderByReceiveEdge, causalDistance);
				sum += d;
				int idx = required.indexOf(edge);
				if (breakdown.length() > 0) {
					breakdown.append(", ");
				}
				breakdown.append(idx).append(":").append(String.format("%.3f", d));
			}
			BREAKDOWN_BY_GENES.put(genesKey, breakdown.length() > 0 ? breakdown.toString() : "(tudo coberto)");
			double distance = evalResult.totalRequired > 0 ? sum / evalResult.totalRequired : 0.0;

			StringBuilder json = new StringBuilder("[");
			for (int i = 0; i < required.size(); i++) {
				if (i > 0) {
					json.append(",");
				}
				boolean covered = evalResult.covered.contains(required.get(i));
				json.append("{\"state\":\"").append(covered ? "COVERED" : "UNCOVERED").append("\"}");
			}
			json.append("]");
			return new TestFitness(distance, evalResult.coveragePercent(), json.toString());
		} catch (IOException | InterruptedException e) {
			BREAKDOWN_BY_GENES.put(genesKey, "(erro: " + e.getMessage() + ")");
			return new TestFitness(1.0, 0.0, "[]");
		}
	}

	private static final java.util.concurrent.atomic.AtomicInteger TEST_ID = new java.util.concurrent.atomic.AtomicInteger(
			0);

	private static int nextTestId() {
		return TEST_ID.getAndIncrement();
	}

	public static void main(String[] args) throws Exception {
		String configPath = args.length > 0 ? args[0] : "config/quorum-handshake-covinst.json";
		int generations = args.length > 1 ? Integer.parseInt(args[1]) : 3;
		String outPath = args.length > 2 ? args[2] : "debug-generation-trace.md";
		int populationSizeArg = args.length > 3 ? Integer.parseInt(args[3]) : 6;

		ExperimentConfig config = ExperimentConfig.load(configPath);
		BenchmarkConfig benchmark = config.benchmark;
		GAConfig ga = config.ga;
		CoverageInstConfig ci = benchmark.coverageInst;

		// Deliberately small population so every individual can be shown in
		// full in the report - the point here is legibility, not a
		// realistic search budget.
		int populationSize = populationSizeArg;

		File filesPath = new File(benchmark.path);
		File workDir = new File("./cov-debug-generation-trace-" + benchmark.name);
		org.apache.commons.io.FileUtils.deleteQuietly(workDir);
		CoverageInstRun run = CoverageInstRun.prepare(filesPath, workDir);

		List<ProcessInstance> processes = new ArrayList<>();
		List<Integer> processIds = new ArrayList<>();
		for (ProcessSpec spec : benchmark.testSetupProcesses) {
			List<String> classNames = ci.extraClassesByRole != null && ci.extraClassesByRole.containsKey(spec.className)
					? ci.extraClassesByRole.get(spec.className)
					: List.of(spec.className);
			processes.add(ClassScanner.scanProcess(run.instrumentedDir(), spec.id, spec.className, classNames));
			processIds.add(spec.id);
		}

		List<RoleLink> roleLinks = new ArrayList<>();
		if (ci.roleLinks != null) {
			for (RoleLinkSpec link : ci.roleLinks) {
				roleLinks.add(new RoleLink(link.from, link.to));
			}
		}
		Topology topology = new Topology(roleLinks,
				ci.fixedMessageTargets != null ? ci.fixedMessageTargets : java.util.Map.of(),
				ci.fixedMessageSources != null ? ci.fixedMessageSources : java.util.Map.of(),
				ci.identityGroups != null ? ci.identityGroups : java.util.Map.of(),
				ci.causalDistance != null ? ci.causalDistance : java.util.Map.of());
		List<RequiredEdge> required = RequiredElementsGenerator.generate(processes, topology);

		int execTimeLimitMs = benchmark.execTimeLimitMs != null ? benchmark.execTimeLimitMs
				: BenchmarkConfig.DEFAULT_EXEC_TIME_LIMIT_MS;

		List<IntegerChromosome> chromosomes = new ArrayList<>();
		if (benchmark.argumentRanges != null && !benchmark.argumentRanges.isEmpty()) {
			for (int i = 0; i < benchmark.argumentRanges.size(); i++) {
				chromosomes.add(
						IntegerChromosome.of(benchmark.argumentRanges.get(i).min, benchmark.argumentRanges.get(i).max, 1));
			}
		} else {
			chromosomes.add(IntegerChromosome.of(ga.min, ga.max, benchmark.argumentsLength));
		}
		Genotype<IntegerGene> genotype = Genotype.of(chromosomes.get(0),
				chromosomes.subList(1, chromosomes.size()).toArray(new IntegerChromosome[0]));

		int inputGeneCountSum = 0;
		for (IntegerChromosome c : chromosomes) {
			inputGeneCountSum += c.length();
		}
		final int inputGeneCount = inputGeneCountSum;

		Problem<Genotype<IntegerGene>, IntegerGene, TestFitness> problem = Problem.of(
				gt -> evaluateWithBreakdown(gt, run, benchmark.testSetupProcesses, required, processIds,
						execTimeLimitMs, inputGeneCount, topology.causalDistance),
				Codec.of(genotype, gt -> gt));

		Selector<IntegerGene, TestFitness> survivorsSelector = new LoggingSelector("SOBREVIVENTES (survivorsSelector)",
				new FuzzySelector<>());
		Selector<IntegerGene, TestFitness> offspringSelector = new LoggingSelector("PAIS DA PRÓXIMA GERAÇÃO (offspringSelector)",
				new FuzzySelector<>());

		ExecutorService executor = Executors.newFixedThreadPool(4);

		Alterer<IntegerGene, TestFitness> swap = new LoggingSwapMutator(ga.mutationRate);
		Alterer<IntegerGene, TestFitness> mutator = new LoggingMutator(ga.mutationRate);
		Alterer<IntegerGene, TestFitness> crossover = new LoggingCrossover(ga.crossoverRate);

		section("# Debug: rastro geração a geração — " + benchmark.name);
		OUT.append("\n_Gerado em ").append(java.time.LocalDateTime.now()).append("_\n\n");
		OUT.append("População reduzida pra ").append(populationSize)
				.append(" indivíduos (em vez do valor real da config) só pra ficar legível — tudo mais (fitness, ")
				.append("seleção, cruzamento, mutação) é o motor real de produção, sem simplificação.\n\n");
		OUT.append("**Como ler**: os blocos SOBREVIVENTES/PAIS/CRUZAMENTO/MUTAÇÃO que aparecem ANTES de uma tabela ")
				.append("\"Geração N\" são os eventos que PRODUZIRAM aquela geração a partir da população da geração ")
				.append("anterior (ou da população inicial aleatória, no caso da Geração 1, que não é mostrada por ")
				.append("si só). A tabela em si é o RESULTADO já avaliado pelo fitness.\n\n");
		OUT.append("**Honestidade**: com só ").append(populationSize).append(" indivíduos e ").append(generations)
				.append(" gerações, não é esperado que esse alvo específico (quorum-handshake) avance ")
				.append("de verdade - o objetivo aqui é mostrar o MECANISMO funcionando, não uma busca bem-sucedida.\n\n");

		OUT.append("## Elementos exigidos (").append(required.size()).append(" no total)\n\n");
		for (int i = 0; i < required.size(); i++) {
			OUT.append(i).append(". `").append(required.get(i)).append("`\n");
		}
		OUT.append("\n");

		Engine<IntegerGene, TestFitness> engine = Engine.builder(problem).minimizing().populationSize(populationSize)
				.survivorsFraction(ga.survivorsFraction).offspringFraction(ga.offspringFraction)
				.survivorsSelector(survivorsSelector).offspringSelector(offspringSelector)
				.alterers(swap, mutator, crossover).executor(executor).build();

		ISeq<Phenotype<IntegerGene, TestFitness>> finalPop = engine.stream().limit(Limits.byFixedGeneration(generations))
				.peek(DebugGenerationTrace::logGeneration).collect(EvolutionResult.toBestEvolutionResult()).population();

		executor.shutdown();

		Files.writeString(Path.of(outPath), OUT.toString());
		System.out.println("Wrote " + outPath);
	}

	private static void section(String header) {
		OUT.append(header).append("\n\n");
	}

	// Engine.executor() runs selectors/alterers for different candidates
	// concurrently - appending piecemeal to the shared OUT buffer from
	// multiple threads interleaves individual .append() calls mid-block
	// (confirmed empirically: an early version produced garbled,
	// character-interleaved log lines). Building each log entry as its own
	// local StringBuilder first, then copying it into OUT in one call while
	// holding the lock, keeps every block atomic without needing to
	// serialize the engine itself.
	private static void appendAtomic(CharSequence block) {
		synchronized (OUT) {
			OUT.append(block);
		}
	}

	private static void logGeneration(EvolutionResult<IntegerGene, TestFitness> result) {
		generationCounter++;
		OUT.append("## Geração ").append(result.generation()).append("\n\n");

		// Population-level UNION coverage - what every real run in this
		// project actually reports (SuiteCoverage.unionCoveragePercent, the
		// same call CoverageInstStrategy's own .peek() makes) - NOT the
		// same thing as any single individual's own %, which is what the
		// per-row table below shows. Different individuals typically cover
		// DIFFERENT subsets of the required elements, so the union is
		// usually well above any one individual's number.
		double unionPercent = SuiteCoverage.unionCoveragePercent(result.population());
		java.util.Set<Integer> unionIndices = new java.util.TreeSet<>();
		for (Phenotype<IntegerGene, TestFitness> p : result.population()) {
			unionIndices.addAll(coveredIndices(p.fitness().getSyncEdgeRequirements()));
		}
		OUT.append("**Cobertura da POPULAÇÃO (união, o número que todo run real desse projeto reporta)**: ")
				.append(String.format("%.1f%%", unionPercent)).append(" — elementos únicos cobertos por ALGUÉM: ")
				.append(unionIndices).append("\n\n");

		OUT.append("| # | Genes (TESTDATA) | Distância | Cobertura (deste indivíduo) | Elementos cobertos (deste indivíduo) | Distância por aresta NÃO coberta (índice:valor) |\n");
		OUT.append("|---|---|---|---|---|---|\n");
		int idx = 0;
		for (Phenotype<IntegerGene, TestFitness> p : result.population()) {
			String genes = genesOf(p.genotype());
			TestFitness f = p.fitness();
			String covered = coveredSummary(f.getSyncEdgeRequirements());
			String breakdown = BREAKDOWN_BY_GENES.getOrDefault(genes, "?");
			OUT.append("| ").append(idx).append(" | `").append(genes).append("` | ").append(String.format("%.4f", f.getDistance()))
					.append(" | ").append(String.format("%.1f%%", f.getCoverage())).append(" | ").append(covered)
					.append(" | `").append(breakdown).append("` |\n");
			idx++;
		}
		OUT.append("\n");
	}

	private static List<Integer> coveredIndices(String syncEdgeRequirementsJson) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode arr = mapper.readTree(syncEdgeRequirementsJson);
			List<Integer> covered = new ArrayList<>();
			for (int i = 0; i < arr.size(); i++) {
				if ("COVERED".equals(arr.get(i).get("state").asText())) {
					covered.add(i);
				}
			}
			return covered;
		} catch (Exception e) {
			return List.of();
		}
	}

	private static String genesOf(Genotype<IntegerGene> gt) {
		StringBuilder sb = new StringBuilder();
		for (Chromosome<IntegerGene> c : gt) {
			for (IntegerGene g : c) {
				if (sb.length() > 0) {
					sb.append(", ");
				}
				sb.append(g.allele());
			}
		}
		return sb.toString();
	}

	private static String coveredSummary(String syncEdgeRequirementsJson) {
		List<Integer> covered = coveredIndices(syncEdgeRequirementsJson);
		return covered.isEmpty() ? "(nenhum)" : covered.toString();
	}

	/** Wraps any Selector to log exactly who went in and who came out. */
	private static final class LoggingSelector implements Selector<IntegerGene, TestFitness> {
		private final String label;
		private final Selector<IntegerGene, TestFitness> delegate;

		LoggingSelector(String label, Selector<IntegerGene, TestFitness> delegate) {
			this.label = label;
			this.delegate = delegate;
		}

		@Override
		public ISeq<Phenotype<IntegerGene, TestFitness>> select(Seq<Phenotype<IntegerGene, TestFitness>> population,
				int count, Optimize opt) {
			ISeq<Phenotype<IntegerGene, TestFitness>> selected = delegate.select(population, count, opt);
			StringBuilder block = new StringBuilder();
			block.append("**").append(label).append("** — pediu ").append(count).append(" de ").append(population.size())
					.append(" indivíduos, selecionou:\n\n");
			for (Phenotype<IntegerGene, TestFitness> p : selected) {
				block.append("- `").append(genesOf(p.genotype())).append("` (dist=")
						.append(String.format("%.4f", p.fitness().getDistance())).append(", cov=")
						.append(String.format("%.1f%%", p.fitness().getCoverage())).append(")\n");
			}
			block.append("\n");
			appendAtomic(block);
			return selected;
		}
	}

	/** Subclasses SinglePointCrossover to log the exact parent/child gene values of every crossed pair. */
	private static final class LoggingCrossover extends SinglePointCrossover<IntegerGene, TestFitness> {
		LoggingCrossover(double probability) {
			super(probability);
		}

		@Override
		protected int crossover(MSeq<IntegerGene> that, MSeq<IntegerGene> other) {
			String beforeA = allelesOf(that);
			String beforeB = allelesOf(other);
			int changed = super.crossover(that, other);
			String afterA = allelesOf(that);
			String afterB = allelesOf(other);
			if (changed > 0) {
				StringBuilder block = new StringBuilder();
				block.append("**CRUZAMENTO** — Pai A `").append(beforeA).append("` + Pai B `").append(beforeB)
						.append("` -> Filho A `").append(afterA).append("` + Filho B `").append(afterB)
						.append("` (").append(changed).append(" genes trocados)\n\n");
				appendAtomic(block);
			}
			return changed;
		}

		private String allelesOf(MSeq<IntegerGene> seq) {
			StringBuilder sb = new StringBuilder();
			for (IntegerGene g : seq) {
				if (sb.length() > 0) {
					sb.append(", ");
				}
				sb.append(g.allele());
			}
			return sb.toString();
		}
	}

	/** Subclasses Mutator to log every individual's before/after genotype. */
	private static final class LoggingMutator extends io.jenetics.Mutator<IntegerGene, TestFitness> {
		LoggingMutator(double probability) {
			super(probability);
		}

		@Override
		protected MutatorResult<Phenotype<IntegerGene, TestFitness>> mutate(Phenotype<IntegerGene, TestFitness> phenotype,
				long generation, double p, RandomGenerator random) {
			String before = genesOf(phenotype.genotype());
			MutatorResult<Phenotype<IntegerGene, TestFitness>> result = super.mutate(phenotype, generation, p, random);
			String after = genesOf(result.result().genotype());
			if (result.mutations() > 0) {
				StringBuilder block = new StringBuilder();
				block.append("**MUTAÇÃO (Mutator)** — `").append(before).append("` -> `").append(after).append("` (")
						.append(result.mutations()).append(" genes alterados)\n\n");
				appendAtomic(block);
			}
			return result;
		}
	}

	/** Subclasses SwapMutator to log every chromosome's before/after allele order. */
	private static final class LoggingSwapMutator extends SwapMutator<IntegerGene, TestFitness> {
		LoggingSwapMutator(double probability) {
			super(probability);
		}

		@Override
		protected MutatorResult<Chromosome<IntegerGene>> mutate(Chromosome<IntegerGene> chromosome, double p,
				RandomGenerator random) {
			StringBuilder before = new StringBuilder();
			for (IntegerGene g : chromosome) {
				if (before.length() > 0) {
					before.append(", ");
				}
				before.append(g.allele());
			}
			MutatorResult<Chromosome<IntegerGene>> result = super.mutate(chromosome, p, random);
			if (result.mutations() > 0) {
				StringBuilder after = new StringBuilder();
				for (IntegerGene g : result.result()) {
					if (after.length() > 0) {
						after.append(", ");
					}
					after.append(g.allele());
				}
				StringBuilder block = new StringBuilder();
				block.append("**MUTAÇÃO (SwapMutator)** — `").append(before).append("` -> `").append(after).append("` (")
						.append(result.mutations()).append(" trocas)\n\n");
				appendAtomic(block);
			}
			return result;
		}
	}
}
