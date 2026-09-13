package CoverageInst;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

/**
 * GraphDistance combines approach level (ControlFlowGraph.shortestPathTo)
 * with branch distance (BranchDistance, at the exact point a real trace
 * diverged from the wanted path) into the actual fitness gradient
 * CoverageInstFitnessFunction searches against - the real target of the
 * BranchDistance.compute() bug fixed earlier, one level up. Untested until
 * now despite being squarely in scope (src/CoverageInst/, not the GA search
 * layer itself) - these pin down the formula directly against hand-built
 * graphs/observation maps, the same style as ControlFlowGraphTest, so a
 * wrong divergence-point calculation or a wrong fail-soft value would show
 * up here in milliseconds instead of only as unexplained search stagnation.
 */
class GraphDistanceTest {

	private static final Map<Integer, Map<String, int[]>> NO_OPERANDS = Map.of();
	private static final Map<String, Integer> NO_PREDICATES = Map.of();

	private static RequiredEdge messageEdge(String senderEdgeId, String receiverEdgeId) {
		return new RequiredEdge(RequiredEdge.Kind.MESSAGE, 0, senderEdgeId, 0, receiverEdgeId);
	}

	@Test
	void distanceIsZeroWhenEveryNodeOnThePathWasObserved() {
		ControlFlowGraph graph = new ControlFlowGraph("P#main:B0",
				Map.of("P#main:B0", java.util.List.of("P#main:B1"), "P#main:B1", java.util.List.of()));
		Map<String, ControlFlowGraph> graphs = Map.of("P#main", graph);
		Map<String, String> syncEdgeBlocks = Map.of("P#main:0", "P#main:B1", "P#main:1", "P#main:B1");
		RequiredEdge edge = messageEdge("P#main:0", "P#main:1");
		Map<Integer, Set<String>> observed = Map.of(0, Set.of("P#main:B0", "P#main:B1"));

		double distance = GraphDistance.compute(edge, graphs, syncEdgeBlocks, NO_PREDICATES, observed, NO_OPERANDS);

		assertEquals(0.0, distance);
	}

	@Test
	void distanceIsMaximalWhenNothingOnThePathWasObserved() {
		ControlFlowGraph graph = new ControlFlowGraph("P#main:B0",
				Map.of("P#main:B0", java.util.List.of("P#main:B1"), "P#main:B1", java.util.List.of()));
		Map<String, ControlFlowGraph> graphs = Map.of("P#main", graph);
		Map<String, String> syncEdgeBlocks = Map.of("P#main:0", "P#main:B1", "P#main:1", "P#main:B1");
		RequiredEdge edge = messageEdge("P#main:0", "P#main:1");

		// Neither process observed anything at all - not even the entry block.
		double distance = GraphDistance.compute(edge, graphs, syncEdgeBlocks, NO_PREDICATES, Map.of(), NO_OPERANDS);

		assertEquals(1.0, distance);
	}

	@Test
	void divergingAtARecognizedNumericPredicateUsesBranchDistanceForThatStep() {
		// B0 branches: taken -> B1 (the send-side target), fallthrough -> B2.
		// The real run took the fallthrough (only B0 observed), a genuine
		// divergence at a recognized IF_ICMPLT with observed operands a=10,
		// b=5 (10 < 5 is false, consistent with the fallthrough having
		// actually happened) - wantedTaken=true since B1 is the taken
		// successor. BranchDistance.compute(IF_ICMPLT, 10, 5, true):
		// raw=(10-5)+1=6, normalized=6/7. Approach level contributes nothing
		// extra here (missingCount=1, so missingCount-1=0), leaving the send
		// side at (6/7)/2 (path length 2) = 3/7. The receive side targets
		// the entry block itself, already observed, contributing 0 - so the
		// two-side average is (3/7 + 0) / 2 = 3/14.
		ControlFlowGraph graph = new ControlFlowGraph("P#main:B0", Map.of("P#main:B0",
				java.util.List.of("P#main:B1", "P#main:B2"), "P#main:B1", java.util.List.of(), "P#main:B2",
				java.util.List.of()));
		Map<String, ControlFlowGraph> graphs = Map.of("P#main", graph);
		Map<String, String> syncEdgeBlocks = Map.of("P#main:0", "P#main:B1", "P#main:1", "P#main:B0");
		Map<String, Integer> branchPredicates = Map.of("P#main:B0", Opcodes.IF_ICMPLT);
		Map<Integer, Set<String>> observed = Map.of(0, Set.of("P#main:B0"));
		Map<Integer, Map<String, int[]>> operands = Map.of(0, Map.of("P#main:B0", new int[] { 10, 5 }));
		RequiredEdge edge = messageEdge("P#main:0", "P#main:1");

		double distance = GraphDistance.compute(edge, graphs, syncEdgeBlocks, branchPredicates, observed, operands);

		assertEquals(3.0 / 14.0, distance, 1e-9);
	}

