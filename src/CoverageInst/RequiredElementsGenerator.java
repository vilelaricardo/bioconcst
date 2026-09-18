package CoverageInst;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CoverageInst's equivalent of ValiElem: turns discovered sync points into
 * the set of required edges - but topology-aware from the start, instead of
 * generating every syntactically-compatible pair and leaving infeasible
 * ones for the tester to discover later (ValiPar's conservative Rᵢᵖ, see
 * project_valipar_spurious_sync_edges memory).
 *
 * A message edge only exists between processes whose roles are linked by a
 * declared RoleLink (or, for a pinned send call site, only the one specific
 * processId it's known to target - see Topology).
 *
 * A same-process, shared-identity edge (Semaphore/Lock/Condition
 * release<->acquire, or CyclicBarrier's symmetric all-to-all rendezvous)
 * only exists between points inside the SAME process instance (these
 * primitives never cross processes), narrowed by Topology.identityGroups
 * when a process has more than one distinct object of that kind - one
 * generic mechanism, not a bespoke one per primitive type, so a future
 * primitive with this same shape needs no new code here.
 *
 * fixedMessageTargets/fixedMessageSources entries may be scoped to one
 * specific process instance (key "<processId>@<edgeId>", checked first) or
 * left unscoped (key "<edgeId>" alone, applying uniformly to every instance
 * of that call site) - see lookupScoped and Topology's own javadoc. Instance
 * scoping matters whenever several processes share one class (so their
 * call sites share one edge id) but each instance's real neighbors differ -
 * a ring/chain topology (each process only reachable from its own
 * predecessor/successor) is the common case, and it's exactly the shape
 * ValiPar's own PCFGs were already keyed per (process, thread) to handle,
 * not a rare exception to special-case reluctantly.
 */
public final class RequiredElementsGenerator {

	private RequiredElementsGenerator() {
	}

	private static <V> V lookupScoped(Map<String, V> map, int processId, String edgeId) {
		V scoped = map.get(processId + "@" + edgeId);
		return scoped != null ? scoped : map.get(edgeId);
	}

	public static List<RequiredEdge> generate(List<ProcessInstance> processes, Topology topology) {
		List<RequiredEdge> edges = new ArrayList<>();
		edges.addAll(generateMessageEdges(processes, topology));
		edges.addAll(generateReleaseAcquireEdges(processes, topology));
		edges.addAll(generateSymmetricEdges(processes, topology));
		return edges;
	}

	private static List<RequiredEdge> generateMessageEdges(List<ProcessInstance> processes, Topology topology) {
		List<RequiredEdge> edges = new ArrayList<>();
		for (RoleLink link : topology.messageLinks) {
			for (ProcessInstance sender : processes) {
				if (!sender.role.equals(link.from)) {
					continue;
				}
				for (SyncPoint sendPoint : sender.syncPoints) {
					if (sendPoint.kind != SyncPoint.Kind.SEND) {
						continue;
					}
					Integer pinnedTarget = lookupScoped(topology.fixedMessageTargets, sender.processId,
							sendPoint.edgeId);

					for (ProcessInstance receiver : processes) {
						if (!receiver.role.equals(link.to) || receiver.processId == sender.processId) {
							continue;
						}
						if (pinnedTarget != null && receiver.processId != pinnedTarget) {
							continue;
						}
						for (SyncPoint receivePoint : receiver.syncPoints) {
							if (receivePoint.kind != SyncPoint.Kind.RECEIVE) {
								continue;
							}
							List<Integer> allowedSources = lookupScoped(topology.fixedMessageSources,
									receiver.processId, receivePoint.edgeId);
							if (allowedSources != null && !allowedSources.contains(sender.processId)) {
								continue;
							}
							edges.add(new RequiredEdge(RequiredEdge.Kind.MESSAGE, sender.processId, sendPoint.edgeId,
									receiver.processId, receivePoint.edgeId));
						}
					}
				}
			}
		}
		return edges;
	}

	// Directional: every release-like point paired with every compatible
	// acquire-like point in the same process (Semaphore, ReentrantLock/Lock,
	// Condition all funnel into these two SyncPoint.Kinds already).
	private static List<RequiredEdge> generateReleaseAcquireEdges(List<ProcessInstance> processes,
			Topology topology) {
		List<RequiredEdge> edges = new ArrayList<>();
		for (ProcessInstance process : processes) {
			for (SyncPoint releasePoint : process.syncPoints) {
				if (releasePoint.kind != SyncPoint.Kind.SEM_RELEASE) {
					continue;
				}
				for (SyncPoint acquirePoint : process.syncPoints) {
					if (acquirePoint.kind != SyncPoint.Kind.SEM_ACQUIRE) {
						continue;
					}
					if (!topology.groupsCompatible(releasePoint.edgeId, acquirePoint.edgeId)) {
						continue;
					}
					edges.add(new RequiredEdge(RequiredEdge.Kind.IDENTITY, process.processId, releasePoint.edgeId,
							process.processId, acquirePoint.edgeId));
				}
			}
		}
		return edges;
	}

	// Symmetric: every distinct PAIR of BARRIER-kind points in the same
	// process (a rendezvous has no sender/receiver side) - same
	// identityGroups mechanism narrows it when a process has more than one
	// distinct barrier.
	private static List<RequiredEdge> generateSymmetricEdges(List<ProcessInstance> processes, Topology topology) {
		List<RequiredEdge> edges = new ArrayList<>();
		for (ProcessInstance process : processes) {
			List<SyncPoint> points = process.syncPoints.stream().filter(p -> p.kind == SyncPoint.Kind.BARRIER)
					.toList();
			for (int i = 0; i < points.size(); i++) {
				for (int j = i + 1; j < points.size(); j++) {
					if (!topology.groupsCompatible(points.get(i).edgeId, points.get(j).edgeId)) {
						continue;
					}
					edges.add(new RequiredEdge(RequiredEdge.Kind.IDENTITY, process.processId, points.get(i).edgeId,
							process.processId, points.get(j).edgeId));
				}
			}
		}
		return edges;
	}
}
