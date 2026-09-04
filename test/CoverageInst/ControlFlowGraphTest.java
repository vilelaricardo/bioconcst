package CoverageInst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * ControlFlowGraph is what GraphDistance walks to find the shortest path to
 * an uncovered sync edge, and where the taken/fallthrough distinction feeds
 * BranchDistance's wantedTaken flag - a wrong path or a swapped
 * taken/fallthrough here would silently corrupt the whole search gradient,
 * so it's tested directly against hand-built successor maps rather than only
 * through bytecode-derived graphs.
 */
class ControlFlowGraphTest {

	@Test
	void shortestPathToTheEntryBlockItselfIsJustThatBlock() {
		ControlFlowGraph graph = new ControlFlowGraph("B0", Map.of("B0", List.of("B1")));

		assertEquals(List.of("B0"), graph.shortestPathTo("B0"));
	}

	@Test
	void shortestPathToAnUnreachableBlockIsEmpty() {
		ControlFlowGraph graph = new ControlFlowGraph("B0", Map.of("B0", List.of("B1")));

		assertTrue(graph.shortestPathTo("B99").isEmpty());
	}

	@Test
	void shortestPathPicksTheShorterOfTwoRoutes() {
		// B0 -> B1 -> B2 -> B3 (long way) and B0 -> B3 (short way, e.g. an
		// early-return branch) - BFS must prefer the 2-hop route.
		ControlFlowGraph graph = new ControlFlowGraph("B0",
				Map.of("B0", List.of("B1", "B3"), "B1", List.of("B2"), "B2", List.of("B3"), "B3", List.of()));

		assertEquals(List.of("B0", "B3"), graph.shortestPathTo("B3"));
	}

	@Test
	void shortestPathThroughABranchFollowsTheCorrectArm() {
		ControlFlowGraph graph = new ControlFlowGraph("B0",
				Map.of("B0", List.of("B1", "B2"), "B1", List.of("B3"), "B2", List.of("B3"), "B3", List.of()));

		List<String> path = graph.shortestPathTo("B3");

		assertEquals(3, path.size());
		assertEquals("B0", path.get(0));
		assertEquals("B3", path.get(2));
		assertTrue(path.get(1).equals("B1") || path.get(1).equals("B2"));
	}

	@Test
	void shortestPathTerminatesAndIsCorrectWhenTheGraphHasACycle() {
		// A loop back edge (B1 -> B0) must not cause infinite traversal.
		ControlFlowGraph graph = new ControlFlowGraph("B0",
				Map.of("B0", List.of("B1"), "B1", List.of("B0", "B2"), "B2", List.of()));

		assertEquals(List.of("B0", "B1", "B2"), graph.shortestPathTo("B2"));
	}

	@Test
	void takenSuccessorIsTheFirstEntryAndFallthroughIsTheSecond() {
		ControlFlowGraph graph = new ControlFlowGraph("B0", Map.of("B0", List.of("TAKEN", "FALLTHROUGH")));

		assertEquals("TAKEN", graph.takenSuccessor("B0"));
		assertEquals("FALLTHROUGH", graph.fallthroughSuccessor("B0"));
	}

	@Test
	void takenAndFallthroughAreNullWhenNotApplicable() {
		ControlFlowGraph graph = new ControlFlowGraph("B0", Map.of("B0", List.of("ONLY")));

		assertEquals("ONLY", graph.takenSuccessor("B0"));
		assertNull(graph.fallthroughSuccessor("B0"), "a block with only one successor has no fallthrough side");
		assertNull(graph.takenSuccessor("UNKNOWN"));
	}
}
