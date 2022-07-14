package ConcurrentTesting;

public class Node {

	private String node;

	private boolean visited;
	private int id;

	public Node(String node) {
		super();
		this.node = node;
	}

	public boolean isVisited() {
		return visited;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public void setVisited(boolean visited) {
		this.visited = visited;
	}

	public String getNode() {
		return this.node;
	}

	public void setNode(String node) {
		this.node = node;
	}

	public String toString() {

		return "{\"Node\": " + getNode() + "\", \"isVisited\": " + isVisited() + "}";

	}

}
