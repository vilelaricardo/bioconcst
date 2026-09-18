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
 * Locks down RequiredElementsGenerator's exact pairing rules - the piece
 * responsible for every topology bug hand-debugged this session (spurious
 * cross-pairings, missing per-instance scoping, the empty-array exclusion
 * trick). These are the same rules RequiredElementsMain prints for a real
 * benchmark; here they run against tiny hand-built ProcessInstance/SyncPoint
 * fixtures instead, so a wrong pairing shows up in milliseconds instead of
 * after a multi-minute GA run.
 */
class RequiredElementsGeneratorTest {

	private static ProcessInstance process(int id, String role, SyncPoint... points) {
		return new ProcessInstance(id, role, List.of(points));
	}

	private static SyncPoint send(String edgeId) {
		return new SyncPoint(edgeId, Kind.SEND, "Fixture");
	}

	private static SyncPoint receive(String edgeId) {
		return new SyncPoint(edgeId, Kind.RECEIVE, "Fixture");
	}

	private static Topology topology(List<RoleLink> links, Map<String, Integer> targets,
			Map<String, List<Integer>> sources, Map<String, String> groups) {
		return new Topology(links, targets, sources, groups, Map.of());
	}

	private static Set<String> asStrings(List<RequiredEdge> edges) {
		return edges.stream().map(RequiredEdge::toString).collect(Collectors.toSet());
	}

	@Test
	void roleLinkAloneProducesTheFullCartesianProduct() {
		ProcessInstance sender = process(0, "Peer", send("Peer#main:0"));
		ProcessInstance receiverA = process(1, "Coordinator", receive("Coordinator#main:0"));
		ProcessInstance receiverB = process(2, "Coordinator", receive("Coordinator#main:0"));

		List<RequiredEdge> edges = RequiredElementsGenerator.generate(List.of(sender, receiverA, receiverB),
				topology(List.of(new RoleLink("Peer", "Coordinator")), Map.of(), Map.of(), Map.of()));

		assertEquals(Set.of("Peer#main:0@p0 -> Coordinator#main:0@p1", "Peer#main:0@p0 -> Coordinator#main:0@p2"),
				asStrings(edges));
	}

	@Test
	void unrelatedRolesNeverPairEvenWhenAnotherLinkExists() {
		ProcessInstance a = process(0, "A", send("A#main:0"));
		ProcessInstance b = process(1, "B", receive("B#main:0"));
		ProcessInstance c = process(2, "C", receive("C#main:0"));

		List<RequiredEdge> edges = RequiredElementsGenerator.generate(List.of(a, b, c),
				topology(List.of(new RoleLink("A", "B")), Map.of(), Map.of(), Map.of()));

		assertEquals(Set.of("A#main:0@p0 -> B#main:0@p1"), asStrings(edges));
	}

	@Test
	void sameProcessNeverPairsWithItselfOnAMessageEdge() {
		// A process running both roles would otherwise self-pair if the role
		// happened to appear on both sides of a link - guarded explicitly.
		ProcessInstance solo = process(0, "Loopback", send("Loopback#main:0"), receive("Loopback#main:1"));

		List<RequiredEdge> edges = RequiredElementsGenerator.generate(List.of(solo),
				topology(List.of(new RoleLink("Loopback", "Loopback")), Map.of(), Map.of(), Map.of()));

		assertTrue(edges.isEmpty(), "a process must never be paired with itself: " + edges);
	}

	@Test
	void fixedMessageTargetsUnscopedPinsEverySenderToOneReceiver() {
		ProcessInstance sender = process(0, "Master", send("Master#main:0"));
		ProcessInstance slave1 = process(1, "Slave", receive("Slave#main:0"));
		ProcessInstance slave2 = process(2, "Slave", receive("Slave#main:0"));

		List<RequiredEdge> edges = RequiredElementsGenerator.generate(List.of(sender, slave1, slave2),
				topology(List.of(new RoleLink("Master", "Slave")), Map.of("Master#main:0", 2), Map.of(), Map.of()));

		assertEquals(Set.of("Master#main:0@p0 -> Slave#main:0@p2"), asStrings(edges));
	}

