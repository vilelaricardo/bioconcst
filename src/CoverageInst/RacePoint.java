package CoverageInst;

import java.util.List;

/**
 * One ambiguous receive point: a destination process that legitimately
 * receives MESSAGE-kind required edges from more than one sender, so which
 * physical sender's packet actually lands first is a genuine OS/network
 * race - see ReplaySchedule's own javadoc for why this is the one real
 * source of non-determinism these benchmarks have.
 *
 * Scoped per destinationProcessId, not per individual receiverEdgeId,
 * because that's the granularity ReplaySchedule/CoverageTracer actually
 * control at - a destination's whole sender-arrival order is one turn-file
 * protocol, not one per receive call site (see RacePoints.detect).
 */
public final class RacePoint {

	public final int destinationProcessId;
	public final List<Integer> candidateSenderIds;
	public final List<RequiredEdge> relatedEdges;

	public RacePoint(int destinationProcessId, List<Integer> candidateSenderIds, List<RequiredEdge> relatedEdges) {
		this.destinationProcessId = destinationProcessId;
		this.candidateSenderIds = candidateSenderIds;
		this.relatedEdges = relatedEdges;
	}
}
