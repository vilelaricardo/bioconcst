package BioConcST;

import java.util.List;

/**
 * Describes the concurrent program under test: where its compiled classes
 * live, which classes are ValiPar "processes", and how a test case is
 * assembled for it. Everything ValiParRun.createBaseline()/newTestCase()
 * need is derived from this instead of being hardcoded per experiment.
 */
public class BenchmarkConfig {
	public String name;
	public String path;
	public List<String> processes;
	public List<String> parseFiles;
	public List<String> ignoreFiles;
	public List<ProcessSpec> testSetupProcesses;
	public int argumentsLength;

	// Optional: per-position [min,max] domain for the test case's argument
	// vector, for benchmarks whose arguments aren't all drawn from the same
	// range (e.g. a fixed thread count plus a 0/1 flag plus an iteration
	// count). When absent, GAConfig.min/max apply uniformly to every position.
	public List<ArgumentRange> argumentRanges;
}
