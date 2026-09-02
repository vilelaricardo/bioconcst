package BioConcST;

/** Genetic Algorithm hyperparameters for one experiment run. */
public class GAConfig {
	public int populationSize;
	public int generations;
	public double mutationRate;
	public double crossoverRate;
	public double survivorsFraction;
	public double offspringFraction;
	public int min;
	public int max;
	public int threadExecutors;
	public int executions;
}
