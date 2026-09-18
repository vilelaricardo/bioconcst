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

	// Race-gene hyperparameters (only read when some benchmark's
	// coverageInst.raceGene is true - see CoverageInstConfig). Absent in
	// JSON deserializes to 0.0/0/null, which CoverageInstStrategy treats as
	// "no LLM oracle configured" and falls back to uniform-random choice.
	public double raceMutationRate;
	public double raceLlmProbability;
	public String ollamaEndpoint;
	public String ollamaModel;
	public int ollamaTimeoutMs;
}