	@Test
	void fixedMessageTargetsIsScopedPerInstanceForARingTopology() {
		// Two Slave instances share the same bytecode (same edgeId "Slave#main:0"),
		// but each one's real neighbor differs - exactly 003_token_ring_file's shape.
		ProcessInstance slave1 = process(1, "Slave", send("Slave#main:0"));
		ProcessInstance slave2 = process(2, "Slave", send("Slave#main:0"));
		ProcessInstance target0 = process(0, "Slave", receive("Slave#main:1"));
		ProcessInstance target3 = process(3, "Slave", receive("Slave#main:1"));

		Map<String, Integer> targets = Map.of("1@Slave#main:0", 3, "2@Slave#main:0", 0);
		List<RequiredEdge> edges = RequiredElementsGenerator.generate(List.of(slave1, slave2, target0, target3),
				topology(List.of(new RoleLink("Slave", "Slave")), targets, Map.of(), Map.of()));

		assertEquals(Set.of("Slave#main:0@p1 -> Slave#main:1@p3", "Slave#main:0@p2 -> Slave#main:1@p0"),
				asStrings(edges));
	}

	@Test
	void fixedMessageTargetsAloneDoesNotRestrictWhichReceiveEdgeOnTheTargetProcess() {
		// fixedMessageTargets only pins WHICH PROCESS a send reaches - it
		// does not restrict which of that process's several receive points
		// it can pair with. This exact gap was self-caught twice this
		// session (010_token_ring_both_directions_different_primitives and
		// 011_parallel_sieve_of_eratosthenes): fixedMessageSources on the
		// OTHER receive point is what's actually needed to fully
		// disambiguate - see the next test.
		ProcessInstance senderA = process(1, "Slave", send("Slave#main:0"));
		ProcessInstance target = process(2, "Slave", receive("Slave#main:0"), receive("Slave#main:1"));

		List<RequiredEdge> edges = RequiredElementsGenerator.generate(List.of(senderA, target),
				topology(List.of(new RoleLink("Slave", "Slave")), Map.of("1@Slave#main:0", 2), Map.of(), Map.of()));

		assertEquals(Set.of("Slave#main:0@p1 -> Slave#main:0@p2", "Slave#main:0@p1 -> Slave#main:1@p2"),
				asStrings(edges), "target-pinning alone can't tell the two receive points on process 2 apart");
	}

	@Test
	void fixedMessageSourcesOnTheOtherReceivePointClosesTheGapTargetPinningLeavesOpen() {
		ProcessInstance senderA = process(1, "Slave", send("Slave#main:0"));
		ProcessInstance target = process(2, "Slave", receive("Slave#main:0"), receive("Slave#main:1"));

		// main:1 is only ever fed by process 3 in the real topology - senderA (1) is not allowed.
		Map<String, List<Integer>> sources = Map.of("2@Slave#main:1", List.of(3));
		List<RequiredEdge> edges = RequiredElementsGenerator.generate(List.of(senderA, target),
				topology(List.of(new RoleLink("Slave", "Slave")), Map.of("1@Slave#main:0", 2), sources, Map.of()));

		assertEquals(Set.of("Slave#main:0@p1 -> Slave#main:0@p2"), asStrings(edges));
	}

	@Test
	void fixedMessageSourcesEmptyListExcludesTheEdgeEntirely() {
		// The "empty array" trick used for gcd-lcm-both: a receive point that's
		// structurally never fed by anyone must be excludable, not just narrowed.
		ProcessInstance sender = process(0, "Master", send("Master#main:0"));
		ProcessInstance receiver = process(1, "Slave", receive("Slave#main:0"));

		List<RequiredEdge> edges = RequiredElementsGenerator.generate(List.of(sender, receiver),
				topology(List.of(new RoleLink("Master", "Slave")), Map.of(), Map.of("Slave#main:0", List.of()),
						Map.of()));

		assertTrue(edges.isEmpty(), "an empty allowed-sources list must exclude the edge, not allow everyone: " + edges);
	}

	@Test
	void fixedMessageSourcesNarrowsToTheAllowedSendersOnly() {
		ProcessInstance senderA = process(0, "Peer", send("Peer#main:0"));
		ProcessInstance senderB = process(1, "Peer", send("Peer#main:0"));
		ProcessInstance receiver = process(2, "Coordinator", receive("Coordinator#main:0"));

		List<RequiredEdge> edges = RequiredElementsGenerator.generate(List.of(senderA, senderB, receiver),
				topology(List.of(new RoleLink("Peer", "Coordinator")), Map.of(),
						Map.of("Coordinator#main:0", List.of(0)), Map.of()));

		assertEquals(Set.of("Peer#main:0@p0 -> Coordinator#main:0@p2"), asStrings(edges));
	}

