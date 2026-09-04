package CoverageInst;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CoverageInst's equivalent of ConcurrentTesting.DistanceElem: for one
 * uncovered RequiredEdge, computes a graded [0,1] distance per side (send,
 * receive) combining two classical search-based-testing components -
 * approach level (how much of the shortest path to the target block was
 * actually observed) and, at the exact point the real trace diverged from
 * that path, branch distance (how close the real operands were to taking
 * the wanted direction instead - see BranchDistance) when the diverging
 * block is a recognized numeric predicate. Nodes past the divergence point
 * still count at full weight (1 each), since how far they'd actually be
 * once that branch flips isn't knowable without re-running - only the
 * divergence step itself gets the finer-grained value. Falls back to the
 * original flat "1 per missing node" contribution wherever a predicate or
 * its operands aren't available (e.g. a String#equals-based branch, which
 * has no natural numeric gradient) - never a regression versus not having
 * branch distance at all.
 */
public final class GraphDistance {

	private GraphDistance() {
	}

	public static double compute(RequiredEdge edge, Map<String, ControlFlowGraph> graphs,
			Map<String, String> syncEdgeBlocks, Map<String, Integer> branchPredicates,
			Map<Integer, Set<String>> observedNodesByProcess,
			Map<Integer, Map<String, int[]>> observedOperandsByProcess) {
		double sendDistance = sideDistance(edge.senderEdgeId, edge.senderProcessId, graphs, syncEdgeBlocks,
				branchPredicates, observedNodesByProcess, observedOperandsByProcess);
		double receiveDistance = sideDistance(edge.receiverEdgeId, edge.receiverProcessId, graphs, syncEdgeBlocks,
				branchPredicates, observedNodesByProcess, observedOperandsByProcess);
		return (sendDistance + receiveDistance) / 2.0;
	}

	private static double sideDistance(String syncEdgeId, int processId, Map<String, ControlFlowGraph> graphs,
			Map<String, String> syncEdgeBlocks, Map<String, Integer> branchPredicates,
			Map<Integer, Set<String>> observedNodesByProcess,
			Map<Integer, Map<String, int[]>> observedOperandsByProcess) {
		String blockId = syncEdgeBlocks.get(syncEdgeId);
		if (blockId == null) {
			// Should never happen for a real declared edge id - fail soft
			// (max distance) instead of letting one unmapped id crash a
			// whole GA evaluation.
			return 1.0;
		}
		String methodKey = syncEdgeId.substring(0, syncEdgeId.lastIndexOf(':'));
		ControlFlowGraph graph = graphs.get(methodKey);
		if (graph == null) {
			return 1.0;
		}
		List<String> path = graph.shortestPathTo(blockId);
		if (path.isEmpty()) {
			return 1.0;
		}
		Set<String> observed = observedNodesByProcess.getOrDefault(processId, Set.of());

		int firstMissingIdx = -1;
		int missingCount = 0;
		for (int i = 0; i < path.size(); i++) {
			if (!observed.contains(path.get(i))) {
				missingCount++;
				if (firstMissingIdx == -1) {
					firstMissingIdx = i;
				}
			}
		}
		if (missingCount == 0) {
			return 0.0;
		}

		double divergenceContribution = 1.0;
		if (firstMissingIdx > 0) {
			String divergedFrom = path.get(firstMissingIdx - 1);
			String wantedNext = path.get(firstMissingIdx);
			Integer opcode = branchPredicates.get(divergedFrom);
			int[] operands = observedOperandsByProcess.getOrDefault(processId, Map.of()).get(divergedFrom);
			if (opcode != null && operands != null) {
				boolean wantedTaken = wantedNext.equals(graph.takenSuccessor(divergedFrom));
				divergenceContribution = BranchDistance.compute(opcode, operands[0], operands[1], wantedTaken);
			}
		}

		return (missingCount - 1 + divergenceContribution) / path.size();
	}
}
