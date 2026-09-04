package BioConcST;

import java.util.List;
import java.util.Map;

/**
 * Declares everything CoverageInst needs beyond what BenchmarkConfig already
 * has (testSetupProcesses, argumentRanges, path) to run strategy
 * "GA_COVINST" for a benchmark - JSON-driven like the rest of the pipeline,
 * so a future benchmark needs only a config block here, never a Java-code
 * change to CoverageInst itself (see CoverageInst.Topology's own javadoc
 * for what each field means; this is its JSON-config mirror).
 */
public class CoverageInstConfig {

	// Optional: which classes actually run inside a process playing a given
	// role, when it's more than just [role] itself - e.g. combined-handshake's
	// "Peer" role spans both Peer.class and the WindowChecker.class thread it
	// spawns. A role missing here defaults to a single class of that same name.
	public Map<String, List<String>> extraClassesByRole;

	public List<RoleLinkSpec> roleLinks;

	// senderEdgeId -> the one processId it's pinned to reach, for send call
	// sites whose destination the benchmark's own code fixes (not left to
	// the broad roleLinks pairing). Optional - defaults to none pinned.
	public Map<String, Integer> fixedMessageTargets;

	// receiverEdgeId -> the set of senderProcessIds actually allowed to pair
	// with it, for receive call sites that are only ever reachable from a
	// SUBSET of the role-level link's senders - needed when several process
	// instances of one role share an identical send call site (so
	// fixedMessageTargets can't tell them apart) but the receiver has
	// multiple distinct receive points each fed by only some of them (e.g.
	// GcdMaster's shared loop-receive is only ever from slaves 1-2, never 3).
	// Optional - defaults to none restricted (every sender the role-level
	// link allows keeps pairing with every receive point, as today).
	public Map<String, List<Integer>> fixedMessageSources;

	// edgeId -> arbitrary group name, for same-process shared-identity
	// primitives (Semaphore/Lock/Condition/CyclicBarrier) when a process has
	// more than one distinct object of that kind. Optional - defaults to
	// none grouped (every point of a compatible kind in the same process
	// pairs freely, correct whenever there's only one such object, which is
	// the common case).
	public Map<String, String> identityGroups;
}
