package CoverageInst;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CoverageInst's equivalent of ValiEval: reads the trace logs a run left
 * under coverage.dir and reports which declared required edges were
 * covered - plus, independently, any REAL observed pairing that doesn't
 * match any declared edge at all.
 *
 * That second list is deliberate, not a bug-detection afterthought: ValiPar
 * generates every syntactically-possible send/receive pair specifically
 * "with the objective of revealing faults related to missing
 * communications" (Souza et al. 2008, Section 4) - if a real defect made
 * process P send to the wrong process, the only way a topology-aware tool
 * notices is if it's still watching for pairings it didn't declare
 * "expected". The tracer here logs the real resolved destination/source
 * unconditionally, regardless of what's declared required, so that
 * capability survives - it just moves from "buried among ~90% chronically
 * infeasible required elements" (their own Table V) to a short, separate
 * anomalies list that only ever has entries when something genuinely
 * unexpected happened.
 */
public final class CoverageEvaluator {

	public static final class Fact {
		final int localProcessId;
		final String edgeId;
		final String correlation;

		Fact(int localProcessId, String edgeId, String correlation) {
			this.localProcessId = localProcessId;
			this.edgeId = edgeId;
			this.correlation = correlation;
		}
	}

	public static final class Result {
		public final int totalRequired;
		public final List<RequiredEdge> covered = new ArrayList<>();
		public final List<RequiredEdge> uncovered = new ArrayList<>();
		public final List<String> anomalies = new ArrayList<>();
		/** Basic-block ids actually observed per process (from NODE trace events) - see GraphDistance. */
		public final Map<Integer, Set<String>> observedNodesByProcess = new HashMap<>();
		/**
		 * Last-observed operands of a numeric predicate's branch block, per
		 * process (from BRANCH1/BRANCH2 trace events) - {a, b}, with b=0 for
		 * a single-operand (IFxx-against-zero) predicate. See BranchDistance.
		 */
		public final Map<Integer, Map<String, int[]>> observedOperandsByProcess = new HashMap<>();
		/**
		 * Per receiving process, which processId actually sent the packet
		 * that satisfied a given receive edge in THIS execution - keyed by
		 * receiverProcessId then receiverEdgeId. This is the exact same
		 * correlation already used below to decide MESSAGE-edge coverage
		 * (a receive Fact's own correlation field), just also copied out
		 * here instead of being discarded once matching is done - see
		 * GraphDistance's chainedDistance mechanism, the only consumer.
		 */
		public final Map<Integer, Map<String, Integer>> observedSenderByReceiveEdge = new HashMap<>();

		Result(int totalRequired) {
			this.totalRequired = totalRequired;
		}

		public double coveragePercent() {
			return totalRequired == 0 ? 0.0 : (covered.size() * 100.0) / totalRequired;
		}
	}

	private CoverageEvaluator() {
	}

