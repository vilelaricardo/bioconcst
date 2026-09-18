package CoverageInst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * CoverageSweepMain.recordAttempt isolates the one piece of the sweep loop
 * that had a real bug (Codex's audit, sync-claude-codex.md 2026-09-18 (11)
 * point 5): an earlier version only credited an attempt's covered edges
 * when they were inside the RacePoint currently being swept, silently
 * discarding coverage of any edge outside it. These fixtures exercise
 * exactly that boundary without needing a real process execution.
 */
class CoverageSweepMainTest {

	private static RequiredEdge messageEdge(int senderProcessId, String senderEdgeId, int receiverProcessId,
			String receiverEdgeId) {
		return new RequiredEdge(RequiredEdge.Kind.MESSAGE, senderProcessId, senderEdgeId, receiverProcessId,
				receiverEdgeId);
	}

	@Test
	void creditsAnEdgeOutsideTheCurrentRacePointInsteadOfDiscardingIt() {
		RequiredEdge ownEdge = messageEdge(1, "Peer#main:0", 0, "Coordinator#main:0");
		RequiredEdge outsideEdge = messageEdge(2, "Peer#main:1", 0, "Coordinator#main:5");

		Set<String> coveredSoFar = new LinkedHashSet<>();
		Map<String, CoverageSweepMain.Witness> witnesses = new LinkedHashMap<>();

		// This attempt happens to cover an edge that is NOT part of the
		// RacePoint being swept (ownEdge) - only the unrelated one.
		Set<String> coveredThisAttempt = Set.of(outsideEdge.toString());

		boolean racePointClosed = CoverageSweepMain.recordAttempt(coveredSoFar, witnesses, coveredThisAttempt,
				List.of(ownEdge), new int[] { 1, 2 }, "0 1,2", 0);

		assertFalse(racePointClosed, "the swept RacePoint's own edge is still uncovered");
		assertTrue(coveredSoFar.contains(outsideEdge.toString()),
				"the incidentally-covered edge outside the RacePoint must still be credited, not discarded");
		assertTrue(witnesses.containsKey(outsideEdge.toString()), "a witness must be recorded for it");
	}

	@Test
	void reportsTheRacePointClosedOnceAllOfItsOwnEdgesAreCovered() {
		RequiredEdge edgeA = messageEdge(1, "Peer#main:0", 0, "Coordinator#main:0");
		RequiredEdge edgeB = messageEdge(2, "Peer#main:0", 0, "Coordinator#main:0");

		Set<String> coveredSoFar = new LinkedHashSet<>();
		Map<String, CoverageSweepMain.Witness> witnesses = new LinkedHashMap<>();

		boolean afterFirst = CoverageSweepMain.recordAttempt(coveredSoFar, witnesses, Set.of(edgeA.toString()),
				List.of(edgeA, edgeB), new int[] { 1, 2 }, "0 1,2", 0);
		assertFalse(afterFirst, "only one of the two required edges is covered so far");

		boolean afterSecond = CoverageSweepMain.recordAttempt(coveredSoFar, witnesses, Set.of(edgeB.toString()),
				List.of(edgeA, edgeB), new int[] { 3, 4 }, "0 2,1", 0);
		assertTrue(afterSecond, "both of the RacePoint's own edges are now covered");
	}

	@Test
	void doesNotOverwriteAnExistingWitnessForAnAlreadyCoveredEdge() {
		RequiredEdge edge = messageEdge(1, "Peer#main:0", 0, "Coordinator#main:0");

		Set<String> coveredSoFar = new LinkedHashSet<>();
		Map<String, CoverageSweepMain.Witness> witnesses = new LinkedHashMap<>();

		CoverageSweepMain.recordAttempt(coveredSoFar, witnesses, Set.of(edge.toString()), List.of(edge),
				new int[] { 111 }, "0 1", 0);
		CoverageSweepMain.recordAttempt(coveredSoFar, witnesses, Set.of(edge.toString()), List.of(edge),
				new int[] { 999 }, "0 1", 0);

		assertEquals(1, witnesses.size());
		assertEquals(111, witnesses.get(edge.toString()).genotype[0],
				"the first witness that closed the edge should be kept, not replaced");
	}
}
