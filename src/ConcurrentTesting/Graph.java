package ConcurrentTesting;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import Utilities.BioConcST_FileReader;

public class Graph {
	protected ArrayList<ArrayList<Node>> adjacent = new ArrayList<ArrayList<Node>>();
	private int iterator;

	void addEdge(int index, Node node) {

		adjacent.get(index).add(node);
	}

	public Graph(int process, int thread, int testID) {

		iterator = 0;

		JSONObject controlflowgraph = new JSONArray("[" +
				new BioConcST_FileReader()
						.fileReader("./experiment/test" + testID + "/valipar/pcfgs/p" + process + "t" + thread
								+ ".json")
				+ "]").getJSONObject(0).getJSONObject("control-flow");

		for (int i = 0; i < controlflowgraph.length(); i++)
			adjacent.add(new ArrayList<Node>());
		controlflowgraph.keys().forEachRemaining(node -> {
			addEdge(iterator, new Node(node));

			controlflowgraph.getJSONArray(node).forEach(neighbor -> addEdge(iterator, new Node(neighbor.toString())));

			iterator++;
		});

	}

	void printGraph(ArrayList<ArrayList<Node>> adj) {
		for (int i = 0; i < adj.size(); i++) {
			System.out.println("\nAdjacency list of vertex: " + i);
			for (int j = 0; j < adj.get(i).size(); j++) {
				System.out.print(" -> " + adj.get(i).get(j));
			}
		}
	}
}