	public static Result evaluate(List<RequiredEdge> required, File coverageDir, List<Integer> processIds)
			throws IOException {
		List<Fact> sends = new ArrayList<>();
		List<Fact> receives = new ArrayList<>();
		// SEM_RELEASE, SEM_ACQUIRE and BARRIER all funnel into one list:
		// evaluation for RequiredEdge.Kind.IDENTITY is always the same
		// question regardless of which same-process primitive produced the
		// events - "did some event at edge A and some event at edge B ever
		// share a System.identityHashCode correlation" - so one list serves
		// Semaphore/Lock/Condition's directional pairs and CyclicBarrier's
		// symmetric pairs alike, and any future primitive of this shape
		// needs no new list here either.
		List<Fact> identityEvents = new ArrayList<>();
		Result result = new Result(required.size());

		for (int processId : processIds) {
			File log = new File(coverageDir, "trace-" + processId + ".log");
			if (!log.exists()) {
				continue;
			}
			for (String line : Files.readAllLines(log.toPath())) {
				if (line.isBlank()) {
					continue;
				}
				String[] parts = line.split(" ");
				String event = parts[0];
				String edgeId = parts[1];
				String correlation = parts[2];
				Fact fact = new Fact(processId, edgeId, correlation);
				switch (event) {
				case "SEND" -> sends.add(fact);
				case "RECEIVE" -> {
					receives.add(fact);
					try {
						int senderProcessId = Integer.parseInt(correlation);
						result.observedSenderByReceiveEdge.computeIfAbsent(processId, k -> new HashMap<>())
								.put(edgeId, senderProcessId);
					} catch (NumberFormatException notResolved) {
						// "UNKNOWN..." or similar - nothing to record.
					}
				}
				case "SEM_RELEASE", "SEM_ACQUIRE", "BARRIER" -> identityEvents.add(fact);
				case "NODE" -> result.observedNodesByProcess.computeIfAbsent(processId, k -> new HashSet<>())
						.add(edgeId);
				case "BRANCH2" -> {
					String[] ops = correlation.split(",");
					result.observedOperandsByProcess.computeIfAbsent(processId, k -> new HashMap<>()).put(edgeId,
							new int[] { Integer.parseInt(ops[0]), Integer.parseInt(ops[1]) });
				}
				case "BRANCH1" -> result.observedOperandsByProcess.computeIfAbsent(processId, k -> new HashMap<>())
						.put(edgeId, new int[] { Integer.parseInt(correlation), 0 });
				default -> {
				}
				}
			}
		}

		for (RequiredEdge edge : required) {
			boolean covered;
			if (edge.kind == RequiredEdge.Kind.MESSAGE) {
				// "BROADCAST" (a multicast/group send - see CoverageTracer.
				// beforeSend) reaches every group member in one real call,
				// so it satisfies the send side for ANY receiver the
				// declared topology allows for that edge, not just one
				// specific processId the way a unicast send does.
				boolean sendMatches = sends.stream()
						.anyMatch(f -> f.localProcessId == edge.senderProcessId && f.edgeId.equals(edge.senderEdgeId)
								&& (f.correlation.equals(String.valueOf(edge.receiverProcessId))
										|| f.correlation.equals("BROADCAST")));
				boolean receiveMatches = receives.stream().anyMatch(
						f -> f.localProcessId == edge.receiverProcessId && f.edgeId.equals(edge.receiverEdgeId)
								&& f.correlation.equals(String.valueOf(edge.senderProcessId)));
				covered = sendMatches && receiveMatches;
			} else {
				covered = identityEvents.stream()
						.anyMatch(a -> a.edgeId.equals(edge.senderEdgeId) && identityEvents.stream().anyMatch(
								b -> b.edgeId.equals(edge.receiverEdgeId) && b.correlation.equals(a.correlation)));
			}
			(covered ? result.covered : result.uncovered).add(edge);
		}

		for (Fact send : sends) {
			if (send.correlation.startsWith("UNKNOWN")) {
				result.anomalies.add("Could not resolve destination of " + send.edgeId + "@p" + send.localProcessId
						+ " (" + send.correlation + ") - registry/timing issue, not necessarily a program bug");
				continue;
			}
			if (send.correlation.equals("BROADCAST")) {
				// Reaches every group member by design - "which one specific
				// process did this reach" doesn't apply, so there's nothing
				// meaningful to flag as unexpected here.
				continue;
			}
			int dest = Integer.parseInt(send.correlation);
			boolean declared = required.stream()
					.anyMatch(e -> e.senderProcessId == send.localProcessId && e.senderEdgeId.equals(send.edgeId)
							&& e.receiverProcessId == dest);
			if (!declared) {
				result.anomalies.add("UNEXPECTED: " + send.edgeId + "@p" + send.localProcessId
						+ " sent to p" + dest + ", which no declared topology link allows");
			}
		}

		return result;
	}
}
