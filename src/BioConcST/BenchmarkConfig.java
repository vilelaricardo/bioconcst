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
}