	@Test
	void divergingWithNoRecognizedPredicateFallsBackToTheFlatPenalty() {
		// Same shape as the branch-distance test, but no predicate/operand
		// data is available for the divergence block - must fall back to the
		// flat "1 per missing node" contribution instead of crashing or
		// silently treating it as fully covered.
		ControlFlowGraph graph = new ControlFlowGraph("P#main:B0", Map.of("P#main:B0",
				java.util.List.of("P#main:B1", "P#main:B2"), "P#main:B1", java.util.List.of(), "P#main:B2",
				java.util.List.of()));
		Map<String, ControlFlowGraph> graphs = Map.of("P#main", graph);
		Map<String, String> syncEdgeBlocks = Map.of("P#main:0", "P#main:B1", "P#main:1", "P#main:B0");
		Map<Integer, Set<String>> observed = Map.of(0, Set.of("P#main:B0"));
		RequiredEdge edge = messageEdge("P#main:0", "P#main:1");

		double distance = GraphDistance.compute(edge, graphs, syncEdgeBlocks, NO_PREDICATES, observed, NO_OPERANDS);

		// send side: (missingCount=1, divergenceContribution defaults to 1.0) -> (1-1+1)/2 = 0.5; receive side: 0.
		assertEquals(0.25, distance);
	}

	@Test
	void anUnmappedSyncEdgeIdFailsSoftInsteadOfCrashing() {
		RequiredEdge edge = messageEdge("Missing#main:0", "Missing#main:1");

		double distance = GraphDistance.compute(edge, Map.of(), Map.of(), NO_PREDICATES, Map.of(), NO_OPERANDS);

		assertEquals(1.0, distance, "an edge id with no known block must be treated as maximally far, not crash");
	}

	@Test
	void aMissingGraphForTheMethodKeyFailsSoft() {
		Map<String, String> syncEdgeBlocks = Map.of("P#main:0", "P#main:B0", "P#main:1", "P#main:B0");
		RequiredEdge edge = messageEdge("P#main:0", "P#main:1");

		// syncEdgeBlocks knows about the block, but graphs has no entry for
		// "P#main" at all - e.g. a method whose class failed to instrument.
		double distance = GraphDistance.compute(edge, Map.of(), syncEdgeBlocks, NO_PREDICATES, Map.of(), NO_OPERANDS);

		assertEquals(1.0, distance);
	}

	@Test
	void anUnreachableTargetBlockFailsSoft() {
		ControlFlowGraph graph = new ControlFlowGraph("P#main:B0",
				Map.of("P#main:B0", java.util.List.of(), "P#main:B1", java.util.List.of()));
		Map<String, ControlFlowGraph> graphs = Map.of("P#main", graph);
		// Send side targets B1, an island nothing points to from the entry -
		// unreachable, fails soft to 1.0. Receive side targets the entry
		// block itself, marked observed, so it isolates to exactly 0.0 -
		// keeping the average diagnostic of the send side's fail-soft value.
		Map<String, String> syncEdgeBlocks = Map.of("P#main:0", "P#main:B1", "P#main:1", "P#main:B0");
		RequiredEdge edge = messageEdge("P#main:0", "P#main:1");
		Map<Integer, Set<String>> observed = Map.of(0, Set.of("P#main:B0"));

		double distance = GraphDistance.compute(edge, graphs, syncEdgeBlocks, NO_PREDICATES, observed, NO_OPERANDS);

		assertEquals(0.5, distance);
	}

