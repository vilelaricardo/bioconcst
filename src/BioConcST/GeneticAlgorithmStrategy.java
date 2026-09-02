package BioConcST;

import java.io.File;

import io.jenetics.IntegerGene;
import io.jenetics.Selector;

/** The current approach: BioConcST's genetic algorithm with FuzzyST selection. */
public class GeneticAlgorithmStrategy implements SearchStrategy {

	@Override
	public SolutionResult run(ExperimentConfig config, BenchmarkConfig benchmark, File filesPath,
			ProcessBuilder instrumentation, String[] testSetup) {
		GAConfig ga = config.ga;

		Selector<IntegerGene, TestFitness> survivorsSelector = new FuzzySelector<>();
		Selector<IntegerGene, TestFitness> offspringSelector = new FuzzySelector<>();

		int execTimeLimitMs = benchmark.execTimeLimitMs != null ? benchmark.execTimeLimitMs
				: BenchmarkConfig.DEFAULT_EXEC_TIME_LIMIT_MS;

		BioConcSTCore core = new BioConcSTCore(ga.populationSize, ga.generations, ga.mutationRate, ga.crossoverRate,
				ga.min, ga.max, ga.survivorsFraction, ga.offspringFraction, benchmark.argumentsLength,
				benchmark.argumentRanges, ga.threadExecutors, execTimeLimitMs, survivorsSelector, offspringSelector);

		return core.generatorEvolution(filesPath, instrumentation, testSetup);
	}
}
