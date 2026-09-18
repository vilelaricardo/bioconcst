package BioConcST;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import ValiPar.ValiParRun;
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
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.engine.Limits;
import io.jenetics.engine.Problem;
import io.jenetics.util.ISeq;

public class BioConcSTCore {

	private int populationSize;
	private int generations;
	private double mutationRate;
	private double crossoverRate;
	private int min;
	private int max;
	private double suvivorsFraction;
	private double offspringFraction;
	private int argumentsLenght;
	private List<ArgumentRange> argumentRanges;
	private int threadExecutors;
	private static int iterator = 0;
	private Selector<IntegerGene, TestFitness> survivorsSelector;
	private Selector<IntegerGene, TestFitness> offspringSelector;
	private Genotype<IntegerGene> GENOTYPE;
	private Problem<Genotype<IntegerGene>, IntegerGene, TestFitness> PROBLEM;
	private SolutionResult solutionResults;

	private static String[] testSetup;
	private int execTimeLimitMs;
	private static int currentExecTimeLimitMs;

	public BioConcSTCore(int populationSize, int generations, double mutationRate, double crossoverRate, int min,
			int max, double suvivorsFraction, double offspringFraction, int argumentsLenght,
			List<ArgumentRange> argumentRanges, int threadExecutors, int execTimeLimitMs,
			Selector<IntegerGene, TestFitness> survivorsSelector, Selector<IntegerGene, TestFitness> offspringSelector) {
		super();
		this.populationSize = populationSize;
		this.generations = generations;
		this.mutationRate = mutationRate;
		this.crossoverRate = crossoverRate;
		this.min = min;
		this.max = max;
		this.suvivorsFraction = suvivorsFraction;
		this.offspringFraction = offspringFraction;
		this.threadExecutors = threadExecutors;
		this.survivorsSelector = survivorsSelector;
		this.offspringSelector = offspringSelector;
		this.argumentsLenght = argumentsLenght;
		this.argumentRanges = argumentRanges;
		this.execTimeLimitMs = execTimeLimitMs;
	}

