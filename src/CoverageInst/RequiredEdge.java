package CoverageInst;

/**
 * One required sync edge: a specific send-side point on a specific process
 * paired with a specific receive-side point on a specific process. Unlike
 * ValiPar's Es, this is generated only from role pairs the benchmark's
 * topology actually declares as able to communicate - a Peer-class process
 * never gets paired with another Peer-class process unless a link between
 * "Peer" and "Peer" is explicitly declared, which none of our benchmarks do.
 */
public final class RequiredEdge {

	public enum Kind {
		MESSAGE,
		// Covers every same-process, shared-object-identity pairing:
		// Semaphore/Lock/Condition release<->acquire AND CyclicBarrier's
		// symmetric all-to-all rendezvous - CoverageEvaluator checks both
		// the same way (do two named edges' logged events ever share a
		// System.identityHashCode correlation), so one Kind covers both;
		// "sender"/"receiver" just mean "the two named points", with no
		// directionality implied for a symmetric primitive like a barrier.
		IDENTITY
	}

	public final Kind kind;
	public final int senderProcessId;
	public final String senderEdgeId;
	public final int receiverProcessId;
	public final String receiverEdgeId;

	public RequiredEdge(Kind kind, int senderProcessId, String senderEdgeId, int receiverProcessId,
			String receiverEdgeId) {
		this.kind = kind;
		this.senderProcessId = senderProcessId;
		this.senderEdgeId = senderEdgeId;
		this.receiverProcessId = receiverProcessId;
		this.receiverEdgeId = receiverEdgeId;
	}

	@Override
	public String toString() {
		return senderEdgeId + "@p" + senderProcessId + " -> " + receiverEdgeId + "@p" + receiverProcessId;
	}
}
