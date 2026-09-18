package CoverageInst;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Generates the sender-arrival-order schedules to try, deterministically,
 * for one RacePoint - the "which order should we force" half of the
 * deterministic arrival-order sweep (see CoverageSweepMain for the "run it
 * and see what got covered" half).
 *
 * Rather than reason about which arrival position corresponds to which
 * specific required edge (receiverEdgeId carries no such guaranteed
 * correlation once more than two candidates are involved), this exhaustively
 * tries every K! permutation of a RacePoint's candidateSenderIds (or, when K!
 * exceeds permutationCap, a random sample of distinct permutations) and lets
 * the caller observe empirically which required edges each one covers.
 *
 * Produces plain strings in the exact single-destination
 * "destProcessId sender1,sender2" grammar CoverageInst.ReplaySchedule already
 * parses (ReplaySchedule.fromInlineString) - this class never touches
 * ReplaySchedule's own API, matching how BioConcST.RaceGeneSupport already
 * builds the same grammar independently.
 */
public final class RaceScheduleSweep {

	private RaceScheduleSweep() {
	}

	public static List<String> candidateSchedules(RacePoint racePoint, int permutationCap) {
		return candidateSchedules(racePoint, permutationCap, new Random(0));
	}

	public static List<String> candidateSchedules(RacePoint racePoint, int permutationCap, Random random) {
		List<Integer> candidates = racePoint.candidateSenderIds;
		long totalPermutations = factorial(candidates.size());

		List<List<Integer>> orders = totalPermutations <= permutationCap ? allPermutations(candidates)
				: sampleDistinctPermutations(candidates, permutationCap, random);

		List<String> schedules = new ArrayList<>();
		for (List<Integer> order : orders) {
			schedules.add(toInlineLine(racePoint.destinationProcessId, order));
		}
		return schedules;
	}

	/**
	 * True when candidateSchedules(...) for this many candidates enumerates
	 * every possible arrival order (K! &lt;= permutationCap), false when it
	 * falls back to a random sample of distinct permutations instead - a
	 * caller reporting results needs to say which happened, since a sampled
	 * sweep finding nothing is much weaker evidence than an exhaustive one.
	 */
	public static boolean isExhaustive(int candidateCount, int permutationCap) {
		return factorial(candidateCount) <= permutationCap;
	}

	private static String toInlineLine(int destinationProcessId, List<Integer> order) {
		List<String> parts = order.stream().map(String::valueOf).toList();
		return destinationProcessId + " " + String.join(",", parts);
	}

	// Guards against a pathological benchmark with a huge candidate count -
	// none of today's benchmarks exceed K=4 (Raft/2PC), but an unbounded
	// factorial would otherwise overflow long before ever comparing against
	// permutationCap.
	private static long factorial(int n) {
		long result = 1;
		for (int i = 2; i <= n && result <= 1_000_000_000L; i++) {
			result *= i;
		}
		return result;
	}

	private static List<List<Integer>> allPermutations(List<Integer> items) {
		List<List<Integer>> result = new ArrayList<>();
		permute(new ArrayList<>(items), 0, result);
		return result;
	}

	private static void permute(List<Integer> items, int k, List<List<Integer>> result) {
		if (k == items.size()) {
			result.add(new ArrayList<>(items));
			return;
		}
		for (int i = k; i < items.size(); i++) {
			Collections.swap(items, k, i);
			permute(items, k + 1, result);
			Collections.swap(items, k, i);
		}
	}

	private static List<List<Integer>> sampleDistinctPermutations(List<Integer> items, int count, Random random) {
		Set<List<Integer>> seen = new LinkedHashSet<>();
		List<Integer> shuffled = new ArrayList<>(items);
		int maxAttempts = count * 50 + 1000;
		for (int attempts = 0; seen.size() < count && attempts < maxAttempts; attempts++) {
			Collections.shuffle(shuffled, random);
			seen.add(new ArrayList<>(shuffled));
		}
		return new ArrayList<>(seen);
	}
}
