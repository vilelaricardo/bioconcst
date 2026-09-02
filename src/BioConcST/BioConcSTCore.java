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
	private int threadExecutors;
	private static int iterator = 0;
	private Selector<IntegerGene, TestFitness> survivorsSelector;
	private Selector<IntegerGene, TestFitness> offspringSelector;
	private Genotype<IntegerGene> GENOTYPE;
	private Problem<Genotype<IntegerGene>, IntegerGene, TestFitness> PROBLEM;
	private SolutionResult solutionResults;

	private static String[] testSetup;

	public BioConcSTCore(int populationSize, int generations, double mutationRate, double crossoverRate, int min,
			int max, double suvivorsFraction, double offspringFraction, int argumentsLenght, int threadExecutors,
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
	}

	public SolutionResult generatorEvolution(File filesPath, ProcessBuilder instrumentation, String[] testSetup) {

		iterator = 0;
		this.testSetup = testSetup;
		GENOTYPE = Genotype.of(IntegerChromosome.of(min, max, argumentsLenght));
		PROBLEM = Problem.of(BioConcSTCore::fitness, Codec.of(GENOTYPE, gt -> gt));

		ValiParRun valipar = new ValiParRun();
		valipar.newExperiment();
		valipar.createBaseline(instrumentation, filesPath);

		System.out.println("Starting evolution...");

		final ExecutorService executor = Executors.newFixedThreadPool(threadExecutors);

		final Engine<IntegerGene, TestFitness> engine = Engine.builder(PROBLEM).minimizing()
				.survivorsFraction(suvivorsFraction).offspringFraction(offspringFraction)
				.survivorsSelector(survivorsSelector).offspringSelector(offspringSelector)
				.populationSize(populationSize)
				.alterers(new SwapMutator<>(mutationRate), new SinglePointCrossover<>(crossoverRate)).executor(executor)
				// .executor((Executor) Runnable::run) //Sequential executor
				.interceptor(EvolutionResult.toUniquePopulation()).build();

		final EvolutionStatistics<TestFitness, ?> statistics = EvolutionStatistics.ofNumber();

		// Tracks, generation by generation, the coverage of the best individual and
		// the population from whichever generation achieved the best coverage so far
		// - this replaces the "sync_coverage"/"bestpop" fields that used to live on a
		// patched Engine (see FuzzySelector.java / SolutionResult.java for the rest).
		final double[] bestCoverageSoFar = { -1.0 };
		final List<Double> syncCoverageHistory = new ArrayList<>();
		final AtomicReference<ISeq<Phenotype<IntegerGene, TestFitness>>> bestPopulation = new AtomicReference<>(
				ISeq.empty());

		final ISeq<Phenotype<IntegerGene, TestFitness>> results = engine.stream()
				.limit(Limits.byFixedGeneration(generations)).peek(statistics).peek(result -> {
					double genCoverage = result.bestPhenotype().fitness().getCoverage();
					syncCoverageHistory.add(genCoverage);
					if (genCoverage > bestCoverageSoFar[0]) {
						bestCoverageSoFar[0] = genCoverage;
						bestPopulation.set(result.population());
					}
				}).map(EvolutionResult::bestPhenotype).collect(ISeq.toISeq());

		executor.shutdown();

		setSolutionResults(new SolutionResult(syncCoverageHistory, statistics, results, bestPopulation.get()));

		return getSolutionResults();

	}

	public synchronized static int increment() {

		return iterator++;
	}

	public static TestFitness fitness(Genotype<IntegerGene> x) {

		return new FitnessFunction(x, increment(), testSetup).getFitness();

	}

	public SolutionResult getSolutionResults() {
		return solutionResults;
	}

	public void setSolutionResults(SolutionResult results) {
		this.solutionResults = results;
	}

}
