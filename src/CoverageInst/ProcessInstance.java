package CoverageInst;

import java.util.List;

/**
 * One concrete process in a benchmark run: its role (used to match
 * RoleLinks - e.g. "Peer"), and the union of sync points found across
 * every class that actually runs inside that process. A process is not
 * always one class: combined-handshake's Peer spawns an internal
 * WindowChecker thread in the SAME JVM, so a "Peer" process's semaphore
 * release lives in WindowChecker.class while the matching acquire lives
 * in Peer.class - both belong to the one process instance.
 */
public final class ProcessInstance {

	public final int processId;
	public final String role;
	public final List<SyncPoint> syncPoints;

	public ProcessInstance(int processId, String role, List<SyncPoint> syncPoints) {
		this.processId = processId;
		this.role = role;
		this.syncPoints = syncPoints;
	}
}
