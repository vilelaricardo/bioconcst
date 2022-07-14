package BioConcST;

import java.io.File;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ConcurrentTesting.DistanceElem;
import ConcurrentTesting.ExecutionTrace;
import ConcurrentTesting.Node;
import ConcurrentTesting.TargetElement;
import ValiPar.RequiredElem;
import ValiPar.Required_elements;
import ValiPar.ValiParRun;
import io.jenetics.Genotype;
import io.jenetics.IntegerGene;

public final class FitnessFunction {

	private double fitness;
	private RequiredElem required_elements;
	private int uncoveredElems;
	private Genotype<IntegerGene> testdata;
	private int testID;
	private File filesPath;
	private ProcessBuilder instrumentation;
	private String[] testSetup;
	private Double coverage;

	public FitnessFunction(Genotype<IntegerGene> testdata, int testID, File filesPath, ProcessBuilder instrumentation,
			String[] testSetup) {

		this.testdata = testdata;
		this.testID = testID;
		this.filesPath = filesPath;
		this.instrumentation = instrumentation;
		this.testSetup = testSetup;
		this.coverage = 0.0;
		newValiParInstance();

		new Required_elements(testID);
		required_elements = new RequiredElem("sync_edge_requirements", testID);

		required_elements.getRequired_elements().forEach(elem -> {
			if (elem.get("state").toString().equals("\"UNCOVERED\"")) {

				JsonNode processId = elem.get("send").get("processId");
				JsonNode threadId = elem.get("send").get("threadId");
				JsonNode nodeId = elem.get("send").get("nodeId");
				TargetElement targetSend = new TargetElement(Integer.parseInt(processId.toString()),
						Integer.parseInt(threadId.toString()), new Node(nodeId.toString()));
				ExecutionTrace traceSend = new ExecutionTrace(0, 0, Integer.parseInt(processId.toString()),
						Integer.parseInt(threadId.toString()));
				processId = elem.get("receive").get("processId");
				threadId = elem.get("receive").get("threadId");
				nodeId = elem.get("receive").get("nodeId");
				TargetElement targetReceive = new TargetElement(Integer.parseInt(processId.toString()),
						Integer.parseInt(threadId.toString()), new Node(nodeId.toString()));
				ExecutionTrace traceReceive = new ExecutionTrace(0, 0, Integer.parseInt(processId.toString()),
						Integer.parseInt(threadId.toString()));
				DistanceElem distance = new DistanceElem(targetSend, traceSend, targetReceive, traceReceive, testID);

				distance.distanceCalculator();

			} else {
				coverage++;
			}
			;
		});

		testdata.setCoverage((coverage / required_elements.getRequired_elements().size()) * 100);
		testdata.setSync_edge_requirements(required_elements.getRequired_elements().toString());

		if (uncoveredElems != 0)
			fitness /= uncoveredElems;

	}

	private void newValiParInstance() {
		ValiParRun valipar = new ValiParRun();
		valipar.createProject(testID);
		valipar.instrumentation(testID, instrumentation, filesPath);
		valipar.elementsGenerator(testID);
		valipar.newTestCase(testdata, testID, testSetup);
		ValiParRun.execution(testID);
		valipar.evaluation(testID);
	}

	public Double getFitness() {
		System.out.println("Test data:" + testdata + "[Fitness: " + fitness + "]");

		// SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss
		// z");
		// Date date = new Date(System.currentTimeMillis());
		// System.out.println(formatter.format(date));

		return fitness;

	}

	public void setFitness(Double fitness) {
		this.fitness = fitness;
	}

	public Genotype<IntegerGene> getTestdata() {
		return testdata;
	}

	public void setTestdata(Genotype<IntegerGene> testdata) {
		this.testdata = testdata;
	}

}
