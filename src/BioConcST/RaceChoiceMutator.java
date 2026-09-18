package BioConcST;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import CoverageInst.RacePoint;
import CoverageInst.RequiredEdge;
import io.jenetics.Chromosome;
import io.jenetics.Genotype;
import io.jenetics.IntegerGene;
import io.jenetics.Mutator;
import io.jenetics.MutatorResult;
import io.jenetics.Phenotype;
import io.jenetics.util.ISeq;

/**
 * Mutates ONLY the trailing race-choice genes appended to the genotype when
 * coverageInst.raceGene is enabled (see CoverageInstStrategy) - the
 * input-data genes are left entirely to the existing SwapMutator/Mutator/
 * SinglePointCrossover chain.
 *
 * Overrides Mutator's Phenotype-level mutate(...) (not the Chromosome- or
 * Gene-level overloads SwapMutator uses) because this is the only level in
 * Mutator's call chain that has access to the whole genotype at once (to
 * isolate the race-gene loci by position).
 *
 * "Which edges are still uncovered" is read from coveredEdgeKeysSoFar - the
 * SAME cumulative, whole-search union set CoverageInstStrategy's
 * updateProgressVisualization() already maintains (via .peek(), sequentially,
 * once per completed generation) - not from this individual's OWN
 * phenotype.fitness(). An earlier version tried the per-individual fitness
 * and found it almost always unavailable: SwapMutator/Mutator/
 * SinglePointCrossover all run before this alterer in the same chain, and
 * touching a phenotype invalidates its cached fitness until the Engine
 * re-evaluates the whole altered population - so most individuals reaching
 * this point had no fitness to read, and the LLM path silently starved
 * (near-100% fallback, confirmed empirically). The shared union set is both
 * always available AND a better signal: it targets what the whole search
 * still hasn't found, not just one individual's last run. Safe to read/write
 * without synchronization because Jenetics' EvolutionStream processes
 * generations strictly sequentially - alter() for generation N+1 always
 * happens after peek() has already observed generation N's evaluated
 * population, never concurrently with it.
 *
 * With probability llmProbability, asks OllamaRaceOracle (chain-of-thought
 * prompt, real source code, real launch args, real still-uncovered edges)
 * which candidate sender to force; otherwise, or on ANY oracle failure/
 * timeout/unparseable response, falls back to a uniform-random pick -
 * silently never letting a flaky/unreachable model stall or crash the GA,
 * but counted (llmSuccessCount/llmFallbackCount) so a run's numbers can be
 * checked for how often the LLM path was actually exercised, not silently
 * degenerating into random-only behavior.
 */
public final class RaceChoiceMutator extends Mutator<IntegerGene, TestFitness> {

	private final List<RacePoint> racePoints;
	private final int inputGeneCount;
	private final Set<String> coveredEdgeKeysSoFar;
	private final double llmProbability;
	private final OllamaRaceOracle oracle;
	private final List<ProcessSpec> testSetupProcesses;
	private final File benchmarkSourceDir;

	private final AtomicInteger llmSuccessCount = new AtomicInteger(0);
	private final AtomicInteger llmFallbackCount = new AtomicInteger(0);

	public RaceChoiceMutator(double probability, List<RacePoint> racePoints, int inputGeneCount,
			Set<String> coveredEdgeKeysSoFar, double llmProbability, OllamaRaceOracle oracle,
			List<ProcessSpec> testSetupProcesses, File benchmarkSourceDir) {
		super(probability);
		this.racePoints = racePoints;
		this.inputGeneCount = inputGeneCount;
		this.coveredEdgeKeysSoFar = coveredEdgeKeysSoFar;
		this.llmProbability = llmProbability;
		this.oracle = oracle;
		this.testSetupProcesses = testSetupProcesses;
		this.benchmarkSourceDir = benchmarkSourceDir;
	}

	public int llmSuccessCount() {
		return llmSuccessCount.get();
	}

	public int llmFallbackCount() {
		return llmFallbackCount.get();
	}