	@Test
	void causalDistanceBorrowsTheRealSendersSideDistanceInsteadOfTheFlatPenalty() {
		// Outer edge under test: identical shape to
		// divergingWithNoRecognizedPredicateFallsBackToTheFlatPenalty (no
		// recognized predicate at "P#main:B0" for process 0) - but this time
		// causalDistance declares that "P#main:0" should instead borrow
		// process 7's own sideDistance for "R#main:9", simulating the
		// cross-process "flag problem" (a String#equals-gated send fed by
		// another process's message payload, e.g. Coordinator's celebrate
		// check fed by a Peer's own window-check outcome).
		ControlFlowGraph outerGraph = new ControlFlowGraph("P#main:B0", Map.of("P#main:B0",
				java.util.List.of("P#main:B1", "P#main:B2"), "P#main:B1", java.util.List.of(), "P#main:B2",
				java.util.List.of()));
		// The "real sender" (process 7) has its own, separate graph with a
		// genuine recognized numeric predicate - exact same shape/operands
		// as divergingAtARecognizedNumericPredicateUsesBranchDistanceForThatStep,
		// whose send-side sub-result (3/7) is already independently verified
		// there, so this test composes two already-proven formulas instead
		// of inventing a new expected number from scratch.
		ControlFlowGraph chainedGraph = new ControlFlowGraph("R#main:B0", Map.of("R#main:B0",
				java.util.List.of("R#main:B1", "R#main:B2"), "R#main:B1", java.util.List.of(), "R#main:B2",
				java.util.List.of()));
		Map<String, ControlFlowGraph> graphs = Map.of("P#main", outerGraph, "R#main", chainedGraph);
		Map<String, String> syncEdgeBlocks = Map.of("P#main:0", "P#main:B1", "P#main:1", "P#main:B0", "R#main:9",
				"R#main:B1");
		Map<String, Integer> branchPredicates = Map.of("R#main:B0", Opcodes.IF_ICMPLT);
		Map<Integer, Set<String>> observed = Map.of(0, Set.of("P#main:B0"), 7, Set.of("R#main:B0"));
		Map<Integer, Map<String, int[]>> operands = Map.of(7, Map.of("R#main:B0", new int[] { 10, 5 }));
		RequiredEdge edge = messageEdge("P#main:0", "P#main:1");

		Map<String, java.util.List<GraphDistance.CausalSource>> causalDistance = Map.of("P#main:0",
				java.util.List.of(new GraphDistance.CausalSource("VoteEdge", "R#main:9")));
		// Process 0 (the receiver in the outer edge) actually received the
		// payload that satisfied "VoteEdge" from process 7 in this
		// execution - the same correlation CoverageEvaluator.Result already
		// resolves for free while checking MESSAGE-edge coverage.
		Map<Integer, Map<String, Integer>> observedSenderByReceiveEdge = Map.of(0, Map.of("VoteEdge", 7));

		double distance = GraphDistance.compute(edge, graphs, syncEdgeBlocks, branchPredicates, observed, operands,
				observedSenderByReceiveEdge, causalDistance);

		// send side borrows R#main:9's sideDistance for process 7 (3/7,
		// verified independently above) as its divergence contribution:
		// (missingCount=1, -1 + 3/7) / path.size()=2 = (3/7)/2 = 3/14.
		// Receive side targets the entry block itself, observed -> 0.0.
		// Overall: (3/14 + 0) / 2 = 3/28.
		assertEquals(3.0 / 28.0, distance, 1e-9);
	}

	@Test
	void causalDistanceFallsBackToTheFlatPenaltyWhenTheRealSenderWasNeverObserved() {
		// Same declared causalDistance as the test above, but the receive
		// it depends on ("VoteEdge") never happened in THIS execution's
		// trace (observedSenderByReceiveEdge has no entry for it) - must
		// fall back to the exact same flat penalty as
		// divergingWithNoRecognizedPredicateFallsBackToTheFlatPenalty,
		// never crash or silently treat it as covered.
		ControlFlowGraph graph = new ControlFlowGraph("P#main:B0", Map.of("P#main:B0",
				java.util.List.of("P#main:B1", "P#main:B2"), "P#main:B1", java.util.List.of(), "P#main:B2",
				java.util.List.of()));
		Map<String, ControlFlowGraph> graphs = Map.of("P#main", graph);
		Map<String, String> syncEdgeBlocks = Map.of("P#main:0", "P#main:B1", "P#main:1", "P#main:B0");
		Map<Integer, Set<String>> observed = Map.of(0, Set.of("P#main:B0"));
		RequiredEdge edge = messageEdge("P#main:0", "P#main:1");
		Map<String, java.util.List<GraphDistance.CausalSource>> causalDistance = Map.of("P#main:0",
				java.util.List.of(new GraphDistance.CausalSource("VoteEdge", "R#main:9")));

		double distance = GraphDistance.compute(edge, graphs, syncEdgeBlocks, NO_PREDICATES, observed, NO_OPERANDS,
				Map.of(), causalDistance);

		assertEquals(0.25, distance);
	}

	@Test
	void computeAveragesTheSendAndReceiveSidesEqually() {
		ControlFlowGraph graph = new ControlFlowGraph("P#main:B0",
				Map.of("P#main:B0", java.util.List.of(), "P#main:B1", java.util.List.of()));
		Map<String, ControlFlowGraph> graphs = Map.of("P#main", graph);
		// Send side (edge :0) targets the entry block itself, observed -
		// distance 0.0. Receive side (edge :1) has no syncEdgeBlocks entry
		// at all, so it fails soft to 1.0 - isolating compute()'s averaging
		// itself from sideDistance's own internal formula (already covered
		// by the other tests above).
		Map<String, String> syncEdgeBlocks = Map.of("P#main:0", "P#main:B0");
		RequiredEdge edge = messageEdge("P#main:0", "P#main:1");
		Map<Integer, Set<String>> observed = Map.of(0, Set.of("P#main:B0"));

		double distance = GraphDistance.compute(edge, graphs, syncEdgeBlocks, NO_PREDICATES, observed, NO_OPERANDS);

		assertEquals(0.5, distance, "average of 0.0 (send, fully observed) and 1.0 (receive, unmapped) is 0.5");
	}
}
