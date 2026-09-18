package BioConcST;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import CoverageInst.ClassScanner;
import CoverageInst.CoverageInstRun;
import CoverageInst.ProcessInstance;
import CoverageInst.RacePoint;
import CoverageInst.RacePoints;
import CoverageInst.RequiredEdge;
import CoverageInst.RequiredElementsGenerator;
import CoverageInst.RoleLink;
import CoverageInst.Topology;
import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;
import io.jenetics.Phenotype;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.util.ISeq;

/**
 * A genuine, uncorrelated random-sampling baseline for CoverageInst
 * benchmarks: draws N independent genotypes from the same domain a
 * GA_COVINST run would use, and scores each with exactly one real
 * evaluate() call - no Engine, no selection, no crossover/mutation, no
 * generations, no HallOfFame/dedup interceptors.
 *
 * This exists because Jenetics' own Engine cannot be configured to behave
 * this way: Engine.evolve() unconditionally re-evaluates the
 * post-selection/alteration population on EVERY call, generation 1
 * included (confirmed by reading Engine.java:194-268 in jenetics 9.0.0's
 * own sources - see the "Initial evaluation" block followed by a second,
 * unconditional "eval(pop)" on the selected-and-altered population). So
 * even populationSize=N/generations=1 with a random selector and zero
 * mutation rate still performs a second, hidden evaluation round with
 * real selection pressure baked in - confirmed empirically on
 * quorum-handshake as ~1157 actual evaluations against a nominal budget
 * of 480 (see sync-claude-codex.md, 2026-09-13/14 entries, for the
 * investigation that established this).
 *
 * config.ga.populationSize is reused as the sample size N, so a benchmark
 * config only needs "strategy" changed to "RANDOM_COVINST" to switch a
 * GA_COVINST config into this baseline. generations/mutationRate/
 * crossoverRate/survivorsFraction/offspringFraction are all ignored here.
 */
public class RandomCoverageInstStrategy implements SearchStrategy {

	@Override
	public SolutionResult run(ExperimentConfig config, BenchmarkConfig benchmark, File filesPath,
			ProcessBuilder instrumentation, String[] testSetup) {
		GAConfig ga = config.ga;

		try {
			CoverageInstConfig ci = benchmark.coverageInst;
			if (ci == null) {
				throw new IllegalStateException(
						"benchmark.coverageInst is required for strategy RANDOM_COVINST (benchmark: "
								+ benchmark.name + ")");
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
					ci.identityGroups != null ? ci.identityGroups : Map.of(),
					ci.causalDistance != null ? ci.causalDistance : Map.of());

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
			// Race points still get their own random gene (uniform over
			// candidateSenderIds) below, for parity with GA_COVINST's
			// genotype shape - but no operator ever biases them here.
			List<RacePoint> racePoints = Boolean.TRUE.equals(ci.raceGene) ? RacePoints.detect(required) : List.of();

			int execTimeLimitMs = benchmark.execTimeLimitMs != null ? benchmark.execTimeLimitMs
					: BenchmarkConfig.DEFAULT_EXEC_TIME_LIMIT_MS;

			List<IntegerChromosome> chromosomeFactories = new ArrayList<>();
			if (benchmark.argumentRanges != null && !benchmark.argumentRanges.isEmpty()) {
				for (int i = 0; i < benchmark.argumentRanges.size(); i++) {
					chromosomeFactories.add(IntegerChromosome.of(benchmark.argumentRanges.get(i).min,
							benchmark.argumentRanges.get(i).max, 1));
				}
			} else {
				chromosomeFactories.add(IntegerChromosome.of(ga.min, ga.max, benchmark.argumentsLength));
			}
			int inputGeneCountSum = 0;
			for (IntegerChromosome chromosome : chromosomeFactories) {
				inputGeneCountSum += chromosome.length();
			}
			final int inputGeneCount = inputGeneCountSum;
			for (RacePoint racePoint : racePoints) {
				chromosomeFactories
						.add(IntegerChromosome.of(0, Math.max(1, racePoint.candidateSenderIds.size()), 1));
			}

			CoverageInstFitnessFunction fitnessFn = new CoverageInstFitnessFunction(run,
					benchmark.testSetupProcesses, required, processIds, execTimeLimitMs, racePoints, inputGeneCount,
					topology.causalDistance);

			int sampleSize = ga.populationSize;
			List<Double> cumulativeCoverageHistory = new ArrayList<>();
			List<Integer> evaluationCountHistory = new ArrayList<>();
			Set<String> coveredEdgeKeysSoFar = new HashSet<>();
			ObjectMapper mapper = new ObjectMapper();
			List<Phenotype<IntegerGene, TestFitness>> samples = new ArrayList<>();

			for (int i = 0; i < sampleSize; i++) {
				List<IntegerChromosome> chromosomes = new ArrayList<>();
				for (IntegerChromosome factory : chromosomeFactories) {
					// newInstance() draws a fresh, independent random
					// chromosome respecting the same [min,max]/length the
					// factory was built with - this is the one call in
					// this whole class that produces a new sample; no
					// Engine, no selection, no alteration touches it.
					chromosomes.add(factory.newInstance());
				}
				Genotype<IntegerGene> genotype = Genotype.of(chromosomes.get(0),
						chromosomes.subList(1, chromosomes.size()).toArray(new IntegerChromosome[0]));
				TestFitness fitness = fitnessFn.evaluate(genotype);
				samples.add(Phenotype.of(genotype, i + 1, fitness));

				try {
					JsonNode elements = mapper.readTree(fitness.getSyncEdgeRequirements());
					for (int e = 0; e < elements.size() && e < required.size(); e++) {
						if ("COVERED".equals(elements.get(e).get("state").asText())) {
							coveredEdgeKeysSoFar.add(required.get(e).toString());
						}
					}
				} catch (Exception e) {
					// Malformed/empty JSON only happens for a timed-out
					// individual ("[]") - nothing new to add for it.
				}
				cumulativeCoverageHistory
						.add(required.isEmpty() ? 0.0 : 100.0 * coveredEdgeKeysSoFar.size() / required.size());
				evaluationCountHistory.add(fitnessFn.evaluationCount());
			}

			ISeq<Phenotype<IntegerGene, TestFitness>> bestList = ISeq.of(samples);
			SolutionResult solutionResult = new SolutionResult(cumulativeCoverageHistory,
					EvolutionStatistics.ofNumber(), bestList, bestList);
			solutionResult.setCumulativeCoverage(cumulativeCoverageHistory);
			solutionResult.setEvaluationCount(fitnessFn.evaluationCount());
			solutionResult.setEvaluationCountHistory(evaluationCountHistory);
			List<String> uncoveredElementKeys = new ArrayList<>();
			for (RequiredEdge edge : required) {
				if (!coveredEdgeKeysSoFar.contains(edge.toString())) {
					uncoveredElementKeys.add(edge.toString());
				}
			}
			solutionResult.setUncoveredElementKeys(uncoveredElementKeys);
			return solutionResult;
		} catch (IOException e) {
			throw new RuntimeException("RandomCoverageInstStrategy failed to prepare " + benchmark.name, e);
		}
	}
}