	@Test
	void releaseAcquireEdgesPairEveryReleaseWithEveryAcquireByDefault() {
		SyncPoint release = new SyncPoint("P#run:0", Kind.SEM_RELEASE, "P");
		SyncPoint acquire = new SyncPoint("P#run:1", Kind.SEM_ACQUIRE, "P");
		ProcessInstance process = process(0, "Slave", release, acquire);

		List<RequiredEdge> edges = RequiredElementsGenerator.generate(List.of(process),
				topology(List.of(), Map.of(), Map.of(), Map.of()));

		assertEquals(Set.of("P#run:0@p0 -> P#run:1@p0"), asStrings(edges));
	}

	@Test
	void identityGroupsPreventCrossPairingBetweenTwoUnrelatedSemaphores() {
		// Same shape as 003_token_ring_file's over-generation finding: two
		// mutually-exclusive operation branches (op0's release/acquire, op1's)
		// share the identityGroups map but must never cross-pair.
		SyncPoint releaseOp0 = new SyncPoint("main:0", Kind.SEM_RELEASE, "Slave");
		SyncPoint acquireOp0 = new SyncPoint("run:0", Kind.SEM_ACQUIRE, "Producer");
		SyncPoint releaseOp1 = new SyncPoint("run:1", Kind.SEM_RELEASE, "Producer");
		SyncPoint acquireOp1 = new SyncPoint("run:2", Kind.SEM_ACQUIRE, "Producer");
		ProcessInstance process = process(1, "Slave", releaseOp0, acquireOp0, releaseOp1, acquireOp1);

		Map<String, String> groups = Map.of("main:0", "op0", "run:0", "op0", "run:1", "op1", "run:2", "op1");
		List<RequiredEdge> edges = RequiredElementsGenerator.generate(List.of(process),
				topology(List.of(), Map.of(), Map.of(), groups));

		assertEquals(Set.of("main:0@p1 -> run:0@p1", "run:1@p1 -> run:2@p1"), asStrings(edges),
				"op0's release must never pair with op1's acquire, or vice versa");
	}

	@Test
	void barrierPointsProduceEveryDistinctPairExactlyOnce() {
		SyncPoint b0 = new SyncPoint("B0", Kind.BARRIER, "Fixture");
		SyncPoint b1 = new SyncPoint("B1", Kind.BARRIER, "Fixture");
		SyncPoint b2 = new SyncPoint("B2", Kind.BARRIER, "Fixture");
		ProcessInstance process = process(0, "Worker", b0, b1, b2);

		List<RequiredEdge> edges = RequiredElementsGenerator.generate(List.of(process),
				topology(List.of(), Map.of(), Map.of(), Map.of()));

		assertEquals(Set.of("B0@p0 -> B1@p0", "B0@p0 -> B2@p0", "B1@p0 -> B2@p0"), asStrings(edges));
	}

	@Test
	void barrierGroupsSeparateTwoUnrelatedBarrierInstances() {
		SyncPoint barrier1A = new SyncPoint("barrier1#0", Kind.BARRIER, "Fixture");
		SyncPoint barrier1B = new SyncPoint("barrier1#1", Kind.BARRIER, "Fixture");
		SyncPoint barrier2A = new SyncPoint("barrier2#0", Kind.BARRIER, "Fixture");
		SyncPoint barrier2B = new SyncPoint("barrier2#1", Kind.BARRIER, "Fixture");
		ProcessInstance process = process(0, "Worker", barrier1A, barrier1B, barrier2A, barrier2B);

		Map<String, String> groups = Map.of("barrier1#0", "g1", "barrier1#1", "g1", "barrier2#0", "g2", "barrier2#1",
				"g2");
		List<RequiredEdge> edges = RequiredElementsGenerator.generate(List.of(process),
				topology(List.of(), Map.of(), Map.of(), groups));

		assertEquals(Set.of("barrier1#0@p0 -> barrier1#1@p0", "barrier2#0@p0 -> barrier2#1@p0"), asStrings(edges));
	}
}
