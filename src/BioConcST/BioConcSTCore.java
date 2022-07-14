package BioConcST;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ValiPar.ValiParRun;
import io.jenetics.Genotype;
import io.jenetics.IntegerGene;
import io.jenetics.Mutator;
import io.jenetics.Phenotype;
import io.jenetics.Selector;
import io.jenetics.SinglePointCrossover;
import io.jenetics.TestDataChromosome;
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
	private List<IntegerGene> testdataGenes;
	private int threadExecutors;
	private static int iterator = 0;
	private Selector<IntegerGene, Double> survivorsSelector;
	private Selector<IntegerGene, Double> offspringSelector;
	private Genotype<IntegerGene> GENOTYPE;
	private Problem<Genotype<IntegerGene>, IntegerGene, Double> PROBLEM;
	private SolutionResult solutionResults;

	private static File filesPath;
	private static ProcessBuilder instrumentation;
	private static String[] testSetup;

	public BioConcSTCore(int populationSize, int generations, double mutationRate, double crossoverRate, int min,
			int max, double suvivorsFraction, double offspringFraction, int argumentsLenght, int threadExecutors,
			Selector<IntegerGene, Double> survivorsSelector, Selector<IntegerGene, Double> offspringSelector) {
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
		this.testdataGenes = new ArrayList<IntegerGene>();

		// Encode Chromosome
		for (int i = 0; i < argumentsLenght; i++) {
			testdataGenes.add(IntegerGene.of(this.min, this.max));
		}
	}

	public SolutionResult generatorEvolution(File filesPath, ProcessBuilder instrumentation, String[] testSetup) {

		iterator = 0;
		this.filesPath = filesPath;
		this.instrumentation = instrumentation;
		this.testSetup = testSetup;
		GENOTYPE = Genotype.of(TestDataChromosome.of(testdataGenes));
		PROBLEM = Problem.of(BioConcSTCore::fitness, Codec.of(GENOTYPE, gt -> gt));

		ValiParRun valipar = new ValiParRun();
		valipar.newExperiment();

		System.out.println("Starting evolution...");

		final ExecutorService executor = Executors.newFixedThreadPool(threadExecutors);

		final Engine<IntegerGene, Double> engine = Engine.builder(PROBLEM).minimizing()
				.survivorsFraction(suvivorsFraction).offspringFraction(offspringFraction)
				.survivorsSelector(survivorsSelector).offspringSelector(offspringSelector)
				.populationSize(populationSize)
				.alterers(new Mutator<>(mutationRate), new SinglePointCrossover<>(crossoverRate)).executor(executor)
				// .executor((Executor) Runnable::run) //Sequential executor
				.interceptor(EvolutionResult.toUniquePopulation()).build();

		final EvolutionStatistics<Double, ?> statistics = EvolutionStatistics.ofNumber();
		final ISeq<Phenotype<IntegerGene, Double>> results = engine.stream()
				.limit(Limits.byFixedGeneration(generations)).peek(statistics).map(EvolutionResult::bestPhenotype)
				.collect(ISeq.toISeq());

		setSolutionResults(new SolutionResult(engine, statistics, results, engine.getBestpop()));

		return getSolutionResults();

	}

	public synchronized static int increment() {

		return iterator++;
	}

	public static Double fitness(Genotype<IntegerGene> x) {

		return new FitnessFunction(x, increment(), filesPath, instrumentation, testSetup).getFitness();

	}

	public SolutionResult getSolutionResults() {
		return solutionResults;
	}

	public void setSolutionResults(SolutionResult results) {
		this.solutionResults = results;
	}

}