package ConcurrentTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import Utilities.BioConcST_FileReader;

public class DistanceElem {

	protected Double distance;
	protected TargetElement targetSend;
	protected ExecutionTrace traceSend;
	protected TargetElement targetReceive;
	protected ExecutionTrace traceReceive;
	protected int testID;

	public DistanceElem(TargetElement targetSend, ExecutionTrace traceSend, TargetElement targetReceive,
			ExecutionTrace traceReceive, int testID) {
		super();
		this.targetSend = targetSend;
		this.traceSend = traceSend;
		this.targetReceive = targetReceive;
		this.traceReceive = traceReceive;
		this.testID = testID;
	}

	public Double getDistance() {
		return distance;
	}

	public void setDistance(Double distance) {
		this.distance = distance;
	}

	private ArrayList<String> readTrace(int test, int execution, int process, int thread,
			int testID) {
		String trace = new BioConcST_FileReader().fileReader("./experiment/test" + testID + "/valipar/tests/test" + test
				+ "/execution" + execution + "/traces/p" + process + "t" + thread + ".log");

		ArrayList<String> traceList = new ArrayList<String>();
		Matcher matcher = Pattern.compile("NodeEvent,<([\\S\\s]*?)\\^\\{").matcher(trace);
		while (matcher.find()) {
			traceList.add(matcher.group().replace("^{", "").replace("NodeEvent,<", ""));
		}
		return traceList;
	}

	public Double distanceCalculator() {

		this.setDistance((distance(targetSend, traceSend) + distance(targetReceive, traceReceive)) / 2);
		return this.getDistance();

	}

	protected Double distance(TargetElement target, ExecutionTrace trace) {
		Graph controlflowgraph = new Graph(target.getProcess(), target.getThread(), this.testID);
		BreadthFirstSearch bfs = new BreadthFirstSearch(controlflowgraph, target.getNode());
		List<String> bfsList = bfs.bfsSearch();
		List<String> traceProgram = readTrace(trace.getTestID(), trace.getExecutionID(), trace.getProcess(),
				trace.getThread(), testID);
		Double distance = 0.0;
		for (int i = 0; i < bfsList.size(); i++) {
			if (traceProgram.contains(bfsList.get(i))) {
				distance = 0.0;

			} else {

				distance++;
			}

		}

		distance = (distance * 1) / bfsList.size();

		return distance;
	}

}
