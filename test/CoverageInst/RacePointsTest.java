package CoverageInst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import CoverageInst.SyncPoint.Kind;

/**
 * RacePoints.detect is a group-by over RequiredElementsGenerator's own
 * output, not a new static analysis - these fixtures exercise that
 * boundary: which shapes of ambiguous receive DO count as a race point
 * (genuinely more than one legitimate sender), and which structurally
 * similar shapes must NOT (a single sender, or a non-MESSAGE edge).
 */
class RacePointsTest {

	private static ProcessInstance process(int id, String role, SyncPoint... points) {
		return new ProcessInstance(id, role, List.of(points));
	}

	private static SyncPoint send(String edgeId) {
		return new SyncPoint(edgeId, Kind.SEND, "Fixture");
	}

	private static SyncPoint receive(String edgeId) {
		return new SyncPoint(edgeId, Kind.RECEIVE, "Fixture");
	}

	private static Set<String> relatedEdgeStrings(RacePoint racePoint) {
		return racePoint.relatedEdges.stream().map(RequiredEdge::toString).collect(Collectors.toSet());
	}

	@Test
	void twoLegitimateSendersToTheSameReceiveEdgeFormOneRacePoint() {
		// Exactly quorum-handshake's Coordinator shape: two Peer instances,
		// no fixedMessageSources restricting who can feed the receive.
		ProcessInstance peer1 = process(1, "Peer", send("Peer#main:0"));
		ProcessInstance peer2 = process(2, "Peer", send("Peer#main:0"));
		ProcessInstance coordinator = process(0, "Coordinator", receive("Coordinator#main:0"));

		List<RequiredEdge> required = RequiredElementsGenerator.generate(List.of(peer1, peer2, coordinator),
				new Topology(List.of(new RoleLink("Peer", "Coordinator")), Map.of(), Map.of(), Map.of(), Map.of()));
		List<RacePoint> racePoints = RacePoints.detect(required);

		assertEquals(1, racePoints.size());
		RacePoint racePoint = racePoints.get(0);
		assertEquals(0, racePoint.destinationProcessId);
		assertEquals(List.of(1, 2), racePoint.candidateSenderIds);
		assertEquals(Set.of("Peer#main:0@p1 -> Coordinator#main:0@p0", "Peer#main:0@p2 -> Coordinator#main:0@p0"),
				relatedEdgeStrings(racePoint));
	}

	@Test
	void aSingleLegitimateSenderIsNotARacePoint() {
		ProcessInstance sender = process(1, "Peer", send("Peer#main:0"));
		ProcessInstance receiver = process(0, "Coordinator", receive("Coordinator#main:0"));

		List<RequiredEdge> required = RequiredElementsGenerator.generate(List.of(sender, receiver),
				new Topology(List.of(new RoleLink("Peer", "Coordinator")), Map.of(), Map.of(), Map.of(), Map.of()));

		assertTrue(RacePoints.detect(required).isEmpty(),
				"a receive point with only one legitimate sender must never be reported as ambiguous");
	}

	@Test
	void fixedMessageSourcesNarrowingToOneSenderRemovesTheRacePoint() {
		// Same two Peer instances as the first test, but fixedMessageSources
		// pins the receive to only one of them - RacePoints must respect
		// that narrowing exactly like RequiredElementsGenerator already does,
		// since it only groups the edges that survive that filtering.
		ProcessInstance peer1 = process(1, "Peer", send("Peer#main:0"));
		ProcessInstance peer2 = process(2, "Peer", send("Peer#main:0"));
		ProcessInstance coordinator = process(0, "Coordinator", receive("Coordinator#main:0"));

		List<RequiredEdge> required = RequiredElementsGenerator.generate(List.of(peer1, peer2, coordinator),
				new Topology(List.of(new RoleLink("Peer", "Coordinator")), Map.of(),
						Map.of("Coordinator#main:0", List.of(1)), Map.of(), Map.of()));

		assertTrue(RacePoints.detect(required).isEmpty(),
				"narrowing to a single allowed sender must remove the ambiguity entirely");
	}

	@Test
	void twoAmbiguousReceiveEdgesOnTheSameDestinationMergeIntoOneRacePoint() {
		// quorum-handshake's actual shape: Coordinator has TWO receive calls
		// (votePacket1, votePacket2), both fed by the same two Peers -
		// ReplaySchedule/CoverageTracer only control ordering per
		// DESTINATION PROCESS, not per receive call site, so both ambiguous
		// edges at process 0 must collapse into a single RacePoint, not two.
		ProcessInstance peer1 = process(1, "Peer", send("Peer#main:0"));
		ProcessInstance peer2 = process(2, "Peer", send("Peer#main:0"));
		ProcessInstance coordinator = process(0, "Coordinator", receive("Coordinator#main:0"),
				receive("Coordinator#main:1"));

		List<RequiredEdge> required = RequiredElementsGenerator.generate(List.of(peer1, peer2, coordinator),
				new Topology(List.of(new RoleLink("Peer", "Coordinator")), Map.of(), Map.of(), Map.of(), Map.of()));
		List<RacePoint> racePoints = RacePoints.detect(required);

		assertEquals(1, racePoints.size(), "one race point per DESTINATION PROCESS, not per receive edge");
		assertEquals(List.of(1, 2), racePoints.get(0).candidateSenderIds);
		assertEquals(4, racePoints.get(0).relatedEdges.size());
	}

	@Test
	void identityKindEdgesAreNeverConsideredRacePoints() {
		// Semaphore/Lock/CyclicBarrier edges are symmetric same-process
		// rendezvous, not a sender-arrival-order race ReplaySchedule can
		// even express - RacePoints.detect must only ever look at MESSAGE edges.
		SyncPoint release = new SyncPoint("P#run:0", Kind.SEM_RELEASE, "P");
		SyncPoint acquire = new SyncPoint("P#run:1", Kind.SEM_ACQUIRE, "P");
		ProcessInstance process = process(0, "Slave", release, acquire);

		List<RequiredEdge> required = RequiredElementsGenerator.generate(List.of(process),
				new Topology(List.of(), Map.of(), Map.of(), Map.of(), Map.of()));

		assertTrue(RacePoints.detect(required).isEmpty());
	}
}
