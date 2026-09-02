package BioConcST;

import java.io.File;

/**
 * A way of generating concurrent test data: the genetic algorithm (current),
 * or an LLM-guided variant (planned) that shares this same seam so it can be
 * compared against the GA using identical benchmark/ValiPar plumbing.
 */
public interface SearchStrategy {

	SolutionResult run(ExperimentConfig config, File filesPath, ProcessBuilder instrumentation, String[] testSetup);

	static SearchStrategy resolve(String name) {
		if (name == null || name.equalsIgnoreCase("GA")) {
			return new GeneticAlgorithmStrategy();
		}
		throw new IllegalArgumentException("Unknown strategy: " + name);
	}
}
