package CoverageInst;

import java.util.List;
import java.util.Map;

/**
 * A benchmark's declared communication topology, used by
 * RequiredElementsGenerator to build a tight required-edges set instead of
 * ValiPar's blind "everything can pair with everything" default.
 *
 * messageLinks is the coarse, role-level declaration ("a Peer can reach a
 * Coordinator") - the right granularity when a sender's destination
 * genuinely varies across instances of the same role (e.g. either Peer may
 * end up talking to the one Coordinator). fixedMessageTargets narrows a
 * SPECIFIC send call site down to the one processId it's actually pinned
 * to when the benchmark's own code fixes it (e.g. Coordinator's
 * celebratePacket1 always targets whichever process registered as
 * remoteIP1) - present only for edges where that's true; absent edges
 * fall back to the broad role-level pairing.
 *
 * fixedMessageSources is the mirror image of fixedMessageTargets, needed
 * when it's the RECEIVE side (not the send side) that's ambiguous by role
 * alone: several process instances of the same role share one identical
 * send call site (e.g. three GcdSlave instances all sending back through
 * the exact same bytecode in a shared helper method - one edge id, so
 * fixedMessageTargets can't tell them apart), while the receiver has
 * MULTIPLE distinct receive call sites that are each only really reachable
 * from a SUBSET of senders (e.g. GcdMaster's one shared loop-receive is
 * only ever fed by slaves 1-2, never 3, which it messages separately and
 * later). Maps a receive edge id to the set of senderProcessIds actually
 * allowed to pair with it; a receive edge absent from this map keeps
 * pairing with every sender the role-level link allows, same as today.
 *
 * Both maps' keys may be INSTANCE-SCOPED ("<processId>@<edgeId>", checked
 * first) or left unscoped ("<edgeId>" alone, applying uniformly to every
 * instance of that call site - the common case, and the only form used
 * above). Instance scoping is what a ring/chain topology needs: N processes
 * of the SAME class share one edge id per call site, but each instance's
 * real neighbor(s) differ (process p's are (p±1) mod N) - e.g.
 * "1@TokenRingSlave#main:0": [0, 2] pins slave 1's receive to only its own
 * two neighbors, while slave 2 gets its own separate entry
 * "2@TokenRingSlave#main:0": [1, 3]. This isn't a rare exception: ValiPar's
 * own PCFGs were already keyed per (process, thread), never per class, for
 * exactly this reason (see ConcurrentTesting.Graph's own (process, thread)
 * constructor params) - ring/chain-shaped code is common enough that
 * per-instance scoping is a first-class capability here, not a bolt-on.
 *
 * identityGroups is the one generic mechanism for every same-process,
 * shared-object primitive (Semaphore/Lock/Condition release-acquire pairs,
 * CyclicBarrier's symmetric all-to-all rendezvous, and whatever future
 * primitive shares this shape) - NOT a bespoke concept per primitive type.
 * It maps a sync point's edge id to an arbitrary group name. Two points in
 * the same process are only paired if neither declares a group (the
 * default - correct whenever a process only has ONE object of that kind,
 * which is the common case and needs zero configuration) OR both declare
 * the SAME group (needed when a process has multiple distinct objects of
 * the same kind that must not be cross-paired, e.g. two unrelated
 * CyclicBarrier instances, or two unrelated Semaphore fields).
 */
public final class Topology {

	public final List<RoleLink> messageLinks;
	public final Map<String, Integer> fixedMessageTargets;
	public final Map<String, List<Integer>> fixedMessageSources;
	public final Map<String, String> identityGroups;

	public Topology(List<RoleLink> messageLinks, Map<String, Integer> fixedMessageTargets,
			Map<String, List<Integer>> fixedMessageSources, Map<String, String> identityGroups) {
		this.messageLinks = messageLinks;
		this.fixedMessageTargets = fixedMessageTargets;
		this.fixedMessageSources = fixedMessageSources;
		this.identityGroups = identityGroups;
	}

	/** Whether two same-process points are eligible to pair - see class javadoc. */
	boolean groupsCompatible(String edgeIdA, String edgeIdB) {
		String groupA = identityGroups.get(edgeIdA);
		String groupB = identityGroups.get(edgeIdB);
		return groupA == null || groupB == null || groupA.equals(groupB);
	}
}
