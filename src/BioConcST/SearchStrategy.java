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
		// Same Engine/FuzzySelector/HallOfFame wiring, but against an in-memory
		// stub instead of the real (expensive) ValiPar-backed evaluation - for
		// cheaply tuning GA hyperparameters/fuzzy calibration before spending
		// real evaluations confirming it transfers. See StubFitnessFunction.
		if (name.equalsIgnoreCase("GA_STUB")) {
			return new StubGeneticAlgorithmStrategy();
		}
		// Same wiring again, but against a real native execution instrumented
		// by CoverageInst instead of ValiPar - see CoverageInstStrategy's own
		// javadoc for why (ValiPar's static analysis structurally
		// over-generates required sync edges for some of our synthetic
		// benchmarks). Any benchmark can use this as long as its config
		// declares a "coverageInst" block - see CoverageInstConfig.
		if (name.equalsIgnoreCase("GA_COVINST")) {
			return new CoverageInstStrategy();
		}
		// A genuine, uncorrelated random-sampling baseline for CoverageInst
		// benchmarks - see RandomCoverageInstStrategy's own javadoc for why
		// GA_COVINST with populationSize=N/generations=1 is NOT this (the
		// Jenetics Engine performs a second, hidden evaluation round with
		// real selection/alteration on every single call, generation 1
		// included).
		if (name.equalsIgnoreCase("RANDOM_COVINST")) {
			return new RandomCoverageInstStrategy();
		}
		throw new IllegalArgumentException("Unknown strategy: " + name);
	}
}