	public SolutionResult generatorEvolution(File filesPath, ProcessBuilder instrumentation, String[] testSetup) {

		iterator = 0;
		this.testSetup = testSetup;
		currentExecTimeLimitMs = this.execTimeLimitMs;
		if (argumentRanges != null && !argumentRanges.isEmpty()) {
			// Jenetics requires every gene within one IntegerChromosome to share
			// the same [min,max) range, so a heterogeneous argument vector is
			// modeled as one length-1 chromosome per position instead of a
			// single length-N chromosome.
			IntegerChromosome[] chromosomes = new IntegerChromosome[argumentRanges.size()];
			for (int i = 0; i < argumentRanges.size(); i++) {
				chromosomes[i] = IntegerChromosome.of(argumentRanges.get(i).min, argumentRanges.get(i).max, 1);
			}
			GENOTYPE = Genotype.of(chromosomes[0], java.util.Arrays.copyOfRange(chromosomes, 1, chromosomes.length));
		} else {
			GENOTYPE = Genotype.of(IntegerChromosome.of(min, max, argumentsLenght));
		}
		PROBLEM = Problem.of(BioConcSTCore::fitness, Codec.of(GENOTYPE, gt -> gt));

		ValiParRun valipar = new ValiParRun();
		valipar.newExperiment();
		valipar.createBaseline(instrumentation, filesPath);

		System.out.println("Starting evolution...");

		final ExecutorService executor = Executors.newFixedThreadPool(threadExecutors);

		// Hall-of-fame runs first, dedup second - NOT the other way around.
		// toUniquePopulation() replaces duplicate genotypes with fresh, still
		// UNEVALUATED ones and marks the result "dirty" for Jenetics' own engine
		// to evaluate as a separate internal step after the interceptor chain
		// returns; calling .fitness() on those (as hall-of-fame does) before that
		// happens throws "Phenotype has no assigned fitness value" (hit this in
		// practice). Running hall-of-fame first means it only ever sees an
		// already-fully-evaluated population. This ordering doesn't let dedup
		// undo the injection either: the champion is only ever injected into a
		// slot where its genotype doesn't already occur, so dedup never sees it
		// as a duplicate.
		final io.jenetics.engine.EvolutionInterceptor<IntegerGene, TestFitness> uniqueInterceptor = EvolutionResult
				.toUniquePopulation();
		final io.jenetics.engine.EvolutionInterceptor<IntegerGene, TestFitness> hallOfFameInterceptor = HallOfFame
				.interceptor();
		final io.jenetics.engine.EvolutionInterceptor<IntegerGene, TestFitness> combinedInterceptor = io.jenetics.engine.EvolutionInterceptor
				.ofAfter(result -> uniqueInterceptor.after(hallOfFameInterceptor.after(result)));

		final Engine<IntegerGene, TestFitness> engine = Engine.builder(PROBLEM).minimizing()
				.survivorsFraction(suvivorsFraction).offspringFraction(offspringFraction)
				.survivorsSelector(survivorsSelector).offspringSelector(offspringSelector)
				.populationSize(populationSize)
				// SwapMutator alone is a documented no-op on any length-1
				// chromosome (Jenetics' own source: SwapMutator.mutate()
				// short-circuits to "0 mutations" whenever
				// chromosome.length() <= 1) - and argumentRanges (used
				// whenever arguments need per-argument bounds) builds ONE
				// single-gene chromosome PER ARGUMENT. Without Mutator here,
				// the population's achievable numeric values were
				// permanently fixed at whatever random values existed in
				// the initial population - crossover could only recombine
				// them, never introduce a new one. Found and fixed in
				// CoverageInstStrategy/StubGeneticAlgorithmStrategy first
				// this same investigation; ported here on the user's
				// explicit request since it's a real, pre-existing gap in
				// the published pipeline's own search capability, not
				// something introduced by that work.
				.alterers(new SwapMutator<>(mutationRate), new Mutator<>(mutationRate),
						new SinglePointCrossover<>(crossoverRate))
				.executor(executor)
				// .executor((Executor) Runnable::run) //Sequential executor
				.interceptor(combinedInterceptor).build();

		final EvolutionStatistics<TestFitness, ?> statistics = EvolutionStatistics.ofNumber();

		// Tracks, generation by generation, the coverage of the population as a
		// whole (the union of what every living individual covers - what the tool
		// actually delivers as a test suite, not any single individual's own
		// coverage) and the population from whichever generation achieved the best
		// coverage so far - this replaces the "sync_coverage"/"bestpop" fields that
		// used to live on a patched Engine (see FuzzySelector.java / SolutionResult.java
		// for the rest).
		final double[] bestCoverageSoFar = { -1.0 };
		final List<Double> syncCoverageHistory = new ArrayList<>();
		final AtomicReference<ISeq<Phenotype<IntegerGene, TestFitness>>> bestPopulation = new AtomicReference<>(
				ISeq.empty());

		final ISeq<Phenotype<IntegerGene, TestFitness>> results;
		try {
			results = engine.stream().limit(Limits.byFixedGeneration(generations)).peek(statistics).peek(result -> {
				double genCoverage = SuiteCoverage.unionCoveragePercent(result.population());
				syncCoverageHistory.add(genCoverage);
				if (genCoverage > bestCoverageSoFar[0]) {
					bestCoverageSoFar[0] = genCoverage;
					bestPopulation.set(result.population());
				}
			}).map(EvolutionResult::bestPhenotype).collect(ISeq.toISeq());
		} finally {
			// Otherwise a stream failure (as happened in practice - see
			// HallOfFame/toUniquePopulation ordering) leaves this thread pool's
			// non-daemon threads running forever, since shutdown() would never be
			// reached.
			executor.shutdown();
		}

		setSolutionResults(new SolutionResult(syncCoverageHistory, statistics, results, bestPopulation.get()));

		return getSolutionResults();

	}

	public synchronized static int increment() {

		return iterator++;
	}

	public static TestFitness fitness(Genotype<IntegerGene> x) {

		return new FitnessFunction(x, increment(), testSetup, currentExecTimeLimitMs).getFitness();

	}

	public SolutionResult getSolutionResults() {
		return solutionResults;
	}

	public void setSolutionResults(SolutionResult results) {
		this.solutionResults = results;
	}

}
