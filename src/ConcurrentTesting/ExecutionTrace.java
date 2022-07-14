package ConcurrentTesting;

public class ExecutionTrace {
	
	private int testID;
	private int executionID;
	private int process;
	private int thread;
	public ExecutionTrace(int testID, int executionID, int process, int thread) {
		super();
		this.testID = testID;
		this.executionID = executionID;
		this.process = process;
		this.thread = thread;
	}
	public int getTestID() {
		return testID;
	}
	public void setTestID(int testID) {
		this.testID = testID;
	}
	public int getExecutionID() {
		return executionID;
	}
	public void setExecutionID(int executionID) {
		this.executionID = executionID;
	}
	public int getProcess() {
		return process;
	}
	public void setProcess(int process) {
		this.process = process;
	}
	public int getThread() {
		return thread;
	}
	public void setThread(int thread) {
		this.thread = thread;
	}
	
}
