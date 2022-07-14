package ConcurrentTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class BreadthFirstSearch {

	private Queue<Node> queue;
	private Node node;
	private boolean flag;
	private Graph gfc;
	private List<String> bfsList;
	private List<String> predList;

	public BreadthFirstSearch(Graph gfc, Node node) {
		queue = new LinkedList<Node>();
		this.node = node;
		this.gfc = gfc;
		bfsList = new ArrayList<String>();
		predList = new ArrayList<String>();
	}

	public List<String> bfsSearch() {

		int nodeId = -1;
		for (int i = 0; i < gfc.adjacent.size(); i++) {
			if (gfc.adjacent.get(i).get(0).getNode().equals(new Node("1").getNode())) {
				nodeId = i;
			}
		}

		bfsList.add(new Node("1").getNode());
		predList.add("root");
		queue.add(gfc.adjacent.get(nodeId).get(0));
		gfc.adjacent.get(nodeId).get(0).setVisited(true);

		while (!queue.isEmpty() && !flag) {
			String pred;
			Node vertice = queue.poll();
			pred = vertice.getNode();
			for (int i = 0; i < gfc.adjacent.size(); i++) {
				if (gfc.adjacent.get(i).get(0).getNode().equals(vertice.getNode())) {
					nodeId = i;
					break;
				}
			}

			for (int i = 0; i < gfc.adjacent.get(nodeId).size(); i++) {
				Node key = gfc.adjacent.get(nodeId).get(i);
				if (!key.isVisited()) {
					key.setVisited(true);
					queue.add(key);
					if (!bfsList.contains(key.getNode()) && !flag) {
						bfsList.add(key.getNode());
						predList.add(pred);
						if (key.getNode().equals(node.getNode().replace("\"", "")))
							flag = true;

					}
				}

			}

		}
		return shortestPath(bfsList, predList);
	}

	private List<String> shortestPath(List<String> bfs, List<String> pred) {

		List<String> shortest = new ArrayList<String>();
		String predc = pred.get(pred.size() - 1);
		shortest.add(bfs.get(bfs.size() - 1));
		for (int i = bfs.size() - 1; i >= 0; i--) {
			if (bfs.get(i).equals(predc)) {
				shortest.add(bfs.get(i));
				predc = pred.get(i);
			}
		}
		Collections.reverse(shortest);

		return shortest;
	}

}
