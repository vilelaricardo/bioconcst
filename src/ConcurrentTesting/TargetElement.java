package ConcurrentTesting;

public class TargetElement {

	private int process;
	private int thread;
	private Node node;

	public TargetElement(int process, int thread, Node node) {
		super();
		this.process = process;
		this.thread = thread;
		this.node = node;
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

	public Node getNode() {
		return node;
	}

	public void setNode(Node node) {
		this.node = node;
	}

}