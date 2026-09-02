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

	// Optional override for how long (ms) a single "valipar exec" is allowed
	// to run before being killed. Most benchmarks finish in well under the
	// default; a few with many tightly-coordinated threads inside one process
	// (e.g. jacobi) need more headroom when several executions run in
	// parallel worker containers and compete for CPU. Null means use the
	// default.
	public Integer execTimeLimitMs;
	public static final int DEFAULT_EXEC_TIME_LIMIT_MS = 10000;
}
