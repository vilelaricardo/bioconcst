package CoverageInst;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects ambiguous receive points from an already-built required-edges
 * list - no new static analysis needed, this is purely a group-by over data
 * RequiredElementsGenerator.generate(...) already computes once per
 * benchmark, before the GA's Engine is built.
 */
public final class RacePoints {

	private RacePoints() {
	}

	/**
	 * Groups MESSAGE-kind edges by (receiverProcessId, receiverEdgeId),
	 * keeps groups with more than one distinct senderProcessId (genuinely
	 * ambiguous), then re-groups those by receiverProcessId alone: one
	 * RacePoint per destination, its candidateSenderIds the union of
	 * senders across all of that destination's ambiguous receive edges.
	 *
	 * RequiredEdge has no equals()/hashCode() (only toString()), so
	 * grouping here is done over field values via a hand-built key, not
	 * object identity or a Set/Map keyed on RequiredEdge itself.
	 */
	public static List<RacePoint> detect(List<RequiredEdge> required) {
		Map<String, List<RequiredEdge>> byReceiverEdge = new LinkedHashMap<>();
		for (RequiredEdge edge : required) {
			if (edge.kind != RequiredEdge.Kind.MESSAGE) {
				continue;
			}
			String key = edge.receiverProcessId + "#" + edge.receiverEdgeId;
			byReceiverEdge.computeIfAbsent(key, k -> new ArrayList<>()).add(edge);
		}

		Map<Integer, Set<Integer>> candidatesByDestination = new LinkedHashMap<>();
		Map<Integer, List<RequiredEdge>> relatedByDestination = new LinkedHashMap<>();
		for (List<RequiredEdge> group : byReceiverEdge.values()) {
			Set<Integer> senders = new LinkedHashSet<>();
			for (RequiredEdge edge : group) {
				senders.add(edge.senderProcessId);
			}
			if (senders.size() <= 1) {
				continue;
			}
			int destinationProcessId = group.get(0).receiverProcessId;
			candidatesByDestination.computeIfAbsent(destinationProcessId, k -> new LinkedHashSet<>()).addAll(senders);
			relatedByDestination.computeIfAbsent(destinationProcessId, k -> new ArrayList<>()).addAll(group);
		}

		List<RacePoint> racePoints = new ArrayList<>();
		for (Map.Entry<Integer, Set<Integer>> entry : candidatesByDestination.entrySet()) {
			int destinationProcessId = entry.getKey();
			racePoints.add(new RacePoint(destinationProcessId, List.copyOf(entry.getValue()),
					List.copyOf(relatedByDestination.get(destinationProcessId))));
		}
		return racePoints;
	}
}
