package CoverageInst;

/** One recognized synchronization call site, discovered by ClassInstrumenter. */
public final class SyncPoint {

	public enum Kind {
		SEND, RECEIVE, SEM_RELEASE, SEM_ACQUIRE, THREAD_START, BARRIER
	}

	public final String edgeId;
	public final Kind kind;
	public final String className;

	public SyncPoint(String edgeId, Kind kind, String className) {
		this.edgeId = edgeId;
		this.kind = kind;
		this.className = className;
	}

	@Override
	public String toString() {
		return kind + " " + edgeId;
	}
}
