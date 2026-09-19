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
	void scopedCausalSourceTakesPrecedenceOverTheRoleLevelOneForTheSameProcess() {
		// Same shape as divergingAtARecognizedNumericPredicateUsesBranchDistanceForThatStep
		// (already independently verified there: BranchDistance.compute(IF_ICMPLT, 10, 5, true) = 6/7,
		// so the send side alone is (6/7)/2 = 3/7 once divided by this test's
		// path length of 2), but the LOCAL source is now declared TWICE for
		// the same edge id: once role-level (pointing at a block with no
		// recorded operands at all, which would fail soft to the flat 0.5
		// pattern if it were the one actually used) and once scoped to
		// process 4 specifically (pointing at the real, resolvable block).
		// A scoped-with-fallback lookup must pick the scoped entry, not
		// silently prefer or merge in the role-level one.
		ControlFlowGraph graph = new ControlFlowGraph("P#main:B0", Map.of("P#main:B0",
				java.util.List.of("P#main:B1", "P#main:B2"), "P#main:B1", java.util.List.of(), "P#main:B2",
				java.util.List.of()));
		Map<String, ControlFlowGraph> graphs = Map.of("P#main", graph);
		Map<String, String> syncEdgeBlocks = Map.of("P#main:0", "P#main:B1", "P#main:1", "P#main:B0");
		Map<String, Integer> branchPredicates = Map.of("P#main:B0", Opcodes.IF_ICMPLT);
		Map<Integer, Set<String>> observed = Map.of(4, Set.of("P#main:B0"));
		Map<Integer, Map<String, int[]>> operands = Map.of(4, Map.of("P#main:B0", new int[] { 10, 5 }));
		RequiredEdge edge = new RequiredEdge(RequiredEdge.Kind.MESSAGE, 4, "P#main:0", 4, "P#main:1");

		Map<String, java.util.List<GraphDistance.CausalSource>> causalDistance = Map.of(
				"P#main:0", java.util.List.of(new GraphDistance.CausalSource("NoSuchBlock#main:B0", true)),
				"4@P#main:0", java.util.List.of(new GraphDistance.CausalSource("P#main:B0", true)));

		double distance = GraphDistance.compute(edge, graphs, syncEdgeBlocks, branchPredicates, observed, operands,
				Map.of(), causalDistance);

		// send side ("P#main:0" -> B1, path [B0,B1] length 2): B0 is
		// observed so only B1 is missing (missingCount=1); the scoped
		// source's divergenceContribution = 6/7 -> (1-1+6/7)/2 = 3/7.
		// receive side ("P#main:1" -> B0, path [B0] length 1): B0 IS
		// observed for process 4 -> missingCount=0 -> 0.0 immediately.
		// Average: (3/7 + 0) / 2 = 3/14.
		assertEquals(3.0 / 14.0, distance, 1e-9,
				"the scoped 4@P#main:0 source must win over the role-level P#main:0 one");
	}

	@Test
	void roleLevelCausalSourceStillAppliesToAProcessWithNoScopedOverrideOfItsOwn() {
		// Two processes share the same role-level causalDistance entry (no
		// scoped key for EITHER of them this time) - confirms that simply
		// having the scoped-lookup machinery in place does not change the
		// plain single-key behavior every existing benchmark config relies
		// on. Reuses the exact shape/numbers as
		// divergingAtARecognizedNumericPredicateUsesBranchDistanceForThatStep.
		ControlFlowGraph graph = new ControlFlowGraph("P#main:B0", Map.of("P#main:B0",
				java.util.List.of("P#main:B1", "P#main:B2"), "P#main:B1", java.util.List.of(), "P#main:B2",
				java.util.List.of()));
		Map<String, ControlFlowGraph> graphs = Map.of("P#main", graph);
		Map<String, String> syncEdgeBlocks = Map.of("P#main:0", "P#main:B1", "P#main:1", "P#main:B0");
		Map<String, Integer> branchPredicates = Map.of("P#main:B0", Opcodes.IF_ICMPLT);
		Map<Integer, Set<String>> observed = Map.of(0, Set.of("P#main:B0"));
		Map<Integer, Map<String, int[]>> operands = Map.of(0, Map.of("P#main:B0", new int[] { 10, 5 }));
		RequiredEdge edge = messageEdge("P#main:0", "P#main:1");
		Map<String, java.util.List<GraphDistance.CausalSource>> causalDistance = Map.of("P#main:0",
				java.util.List.of(new GraphDistance.CausalSource("P#main:B0", true)));

		double distance = GraphDistance.compute(edge, graphs, syncEdgeBlocks, branchPredicates, observed, operands,
				Map.of(), causalDistance);

		assertEquals(3.0 / 14.0, distance, 1e-9);
	}

	@Test
	void aProcessWithNeitherAScopedNorARoleLevelSourceFallsBackToTheStructuralDefault() {
		// The base-of-recursion case: causalDistance has entries for OTHER
		// processes/edges, but none at all - scoped or role-level - for
		// this specific (processId, syncEdgeId) pair. Must behave exactly
		// like divergingWithNoRecognizedPredicateFallsBackToTheFlatPenalty,
		// not crash or silently treat the edge as resolved.
		ControlFlowGraph graph = new ControlFlowGraph("P#main:B0", Map.of("P#main:B0",
				java.util.List.of("P#main:B1", "P#main:B2"), "P#main:B1", java.util.List.of(), "P#main:B2",
				java.util.List.of()));
		Map<String, ControlFlowGraph> graphs = Map.of("P#main", graph);
		Map<String, String> syncEdgeBlocks = Map.of("P#main:0", "P#main:B1", "P#main:1", "P#main:B0");
		Map<Integer, Set<String>> observed = Map.of(0, Set.of("P#main:B0"));
		RequiredEdge edge = messageEdge("P#main:0", "P#main:1");
		// Present, but for a completely unrelated edge id - proves an
		// unrelated entry elsewhere in the map cannot leak into this one.
		Map<String, java.util.List<GraphDistance.CausalSource>> causalDistance = Map.of("Other#main:0",
				java.util.List.of(new GraphDistance.CausalSource("P#main:B0", true)));

		double distance = GraphDistance.compute(edge, graphs, syncEdgeBlocks, NO_PREDICATES, observed, NO_OPERANDS,
				Map.of(), causalDistance);

		assertEquals(0.25, distance);
	}

	@Test
	void chainedScopedSourcesRecurseThroughEveryLinkDownToTheRoleLevelBaseCase() {
		// Three instances of the SAME shared role (like RIP's four Router
		// instances, or any linear chain) - process 3's send borrows
		// process 2's, which borrows process 1's, which has no scoped
		// override at all and terminates on the plain role-level LOCAL
		// source (the chain's base case, e.g. the first router with no
		// same-role upstream to recurse into). All three share one
		// ControlFlowGraph/syncEdgeBlocks (same class), matching how four
		// Router instances share Router#main's block ids in practice.
		ControlFlowGraph graph = new ControlFlowGraph("P#main:B0",
				Map.of("P#main:B0", java.util.List.of(), "P#main:B1", java.util.List.of()));
		Map<String, ControlFlowGraph> graphs = Map.of("P#main", graph);
		Map<String, String> syncEdgeBlocks = Map.of("P#main:0", "P#main:B0", "P#main:1", "P#main:B0");
		Map<String, Integer> branchPredicates = Map.of("P#main:B1", Opcodes.IF_ICMPLT);
		// None of the three processes observed B0 itself (forcing
		// missingCount=1 for each one's own sideDistance("P#main:0", ...)),
		// but process 1 DID reach B1 (its own local predicate) with real
		// operands - the value every level of the chain must ultimately
		// recover.
		Map<Integer, Set<String>> observed = Map.of(1, Set.of("P#main:B1"));
		Map<Integer, Map<String, int[]>> operands = Map.of(1, Map.of("P#main:B1", new int[] { 10, 5 }));
		Map<Integer, Map<String, Integer>> observedSenderByReceiveEdge = Map.of(
				3, Map.of("P#main:5", 2),
				2, Map.of("P#main:5", 1));
		Map<String, java.util.List<GraphDistance.CausalSource>> causalDistance = Map.of(
				// Base case: process 1 has no scoped "1@P#main:0" entry at
				// all, so it falls back to this role-level LOCAL source and
				// the recursion terminates here.
				"P#main:0", java.util.List.of(new GraphDistance.CausalSource("P#main:B1", true)),
				// Each non-base instance's scoped entry recurses into
				// whichever process actually fed its own receive.
				"2@P#main:0",
				java.util.List.of(new GraphDistance.CausalSource("P#main:5", "P#main:0")),
				"3@P#main:0",
				java.util.List.of(new GraphDistance.CausalSource("P#main:5", "P#main:0")));
		// Edge under test is process 3's send; its receiver side targets
		// P#main:1, mapped to the same entry block B0, unobserved for
		// process 3 and with no causalDistance entry of its own - since B0
		// IS the entry block, missingCount=1 but firstMissingIdx=0, so the
		// structural-fallback branch (which requires firstMissingIdx>0)
		// never fires either, leaving divergenceContribution at its 1.0
		// default. This isolates the chain's result to the send side alone.
		RequiredEdge edge = new RequiredEdge(RequiredEdge.Kind.MESSAGE, 3, "P#main:0", 3, "P#main:1");

		double distance = GraphDistance.compute(edge, graphs, syncEdgeBlocks, branchPredicates, observed, operands,
				observedSenderByReceiveEdge, causalDistance);

		// Base (process 1): BranchDistance.compute(IF_ICMPLT, 10, 5, true) = 6/7
		// (independently verified in divergingAtARecognizedNumericPredicateUsesBranchDistanceForThatStep,
		// same raw value, that test then divides by ITS OWN path length of
		// 2 - here the path is length 1, so the raw 6/7 is not divided
		// further): path P#main:0 -> P#main:B0 is the entry itself (length
		// 1) -> sideDistance("P#main:0", 1) = (1-1+6/7)/1 = 6/7.
		// process 2 recurses into process 1's 6/7, same path length 1 ->
		// (1-1+6/7)/1 = 6/7. process 3 recurses into process 2's 6/7 the
		// same way -> send side = 6/7.
		// Receive side: P#main:1 also maps to the entry block B0, which is
		// unobserved for process 3 too - missingCount=1 but
		// firstMissingIdx=0, so the structural-fallback branch (guarded by
		// firstMissingIdx>0) never fires, leaving the 1.0 default.
		// Overall: (6/7 + 1.0) / 2 = 13/14.
		assertEquals(13.0 / 14.0, distance, 1e-9,
				"process 3's distance must reflect process 1's real local predicate through two levels of recursion, not fail soft at process 2 or 3");
	}

	@Test
	void aScopedListMayCombineALocalSourceWithACrossSourceInTheSameProcess() {
		// Exactly the shape R2..R4 need in a linear chain like RIP: the
		// SAME scoped entry both looks at this process's own local
		// predicate (in case saturation/divergence happens right here)
		// AND recurses into whichever process fed its own receive (in
		// case the real cause is further upstream) - the two contributions
		// are summed, not chosen between.
		ControlFlowGraph graph = new ControlFlowGraph("P#main:B0",
				Map.of("P#main:B0", java.util.List.of(), "P#main:B1", java.util.List.of()));
		Map<String, ControlFlowGraph> graphs = Map.of("P#main", graph);
		Map<String, String> syncEdgeBlocks = Map.of("P#main:0", "P#main:B0", "P#main:1", "P#main:B0");
		Map<String, Integer> branchPredicates = Map.of("P#main:B1", Opcodes.IF_ICMPLT);
		// Process 1 (upstream) has its own local predicate observed -
		// this is the base of the chain, reached recursively below.
		// Process 2 ALSO has its own local predicate observed (distinct
		// operands, so its contribution is independently checkable), on
		// top of recursing into process 1.
		Map<Integer, Set<String>> observed = Map.of(1, Set.of("P#main:B1"), 2, Set.of("P#main:B1"));
		Map<Integer, Map<String, int[]>> operands = Map.of(
				1, Map.of("P#main:B1", new int[] { 10, 5 }),
				2, Map.of("P#main:B1", new int[] { 20, 5 }));
		Map<Integer, Map<String, Integer>> observedSenderByReceiveEdge = Map.of(2, Map.of("P#main:5", 1));
		Map<String, java.util.List<GraphDistance.CausalSource>> causalDistance = Map.of(
				"P#main:0", java.util.List.of(new GraphDistance.CausalSource("P#main:B1", true)),
				"2@P#main:0", java.util.List.of(
						new GraphDistance.CausalSource("P#main:B1", true),
						new GraphDistance.CausalSource("P#main:5", "P#main:0")));
		RequiredEdge edge = new RequiredEdge(RequiredEdge.Kind.MESSAGE, 2, "P#main:0", 2, "P#main:1");

		double distance = GraphDistance.compute(edge, graphs, syncEdgeBlocks, branchPredicates, observed, operands,
				observedSenderByReceiveEdge, causalDistance);

		// Process 1's own sideDistance("P#main:0", 1) - base of the chain,
		// no scoped override, falls to the role-level local source:
		// BranchDistance.compute(IF_ICMPLT, 10, 5, true) = 6/7 (already
		// proven elsewhere in this file), path length 1 -> 6/7.
		// Process 2's local contribution: BranchDistance.compute(IF_ICMPLT, 20, 5, true).
		// raw = (20-5)+1 = 16, normalized = 16/17 (BranchDistance clamps
		// the normalized form to (raw)/(raw+1) - same formula as the 6/7
		// case: 6/(6+1)).
		// chainedSum for process 2 = local (16/17) + cross (6/7, process 1's
		// result) - summed, not averaged or chosen between. missingCount=1,
		// path length 1 -> send side = 16/17 + 6/7.
		// Receive side: P#main:1 -> B0, unobserved for process 2,
		// missingCount=1 but firstMissingIdx=0 (entry block itself) -> the
		// structural-fallback branch never fires -> stays at the 1.0 default.
		double expectedLocal = 16.0 / 17.0;
		double expectedCross = 6.0 / 7.0;
		double expectedSend = expectedLocal + expectedCross;
		double expectedDistance = (expectedSend + 1.0) / 2.0;
		assertEquals(expectedDistance, distance, 1e-9,
				"a scoped list's local and cross contributions must both be summed, not one replacing the other");
	}

	@Test
	void aCycleInTheDeclaredChainSkipsJustThatSourceInsteadOfRecursingForever() {
		// Two processes each configured to recurse into the other -
		// exactly the kind of misconfiguration this project's own
		// dependency model assumes never happens (finite, acyclic chains
		// only), but must fail soft rather than StackOverflowError and
		// take down an entire GA run over one bad edge.
		ControlFlowGraph graph = new ControlFlowGraph("P#main:B0",
				Map.of("P#main:B0", java.util.List.of(), "P#main:B1", java.util.List.of()));
		Map<String, ControlFlowGraph> graphs = Map.of("P#main", graph);
		Map<String, String> syncEdgeBlocks = Map.of("P#main:0", "P#main:B0", "P#main:1", "P#main:B0");
		Map<Integer, Map<String, Integer>> observedSenderByReceiveEdge = Map.of(
				1, Map.of("P#main:5", 2),
				2, Map.of("P#main:5", 1));
		Map<String, java.util.List<GraphDistance.CausalSource>> causalDistance = Map.of(
				"1@P#main:0", java.util.List.of(new GraphDistance.CausalSource("P#main:5", "P#main:0")),
				"2@P#main:0", java.util.List.of(new GraphDistance.CausalSource("P#main:5", "P#main:0")));
		RequiredEdge edge = new RequiredEdge(RequiredEdge.Kind.MESSAGE, 1, "P#main:0", 1, "P#main:1");

		double distance = GraphDistance.compute(edge, graphs, syncEdgeBlocks, NO_PREDICATES, Map.of(), NO_OPERANDS,
				observedSenderByReceiveEdge, causalDistance);

		// process1 recurses into process2 (pair "2@P#main:0", not yet
		// active); process2 tries to recurse back into process1, but
		// "1@P#main:0" is already on the active path - that source is
		// skipped, process2 resolves nothing and falls back to the
		// structural default (1.0, since firstMissingIdx=0 here skips the
		// normal structural-fallback branch too). process1's chainedSum
		// borrows that 1.0 -> send side = (1-1+1.0)/1 = 1.0. Receive side
		// (P#main:1 -> same entry block, unobserved) also stays at 1.0 by
		// the same reasoning. Overall: (1.0 + 1.0) / 2 = 1.0 - finite,
		// not a crash, and no worse than the ordinary fail-soft maximum.
		assertEquals(1.0, distance, 1e-9);
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
