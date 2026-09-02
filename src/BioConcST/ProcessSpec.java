package BioConcST;

/**
 * One process entry in a benchmark's test case setup, e.g. {id=0,
 * className=GcdMaster, args=TESTDATA} - "TESTDATA" is replaced with the
 * evolving individual's gene values at test-case-creation time.
 */
public class ProcessSpec {
	public int id;
	public String className;
	public String args;
}
