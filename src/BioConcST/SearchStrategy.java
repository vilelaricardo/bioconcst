package BioConcST;

import java.io.File;

/**
 * A way of generating concurrent test data: the genetic algorithm (current),
 * or an LLM-guided variant (planned) that shares this same seam so it can be
 * compared against the GA using identical benchmark/ValiPar plumbing.
 */
public interface SearchStrategy {

	// benchmark is passed explicitly (rather than read from config.benchmark)
	// because a suite run iterates config.benchmarks - config.benchmark is
	// null in that case.
	SolutionResult run(ExperimentConfig config, BenchmarkConfig benchmark, File filesPath,
			ProcessBuilder instrumentation, String[] testSetup);

	static SearchStrategy resolve(String name) {
		if (name == null || name.equalsIgnoreCase("GA")) {
			return new GeneticAlgorithmStrategy();
		}
		throw new IllegalArgumentException("Unknown strategy: " + name);
	}
}
