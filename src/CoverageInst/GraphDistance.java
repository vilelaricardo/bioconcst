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
		return compute(edge, graphs, syncEdgeBlocks, branchPredicates, observedNodesByProcess,
				observedOperandsByProcess, Map.of(), Map.of());
	}

	/**
	 * Same as the 6-arg overload, plus two optional, purely additive
	 * inputs used only when the divergence block for an uncovered edge has
	 * no locally-recognized numeric predicate (e.g. a String#equals-based
	 * decision fed by a message payload from another process - the
	 * "cross-process flag problem", see causalDistance's own doc below).
	 * When causalDistance has no entry for the edge being computed, or
	 * observedSenderByReceiveEdge can't resolve a real sender for it, this
	 * is byte-for-byte identical to the 6-arg overload - existing callers
	 * and existing benchmarks are unaffected unless they opt in.
	 *
	 * @param observedSenderByReceiveEdge per receiving process, which
	 *        processId actually sent the packet observed at a given
	 *        receive edge in this execution - CoverageEvaluator.Result's
	 *        field of the same name, already resolved for free as part of
	 *        MESSAGE-edge coverage matching.
	 * @param causalDistance declares, for a sender edge id with no local
	 *        numeric predicate, which receiver edges' real senders should
	 *        instead lend their own sideDistance (recursively, for the
	 *        given targetSenderEdge) as this edge's divergence
	 *        contribution - the cross-process analogue of the classical
	 *        chaining approach (Ferguson & Korel, 1996). Declared in JSON
	 *        topology config, never inferred, matching this project's
	 *        established fixedMessageSources/identityGroups precedent.
	 *        Assumes each sender process only ever sends once per edge id
	 *        per test case (true of every benchmark this was built for) -
	 *        not a general reaching-definitions analysis.
	 */
	public static double compute(RequiredEdge edge, Map<String, ControlFlowGraph> graphs,
			Map<String, String> syncEdgeBlocks, Map<String, Integer> branchPredicates,
			Map<Integer, Set<String>> observedNodesByProcess,
			Map<Integer, Map<String, int[]>> observedOperandsByProcess,
			Map<Integer, Map<String, Integer>> observedSenderByReceiveEdge,
			Map<String, List<CausalSource>> causalDistance) {
		double sendDistance = sideDistance(edge.senderEdgeId, edge.senderProcessId, graphs, syncEdgeBlocks,
				branchPredicates, observedNodesByProcess, observedOperandsByProcess, observedSenderByReceiveEdge,
				causalDistance);
		double receiveDistance = sideDistance(edge.receiverEdgeId, edge.receiverProcessId, graphs, syncEdgeBlocks,
				branchPredicates, observedNodesByProcess, observedOperandsByProcess, observedSenderByReceiveEdge,
				causalDistance);
		return (sendDistance + receiveDistance) / 2.0;
	}

	/**
	 * One declared source a chained edge may borrow its divergence
	 * contribution from - see compute()'s javadoc. Plain class with public
	 * fields (not a record) so Jackson can bind it straight from JSON the
	 * same way every other *Spec/*Config class in this codebase already
	 * does (see RoleLinkSpec) - a record needs jackson-module-parameter-names
	 * (not on this project's classpath) to deserialize via its canonical
	 * constructor.
	 *
	 * Two mutually-exclusive shapes, picked by which fields are set:
	 * <ul>
	 * <li><b>Cross-process</b> ({@code viaReceiverEdge}/{@code
	 * targetSenderEdge} set): the original mechanism - borrow whichever
	 * process really sent to viaReceiverEdge's own sideDistance for
	 * targetSenderEdge, recursively.</li>
	 * <li><b>Local</b> ({@code localBlock}/{@code wantedTaken} set): for
	 * the SAME-process flag problem - a boolean already computed earlier
	 * in the same method (e.g. {@code inWindow = a>=lo && a<=hi}) is what
	 * actually gates the target, so the block that CONSUMES the flag
	 * (an IFEQ/IFNE with no numeric gradient of its own) has to point
	 * back at the block(s) that COMPUTED it - localBlock names one such
	 * block (found via a dry-run, e.g. PcfgVisualizer/a quick
	 * CoverageInstRun.branchPredicates() dump - block ids are internal
	 * and can shift if the class is recompiled differently, same caveat
	 * as every other edge-id-based config value in this project).
	 * wantedTaken is evaluated directly against that block's own last
	 * recorded operands via BranchDistance, with no path/missingness
	 * check at all (the block is known to run unconditionally, or is
	 * skipped by short-circuit - see the null-operands skip below).</li>
	 * </ul>
	 */
	public static final class CausalSource {
		public String viaReceiverEdge;
		public String targetSenderEdge;
		public String localBlock;
		public Boolean wantedTaken;

		public CausalSource() {
		}

		public CausalSource(String viaReceiverEdge, String targetSenderEdge) {
			this.viaReceiverEdge = viaReceiverEdge;
			this.targetSenderEdge = targetSenderEdge;
		}

		public CausalSource(String localBlock, boolean wantedTaken) {
			this.localBlock = localBlock;
			this.wantedTaken = wantedTaken;
		}
	}

	private static double sideDistance(String syncEdgeId, int processId, Map<String, ControlFlowGraph> graphs,
			Map<String, String> syncEdgeBlocks, Map<String, Integer> branchPredicates,
			Map<Integer, Set<String>> observedNodesByProcess,
			Map<Integer, Map<String, int[]>> observedOperandsByProcess,
			Map<Integer, Map<String, Integer>> observedSenderByReceiveEdge,
			Map<String, List<CausalSource>> causalDistance) {
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
		boolean resolvedByChaining = false;

		// causalDistance is an explicit, per-edge human declaration ("this
		// edge's own local predicate has no useful gradient, borrow this
		// other process's instead") - it takes priority over whatever a
		// LOCAL predicate lookup would find. This matters in practice, not
		// just in theory: a String#equals()-gated decision compiles to
		// IFEQ/IFNE testing a boolean already computed by .equals() itself
		// - branchPredicates DOES recognize IFEQ/IFNE (they're real
		// IFxx-against-zero opcodes), so without this priority the local
		// lookup below would "succeed" with a flag operand (always exactly
		// 0 or 1, no continuous gradient) and silently mask the very case
		// causalDistance exists to fix.
		List<CausalSource> sources = causalDistance.get(syncEdgeId);
		if (sources != null && !sources.isEmpty()) {
			double chainedSum = 0.0;
			boolean resolvedAny = false;
			for (CausalSource source : sources) {
				if (source.localBlock != null) {
					// Same-process flag problem: a boolean computed earlier
					// in this SAME method (e.g. inWindow = a>=lo && a<=hi)
					// gates the target, and the block that CONSUMES it has
					// no gradient of its own - borrow the block that
					// COMPUTED it instead. No path/missingness check: that
					// block either ran (short-circuit reached it) or it
					// didn't, decided purely by whether an operand pair was
					// actually recorded for it this execution.
					Integer opcode = branchPredicates.get(source.localBlock);
					int[] operands = observedOperandsByProcess.getOrDefault(processId, Map.of())
							.get(source.localBlock);
					if (opcode == null || operands == null) {
						// Short-circuit skipped this block entirely this
						// execution (e.g. the first half of an && already
						// failed) - nothing to borrow from, skip.
						continue;
					}
					chainedSum += BranchDistance.compute(opcode, operands[0], operands[1], source.wantedTaken);
					resolvedAny = true;
					continue;
				}
				Integer realSender = observedSenderByReceiveEdge.getOrDefault(processId, Map.of())
						.get(source.viaReceiverEdge);
				if (realSender == null) {
					// That receive hasn't happened (yet) in this execution -
					// nothing to borrow from, skip.
					continue;
				}
				chainedSum += sideDistance(source.targetSenderEdge, realSender, graphs, syncEdgeBlocks,
						branchPredicates, observedNodesByProcess, observedOperandsByProcess,
						observedSenderByReceiveEdge, causalDistance);
				resolvedAny = true;
			}
			if (resolvedAny) {
				divergenceContribution = chainedSum;
				resolvedByChaining = true;
			}
		}

		if (!resolvedByChaining && firstMissingIdx > 0) {
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
