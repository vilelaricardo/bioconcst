package CoverageInst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * RaceScheduleSweep only ever needs a RacePoint's destinationProcessId and
 * candidateSenderIds, so these fixtures build RacePoint directly rather than
 * going through RequiredElementsGenerator/RacePoints.detect the way
 * RacePointsTest does - relatedEdges plays no role in permutation
 * generation and is left empty throughout.
 */
class RaceScheduleSweepTest {

	private static RacePoint racePoint(int destinationProcessId, Integer... candidateSenderIds) {
		return new RacePoint(destinationProcessId, List.of(candidateSenderIds), List.of());
	}

	private static Set<List<Integer>> ordersOf(List<String> schedules, int destinationProcessId) {
		return schedules.stream().map(line -> ReplaySchedule.fromInlineString(line).senderOrderFor(destinationProcessId))
				.collect(Collectors.toSet());
	}

	@Test
	void twoCandidatesProduceBothPermutationsExactly() {
		RacePoint racePoint = racePoint(0, 1, 2);
		List<String> schedules = RaceScheduleSweep.candidateSchedules(racePoint, 40);

		assertEquals(2, schedules.size());
		assertEquals(Set.of(List.of(1, 2), List.of(2, 1)), ordersOf(schedules, 0));
	}

	@Test
	void threeCandidatesProduceAllSixPermutations() {
		RacePoint racePoint = racePoint(0, 1, 2, 3);
		List<String> schedules = RaceScheduleSweep.candidateSchedules(racePoint, 40);

		assertEquals(6, schedules.size());
		assertEquals(6, ordersOf(schedules, 0).size(), "all six permutations must be distinct");
		for (List<Integer> order : ordersOf(schedules, 0)) {
			assertEquals(Set.of(1, 2, 3), Set.copyOf(order));
		}
	}

	@Test
	void fourCandidatesProduceAllTwentyFourPermutationsWithinDefaultCap() {
		RacePoint racePoint = racePoint(5, 10, 20, 30, 40);
		List<String> schedules = RaceScheduleSweep.candidateSchedules(racePoint, 40);

		assertEquals(24, schedules.size());
		assertEquals(24, ordersOf(schedules, 5).size());
	}

	@Test
	void fiveCandidatesExceedingTheCapAreSampledWithoutRepetition() {
		// 5! = 120 > cap, so this must fall back to sampling instead of
		// enumerating all of them.
		RacePoint racePoint = racePoint(0, 1, 2, 3, 4, 5);
		List<String> schedules = RaceScheduleSweep.candidateSchedules(racePoint, 40, new Random(42));

		assertEquals(40, schedules.size());
		Set<List<Integer>> orders = ordersOf(schedules, 0);
		assertEquals(40, orders.size(), "sampled permutations must all be distinct");
		for (List<Integer> order : orders) {
			assertEquals(Set.of(1, 2, 3, 4, 5), Set.copyOf(order), "every sample must be a permutation of all candidates");
		}
	}

	@Test
	void samplingIsDeterministicForAFixedSeed() {
		RacePoint racePoint = racePoint(0, 1, 2, 3, 4, 5);
		List<String> first = RaceScheduleSweep.candidateSchedules(racePoint, 40, new Random(7));
		List<String> second = RaceScheduleSweep.candidateSchedules(racePoint, 40, new Random(7));

		assertEquals(first, second, "the same seed must reproduce the exact same sampled schedules");
	}

	@Test
	void scheduleLinesLeaveOtherDestinationsUnscheduled() {
		RacePoint racePoint = racePoint(3, 1, 2);
		List<String> schedules = RaceScheduleSweep.candidateSchedules(racePoint, 40);

		for (String line : schedules) {
			ReplaySchedule parsed = ReplaySchedule.fromInlineString(line);
			assertTrue(parsed.senderOrderFor(3).size() == 2, "the targeted destination must carry the full order");
			assertTrue(parsed.senderOrderFor(99).isEmpty(),
					"an unrelated destination must be absent, so it runs unconstrained");
		}
	}
}
