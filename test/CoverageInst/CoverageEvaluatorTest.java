package CoverageInst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

import CoverageInst.CoverageEvaluator.Result;

/**
 * CoverageEvaluator turns real trace-<pid>.log files (written by
 * CoverageTracer at runtime, one "EVENT edgeId correlation timestamp" line
 * per event) into covered/uncovered required edges plus a short anomalies
 * list - synthesizing those trace files directly here is far faster and more
 * precise than only exercising this through a real multi-process run, and
 * covers the BROADCAST/UNKNOWN correlation handling added this session
 * (multicast support, registry-timeout diagnostics) without needing real
 * sockets at all.
 */
class CoverageEvaluatorTest {

	private static File traceDir(String... linesByProcess) throws Exception {
		File dir = Files.createTempDirectory("coverageinst-eval-test").toFile();
		for (int processId = 0; processId < linesByProcess.length; processId++) {
			if (linesByProcess[processId] != null) {
				Files.writeString(new File(dir, "trace-" + processId + ".log").toPath(), linesByProcess[processId]);
			}
		}
		return dir;
	}

	private static RequiredEdge message(int senderPid, String senderEdge, int receiverPid, String receiverEdge) {
		return new RequiredEdge(RequiredEdge.Kind.MESSAGE, senderPid, senderEdge, receiverPid, receiverEdge);
	}

	private static RequiredEdge identity(int pid, String a, String b) {
		return new RequiredEdge(RequiredEdge.Kind.IDENTITY, pid, a, pid, b);
	}

	@Test
	void aMessageEdgeIsCoveredOnlyWhenBothSendAndReceiveAreObserved() throws Exception {
		RequiredEdge edge = message(0, "A#main:0", 1, "B#main:0");
		File dir = traceDir("SEND A#main:0 1\n", "RECEIVE B#main:0 0\n");

		Result result = CoverageEvaluator.evaluate(List.of(edge), dir, List.of(0, 1));

		assertEquals(List.of(edge).toString(), result.covered.toString());
		assertTrue(result.uncovered.isEmpty());
		assertEquals(100.0, result.coveragePercent());
	}

	@Test
	void aMessageEdgeIsUncoveredWhenTheReceiveNeverHappened() throws Exception {
		RequiredEdge edge = message(0, "A#main:0", 1, "B#main:0");
		File dir = traceDir("SEND A#main:0 1\n", "");

		Result result = CoverageEvaluator.evaluate(List.of(edge), dir, List.of(0, 1));

		assertTrue(result.covered.isEmpty());
		assertEquals(List.of(edge).toString(), result.uncovered.toString());
		assertEquals(0.0, result.coveragePercent());
	}

	@Test
	void broadcastCorrelationSatisfiesTheSendSideForEveryDeclaredReceiver() throws Exception {
		RequiredEdge toSlave1 = message(0, "Master#main:0", 1, "Slave#main:0");
		RequiredEdge toSlave2 = message(0, "Master#main:0", 2, "Slave#main:0");
		File dir = traceDir("SEND Master#main:0 BROADCAST\n", "RECEIVE Slave#main:0 0\n", "RECEIVE Slave#main:0 0\n");

		Result result = CoverageEvaluator.evaluate(List.of(toSlave1, toSlave2), dir, List.of(0, 1, 2));

		assertEquals(2, result.covered.size(), "one multicast send must satisfy every declared receiver: "
				+ result.covered + " / uncovered: " + result.uncovered);
	}

	@Test
	void identityEdgesAreCoveredWhenTwoEventsShareTheSameCorrelation() throws Exception {
		RequiredEdge edge = identity(0, "P#run:0", "P#run:1");
		File dir = traceDir("SEM_RELEASE P#run:0 555\nSEM_ACQUIRE P#run:1 555\n");

		Result result = CoverageEvaluator.evaluate(List.of(edge), dir, List.of(0));

		assertEquals(List.of(edge).toString(), result.covered.toString());
	}

	@Test
	void identityEdgesAreNotCoveredWhenCorrelationsDiffer() throws Exception {
		RequiredEdge edge = identity(0, "P#run:0", "P#run:1");
		File dir = traceDir("SEM_RELEASE P#run:0 111\nSEM_ACQUIRE P#run:1 222\n");

		Result result = CoverageEvaluator.evaluate(List.of(edge), dir, List.of(0));

		assertTrue(result.covered.isEmpty());
	}

	@Test
	void anUnresolvedRegistryLookupIsReportedAsATimingAnomalyNotACrash() throws Exception {
		File dir = traceDir("SEND A#main:0 UNKNOWN(127.0.0.1:9999)\n");

		Result result = CoverageEvaluator.evaluate(List.of(), dir, List.of(0));

		assertEquals(1, result.anomalies.size());
		assertTrue(result.anomalies.get(0).contains("registry/timing issue"));
	}

	@Test
	void aSendToAnUndeclaredDestinationIsFlaggedAsUnexpected() throws Exception {
		// No RequiredEdge declares process 0 sending to process 5 at all.
		File dir = traceDir("SEND A#main:0 5\n");

		Result result = CoverageEvaluator.evaluate(List.of(), dir, List.of(0));

		assertEquals(1, result.anomalies.size());
		assertTrue(result.anomalies.get(0).contains("UNEXPECTED"));
		assertTrue(result.anomalies.get(0).contains("p5"));
	}

	@Test
	void broadcastCorrelationIsNeverFlaggedAsAnAnomalyEvenWhenUndeclared() throws Exception {
		// Regression test: BROADCAST must be recognized and skipped BEFORE
		// the Integer.parseInt(send.correlation) call in the anomaly loop -
		// this exact ordering was a real bug found and fixed this session.
		File dir = traceDir("SEND A#main:0 BROADCAST\n");

		Result result = CoverageEvaluator.evaluate(List.of(), dir, List.of(0));

		assertTrue(result.anomalies.isEmpty());
	}

	@Test
	void coveragePercentIsZeroNotNaNWhenNothingIsRequired() throws Exception {
		File dir = traceDir("");

		Result result = CoverageEvaluator.evaluate(List.of(), dir, List.of(0));

		assertEquals(0.0, result.coveragePercent());
	}

	@Test
	void nodeAndBranchEventsAreCapturedForGraphDistance() throws Exception {
		File dir = traceDir("NODE Fixture#main:B0 -\nBRANCH1 Fixture#main:B0 7\nBRANCH2 Fixture#main:B1 3,9\n");

		Result result = CoverageEvaluator.evaluate(List.of(), dir, List.of(0));

		assertTrue(result.observedNodesByProcess.get(0).contains("Fixture#main:B0"));
		assertEquals(7, result.observedOperandsByProcess.get(0).get("Fixture#main:B0")[0]);
		assertEquals(0, result.observedOperandsByProcess.get(0).get("Fixture#main:B0")[1],
				"a single-operand predicate's second operand is always 0");
		assertEquals(3, result.observedOperandsByProcess.get(0).get("Fixture#main:B1")[0]);
		assertEquals(9, result.observedOperandsByProcess.get(0).get("Fixture#main:B1")[1]);
	}
}