	@Override
	protected MutatorResult<Phenotype<IntegerGene, TestFitness>> mutate(Phenotype<IntegerGene, TestFitness> phenotype,
			long generation, double p, RandomGenerator random) {
		if (racePoints.isEmpty()) {
			return new MutatorResult<>(phenotype, 0);
		}

		List<Chromosome<IntegerGene>> chromosomes = new ArrayList<>();
		phenotype.genotype().forEach(chromosomes::add);

		// Computed once per phenotype (not once per race point) - the same
		// TESTDATA substitution CoverageInstFitnessFunction.evaluate() uses,
		// so the prompt sees the ACTUAL argument values this individual will
		// run with (e.g. which numeric value each Peer got) rather than the
		// bare "TESTDATA0"-style template - without this, the LLM has no way
		// to reason about which branch a candidate's code will actually take.
		List<CoverageInst.CoverageInstRun.ProcessLaunchSpec> launchSpecs = CoverageInstFitnessFunction
				.buildLaunchSpecs(phenotype.genotype(), testSetupProcesses, inputGeneCount);

		int mutations = 0;
		for (int i = 0; i < racePoints.size(); i++) {
			if (random.nextDouble() >= p) {
				continue;
			}
			RacePoint racePoint = racePoints.get(i);
			int candidateCount = racePoint.candidateSenderIds.size();
			int newWinnerIndex = candidateCount > 1 && oracle != null && random.nextDouble() < llmProbability
					? chooseViaLlmOrFallback(racePoint, launchSpecs, random)
					: random.nextInt(candidateCount);

			// IntegerGene.of(value, min, max) treats max as EXCLUSIVE (same
			// convention confirmed for IntegerChromosome.of - see
			// CoverageInstStrategy's genotype-construction comment): the
			// gene's own valid range must be [0, candidateCount), i.e. max=
			// candidateCount, NOT candidateCount-1 - the latter builds a
			// gene whose own isValid() is false whenever newWinnerIndex is
			// the LAST candidate, which Jenetics' Engine then discards and
			// replaces before scoring, silently undoing this exact mutation.
			Chromosome<IntegerGene> oldChromosome = chromosomes.get(inputGeneCount + i);
			chromosomes.set(inputGeneCount + i,
					oldChromosome.newInstance(ISeq.of(IntegerGene.of(newWinnerIndex, 0, candidateCount))));
			mutations++;
		}

		if (mutations == 0) {
			return new MutatorResult<>(phenotype, 0);
		}
		Phenotype<IntegerGene, TestFitness> mutated = Phenotype.of(Genotype.of(chromosomes), generation);
		return new MutatorResult<>(mutated, mutations);
	}

	private int chooseViaLlmOrFallback(RacePoint racePoint,
			List<CoverageInst.CoverageInstRun.ProcessLaunchSpec> launchSpecs, RandomGenerator random) {
		List<RequiredEdge> uncoveredRelated = racePoint.relatedEdges.stream()
				.filter(edge -> !coveredEdgeKeysSoFar.contains(edge.toString())).toList();
		if (uncoveredRelated.isEmpty()) {
			// The whole search has already covered every edge at this race
			// point (union across every generation so far) - nothing left
			// for the LLM to target, so there's no informative prompt to
			// build; a random pick is exactly as good here (not counted as
			// a failure, just not applicable).
			llmFallbackCount.incrementAndGet();
			return random.nextInt(racePoint.candidateSenderIds.size());
		}

		List<String> labels = RacePromptBuilder.candidateLabels(racePoint);
		String prompt = RacePromptBuilder.build(racePoint, uncoveredRelated, testSetupProcesses, benchmarkSourceDir,
				launchSpecs);
		Optional<Integer> choice = oracle.chooseWinner(prompt, labels);
		if (choice.isPresent()) {
			llmSuccessCount.incrementAndGet();
			return choice.get();
		}
		llmFallbackCount.incrementAndGet();
		return random.nextInt(racePoint.candidateSenderIds.size());
	}
}
