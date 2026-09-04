package CoverageInst;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

/**
 * Builds a PUBLICATION-oriented PCFG - deliberately separate from
 * {@link BasicBlockInstrumenter}'s search-optimized graph, and never
 * consumed by GraphDistance/CoverageInstFitnessFunction/CoverageInstStrategy
 * (those keep using the existing graph unchanged - this class only feeds
 * {@link PcfgVisualizer}'s standalone rendering).
 *
 * Two differences from the search graph, both requested explicitly so the
 * rendered diagram can go in a paper: (1) a call into another method of the
 * SAME class is followed regardless of whether the callee has a
 * synchronization point - GraphDistance never needs that (it only searches
 * for paths to sync points), but Souza et al.'s own PCFG definition treats
 * every statement of every reachable method as a node, sync or not; (2)
 * when a method is called from N different call sites, its body is
 * DUPLICATED N times - one independent copy per call site, each with its
 * own entry/exit wiring - instead of every call site sharing one node. A
 * shared node is a safe, flow-insensitive over-approximation that's fine
 * for a distance metric (see BasicBlockInstrumenter's own javadoc) but
 * reads as a tangle of crossing arrows in a diagram; a human drawing the
 * same program by hand would draw each call's path separately.
 *
 * Deliberately does NOT share code with BasicBlockInstrumenter even though
 * the per-method leader/successor computation is structurally similar:
 * that class is validated end-to-end by the real search pipeline, and
 * refactoring it to serve this purely-visual tool would risk that
 * validated path for no benefit to the search itself. SyncPointMatcher and
 * BranchDistance ARE reused (see their own javadoc - a sync edge's id
 * depends only on method-declaration order and bytecode order, never on
 * block granularity, so recomputing it here with the same algorithm
 * reproduces the exact same ids BasicBlockInstrumenter/ClassInstrumenter
 * already assigned).
 *
 * Scope, deliberate (same as BasicBlockInstrumenter's own interprocedural
 * support): only same-class calls are followed; a call whose target is
 * never re-entered along the current call path (simple recursion guard -
 * no current benchmark recurses, but this must not hang if one someday
 * does) is expanded, otherwise it's left as a dead end. A duplicated call
 * site can't tell WHICH copy a given real execution actually took (the
 * underlying bytecode has one physical instruction regardless of how many
 * source call sites reach it), so a synchronization edge's dashed overlay
 * line in {@link PcfgVisualizer} points at exactly one representative copy
 * (the first one this builder's deterministic expansion order reaches) -
 * not at all N of them. This keeps the diagram legible and honest about
 * what it can and can't distinguish, rather than either picking an
 * arbitrary "correct" copy or drawing N redundant lines to the same target.
 */
public final class AcademicCfgBuilder {

	private AcademicCfgBuilder() {
	}

	/** One method's own blocks, computed independently of any call it makes - the unit this builder duplicates per call site. */
	private static final class MethodTemplate {
		final String entryBlockId;
		final Map<String, List<String>> successors;
		final List<String> exitBlocks;
		final List<CallSite> callSites;
		final Map<String, Integer> branchPredicates;
		final Map<String, String> syncEdgeBlocks;

		MethodTemplate(String entryBlockId, Map<String, List<String>> successors, List<String> exitBlocks,
				List<CallSite> callSites, Map<String, Integer> branchPredicates, Map<String, String> syncEdgeBlocks) {
			this.entryBlockId = entryBlockId;
			this.successors = successors;
			this.exitBlocks = exitBlocks;
			this.callSites = callSites;
			this.branchPredicates = branchPredicates;
			this.syncEdgeBlocks = syncEdgeBlocks;
		}
	}

	private static final class CallSite {
		final String callerBlockId;
		final String calleeMethodKey;
		final String continuationBlockId;

		CallSite(String callerBlockId, String calleeMethodKey, String continuationBlockId) {
			this.callerBlockId = callerBlockId;
			this.calleeMethodKey = calleeMethodKey;
			this.continuationBlockId = continuationBlockId;
		}
	}

	private static final class Expansion {
		final String entryBlockId;
		final List<String> exitBlockIds;

		Expansion(String entryBlockId, List<String> exitBlockIds) {
			this.entryBlockId = entryBlockId;
			this.exitBlockIds = exitBlockIds;
		}
	}

	public static BasicBlockInstrumenter.ClassResult build(File classFile, String className) throws IOException {
		byte[] classBytes;
		try (FileInputStream in = new FileInputStream(classFile)) {
			classBytes = in.readAllBytes();
		}
		ClassReader reader = new ClassReader(classBytes);
		ClassNode classNode = new ClassNode(Opcodes.ASM9);
		reader.accept(classNode, 0);

		Map<String, MethodTemplate> templates = new LinkedHashMap<>();
		Map<String, MethodNode> methodNodeByKey = new LinkedHashMap<>();
		int counter = 0;
		for (MethodNode method : classNode.methods) {
			// <init>/<clinit> are never part of a process's real PCFG - a
			// class's implicit or trivial constructor runs (if at all) as a
			// side effect of `new`, not as reachable process behavior, and
			// showing it as an unreachable one-node island (nothing in
			// these benchmarks ever calls it explicitly) would be pure
			// noise in a diagram meant for publication.
			if ("<init>".equals(method.name) || "<clinit>".equals(method.name)) {
				continue;
			}
			methodNodeByKey.putIfAbsent(className + "#" + method.name, method);
			if (method.instructions == null || method.instructions.size() == 0) {
				continue;
			}
			MethodTemplate template = buildTemplate(className, method, counter);
			counter += template.syncEdgeBlocks.size();
			if (template.entryBlockId != null) {
				templates.putIfAbsent(className + "#" + method.name, template);
			}
		}

		Builder builder = new Builder(templates, methodNodeByKey);
		builder.run();
		return new BasicBlockInstrumenter.ClassResult(classBytes, builder.graphs, builder.syncEdgeBlocks,
				builder.branchPredicates);
	}

	/** Holds the mutable, cross-recursion state for one class's expansion - a fresh instance per {@link #build}. */
	private static final class Builder {
		final Map<String, MethodTemplate> templates;
		final Map<String, MethodNode> methodNodeByKey;
		final Map<String, ControlFlowGraph> graphs = new LinkedHashMap<>();
		final Map<String, String> syncEdgeBlocks = new LinkedHashMap<>();
		final Map<String, Integer> branchPredicates = new LinkedHashMap<>();
		int callCounter = 0;
		// Reassigned once per top-level component (see expandComponent) -
		// NOT one map for the whole class. Souza (2008)'s Buffer_With_Lock_
		// Condition-shaped case is exactly why this matters: a class can
		// have several mutually-disconnected components (three separate
		// methods, none calling the others, each called externally by a
		// different thread) - sharing ONE map across all of them would make
		// every component's ControlFlowGraph.successors() return every
		// OTHER component's blocks too, and PcfgVisualizer would draw each
		// component's blocks once per component (since they're separate
		// object identities), producing exactly the kind of duplicate,
		// bolded-looking labels this class exists to avoid.
		Map<String, List<String>> merged;

		Builder(Map<String, MethodTemplate> templates, Map<String, MethodNode> methodNodeByKey) {
			this.templates = templates;
			this.methodNodeByKey = methodNodeByKey;
		}

		void run() {
			if (templates.isEmpty()) {
				return;
			}
			Set<String> everCalled = new HashSet<>();
			for (MethodTemplate template : templates.values()) {
				for (CallSite callSite : template.callSites) {
					everCalled.add(callSite.calleeMethodKey);
				}
			}
			// Primary component, from the class's own real entry point -
			// matching Souza's own PCFG definition, scoped to a process's
			// reachable behavior - expanded unconditionally.
			String canonicalEntry = pickCanonicalEntry(templates.keySet(), methodNodeByKey, everCalled);
			Set<String> visited = new LinkedHashSet<>();
			expandComponent(canonicalEntry, visited);
			// A class can still have OTHER methods with sync points of
			// their own that main()/run() never calls directly - e.g.
			// Buffer_With_Lock_Condition's setIncrementSharedValue/
			// setMultiplySharedValue/getSharedValue are three separate,
			// mutually-uncalled entry points that Producer/Consumer (a
			// DIFFERENT class) each call independently - so every
			// remaining unvisited method that has a sync point or a local
			// call of its own (i.e. isn't a trivial accessor like a plain
			// setter) becomes its own additional component below. Only a
			// method with neither - genuinely nothing sync-relevant
			// reachable from it - is left out, matching the noise this was
			// built to avoid in the first place.
			for (String key : new ArrayList<>(templates.keySet())) {
				if (visited.contains(key)) {
					continue;
				}
				MethodTemplate template = templates.get(key);
				if (template.syncEdgeBlocks.isEmpty() && template.callSites.isEmpty()) {
					continue;
				}
				expandComponent(key, visited);
			}
		}

		private void expandComponent(String rootMethodKey, Set<String> globalVisited) {
			merged = new LinkedHashMap<>();
			Set<String> visitedThisComponent = new LinkedHashSet<>();
			Expansion root = expand(rootMethodKey, "", new HashSet<>(), visitedThisComponent);
			if (root == null) {
				return;
			}
			ControlFlowGraph graph = new ControlFlowGraph(root.entryBlockId, merged);
			for (String key : visitedThisComponent) {
				graphs.putIfAbsent(key, graph);
			}
			globalVisited.addAll(visitedThisComponent);
		}

		private Expansion expand(String methodKey, String scope, Set<String> activeOnPath, Set<String> visitedThisRoot) {
			MethodTemplate template = templates.get(methodKey);
			if (template == null) {
				return null;
			}
			visitedThisRoot.add(methodKey);

			Map<String, String> rename = new HashMap<>();
			for (String templateBlockId : template.successors.keySet()) {
				rename.put(templateBlockId, templateBlockId + scope);
			}
			for (Map.Entry<String, List<String>> entry : template.successors.entrySet()) {
				List<String> renamed = new ArrayList<>();
				for (String target : entry.getValue()) {
					renamed.add(rename.get(target));
				}
				merged.put(rename.get(entry.getKey()), renamed);
			}
			for (Map.Entry<String, Integer> entry : template.branchPredicates.entrySet()) {
				branchPredicates.put(rename.get(entry.getKey()), entry.getValue());
			}
			for (Map.Entry<String, String> entry : template.syncEdgeBlocks.entrySet()) {
				syncEdgeBlocks.putIfAbsent(entry.getKey(), rename.get(entry.getValue()));
			}

			Set<String> nextActive = new HashSet<>(activeOnPath);
			nextActive.add(methodKey);
			for (CallSite callSite : template.callSites) {
				String callerBlockFinal = rename.get(callSite.callerBlockId);
				String continuationFinal = rename.get(callSite.continuationBlockId);
				if (activeOnPath.contains(callSite.calleeMethodKey) || !templates.containsKey(callSite.calleeMethodKey)) {
					// Recursion guard, or a call to a method with no sync
					// point AND no instructions of its own (shouldn't
					// happen for a real method, but never crash on it) -
					// the call site's block is left with no successor,
					// a documented dead end rather than a silent hang.
					continue;
				}
				String childScope = scope + "@c" + (callCounter++);
				Expansion child = expand(callSite.calleeMethodKey, childScope, nextActive, visitedThisRoot);
				if (child == null) {
					continue;
				}
				merged.get(callerBlockFinal).add(child.entryBlockId);
				for (String exitBlockId : child.exitBlockIds) {
					merged.get(exitBlockId).add(continuationFinal);
				}
			}

			List<String> exitBlocksFinal = new ArrayList<>();
			for (String templateExit : template.exitBlocks) {
				exitBlocksFinal.add(rename.get(templateExit));
			}
			return new Expansion(rename.get(template.entryBlockId), exitBlocksFinal);
		}
	}

	private static String pickCanonicalEntry(Set<String> methodKeys, Map<String, MethodNode> methodNodeByKey,
			Set<String> everCalled) {
		for (String key : methodKeys) {
			MethodNode m = methodNodeByKey.get(key);
			if (m != null && "main".equals(m.name) && "([Ljava/lang/String;)V".equals(m.desc)) {
				return key;
			}
		}
		for (String key : methodKeys) {
			MethodNode m = methodNodeByKey.get(key);
			if (m != null && "run".equals(m.name) && "()V".equals(m.desc)) {
				return key;
			}
		}
		for (String key : methodKeys) {
			if (!everCalled.contains(key)) {
				return key;
			}
		}
		return methodKeys.iterator().next();
	}

	// ---- Per-method template computation, structurally similar to
	// ---- BasicBlockInstrumenter's own but deliberately not shared - see
	// ---- this class's javadoc for why - and with a wider block-boundary
	// ---- trigger (any same-class call, not just sync-point-bearing ones).

	private static MethodTemplate buildTemplate(String className, MethodNode method, int counterStart) {
		InsnList insnList = method.instructions;
		AbstractInsnNode[] insns = insnList.toArray();
		int n = insns.length;

		Map<Label, Integer> labelIndex = new HashMap<>();
		for (int i = 0; i < n; i++) {
			if (insns[i] instanceof LabelNode labelNode) {
				labelIndex.put(labelNode.getLabel(), i);
			}
		}

		boolean[] isLeaderRaw = new boolean[n];
		if (n > 0) {
			isLeaderRaw[0] = true;
		}
		for (int i = 0; i < n; i++) {
			AbstractInsnNode insn = insns[i];
			if (insn instanceof JumpInsnNode jump) {
				markLeader(isLeaderRaw, labelIndex.get(jump.label.getLabel()));
				markLeader(isLeaderRaw, i + 1);
			} else if (insn instanceof TableSwitchInsnNode tsi) {
				markLeader(isLeaderRaw, labelIndex.get(tsi.dflt.getLabel()));
				for (LabelNode l : tsi.labels) {
					markLeader(isLeaderRaw, labelIndex.get(l.getLabel()));
				}
				markLeader(isLeaderRaw, i + 1);
			} else if (insn instanceof LookupSwitchInsnNode lsi) {
				markLeader(isLeaderRaw, labelIndex.get(lsi.dflt.getLabel()));
				for (LabelNode l : lsi.labels) {
					markLeader(isLeaderRaw, labelIndex.get(l.getLabel()));
				}
				markLeader(isLeaderRaw, i + 1);
			} else if (insn instanceof MethodInsnNode min && isSameClassCall(min, className)) {
				markLeader(isLeaderRaw, i + 1);
			} else {
				int op = insn.getOpcode();
				boolean terminal = op == Opcodes.ATHROW || (op >= Opcodes.IRETURN && op <= Opcodes.RETURN);
				if (terminal) {
					markLeader(isLeaderRaw, i + 1);
				}
			}
		}

		TreeSet<Integer> blockRealStarts = new TreeSet<>();
		for (int i = 0; i < n; i++) {
			if (isLeaderRaw[i]) {
				int real = resolveToRealStart(insns, i);
				if (real != -1) {
					blockRealStarts.add(real);
				}
			}
		}
		if (blockRealStarts.isEmpty()) {
			return new MethodTemplate(null, Map.of(), List.of(), List.of(), Map.of(), Map.of());
		}
		List<Integer> starts = new ArrayList<>(blockRealStarts);

		String methodKey = className + "#" + method.name;
		Map<Integer, String> blockIdByStart = new HashMap<>();
		for (int k = 0; k < starts.size(); k++) {
			blockIdByStart.put(starts.get(k), methodKey + ":B" + k);
		}

		Map<String, String> syncEdgeBlocks = new LinkedHashMap<>();
		int counter = counterStart;
		for (int i = 0; i < n; i++) {
			if (!(insns[i] instanceof MethodInsnNode min)) {
				continue;
			}
			SyncPoint.Kind kind = SyncPointMatcher.matchKind(min.getOpcode(), min.owner, min.name, min.desc);
			if (kind == null) {
				continue;
			}
			String edgeId = className + "#" + method.name + ":" + (counter++);
			int blockIdx = floorBlockIndex(starts, i);
			syncEdgeBlocks.put(edgeId, blockIdByStart.get(starts.get(blockIdx)));
		}

		Map<String, List<String>> successors = new LinkedHashMap<>();
		Map<String, Integer> branchPredicates = new LinkedHashMap<>();
		List<String> exitBlocks = new ArrayList<>();
		List<CallSite> callSites = new ArrayList<>();
		for (int k = 0; k < starts.size(); k++) {
			int blockEndExclusive = k + 1 < starts.size() ? starts.get(k + 1) : n;
			int lastReal = lastRealInstructionIndex(insns, starts.get(k), blockEndExclusive);
			String blockId = blockIdByStart.get(starts.get(k));
			List<String> succ = new ArrayList<>();
			if (lastReal != -1) {
				AbstractInsnNode last = insns[lastReal];
				if (last instanceof JumpInsnNode jump) {
					addSuccessor(succ, blockIdByStart, starts,
							resolveToRealStart(insns, labelIndex.get(jump.label.getLabel())));
					if (jump.getOpcode() != Opcodes.GOTO && blockEndExclusive < n) {
						addSuccessor(succ, blockIdByStart, starts, resolveToRealStart(insns, blockEndExclusive));
						if (BranchDistance.isRecognized(jump.getOpcode())) {
							branchPredicates.put(blockId, jump.getOpcode());
						}
					}
				} else if (last instanceof TableSwitchInsnNode tsi) {
					addSuccessor(succ, blockIdByStart, starts,
							resolveToRealStart(insns, labelIndex.get(tsi.dflt.getLabel())));
					for (LabelNode l : tsi.labels) {
						addSuccessor(succ, blockIdByStart, starts, resolveToRealStart(insns, labelIndex.get(l.getLabel())));
					}
				} else if (last instanceof LookupSwitchInsnNode lsi) {
					addSuccessor(succ, blockIdByStart, starts,
							resolveToRealStart(insns, labelIndex.get(lsi.dflt.getLabel())));
					for (LabelNode l : lsi.labels) {
						addSuccessor(succ, blockIdByStart, starts, resolveToRealStart(insns, labelIndex.get(l.getLabel())));
					}
				} else if (last instanceof MethodInsnNode min && isSameClassCall(min, className)) {
					if (k + 1 < starts.size()) {
						String continuationId = blockIdByStart.get(starts.get(k + 1));
						callSites.add(new CallSite(blockId, className + "#" + min.name, continuationId));
					}
				} else {
					int op = last.getOpcode();
					boolean terminal = op == Opcodes.ATHROW || (op >= Opcodes.IRETURN && op <= Opcodes.RETURN);
					if (terminal) {
						exitBlocks.add(blockId);
					} else if (blockEndExclusive < n) {
						addSuccessor(succ, blockIdByStart, starts, resolveToRealStart(insns, blockEndExclusive));
					}
				}
			}
			successors.put(blockId, succ);
		}

		return new MethodTemplate(blockIdByStart.get(starts.get(0)), successors, exitBlocks, callSites, branchPredicates,
				syncEdgeBlocks);
	}

	private static boolean isSameClassCall(MethodInsnNode min, String className) {
		return min.owner.equals(className);
	}

	private static boolean isPseudo(AbstractInsnNode insn) {
		return insn instanceof LabelNode || insn instanceof LineNumberNode || insn instanceof FrameNode;
	}

	private static void markLeader(boolean[] isLeaderRaw, int index) {
		if (index >= 0 && index < isLeaderRaw.length) {
			isLeaderRaw[index] = true;
		}
	}

	private static int resolveToRealStart(AbstractInsnNode[] insns, Integer index) {
		if (index == null || index < 0) {
			return -1;
		}
		int i = index;
		while (i < insns.length && isPseudo(insns[i])) {
			i++;
		}
		return i < insns.length ? i : -1;
	}

	private static int lastRealInstructionIndex(AbstractInsnNode[] insns, int fromInclusive, int toExclusive) {
		for (int i = toExclusive - 1; i >= fromInclusive; i--) {
			if (!isPseudo(insns[i])) {
				return i;
			}
		}
		return -1;
	}

	private static int floorBlockIndex(List<Integer> starts, int rawIndex) {
		int lo = 0;
		int hi = starts.size() - 1;
		int ans = 0;
		while (lo <= hi) {
			int mid = (lo + hi) / 2;
			if (starts.get(mid) <= rawIndex) {
				ans = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return ans;
	}

	private static void addSuccessor(List<String> succ, Map<Integer, String> blockIdByStart, List<Integer> starts,
			int targetRealIndex) {
		if (targetRealIndex == -1) {
			return;
		}
		int blockIdx = floorBlockIndex(starts, targetRealIndex);
		String id = blockIdByStart.get(starts.get(blockIdx));
		if (id != null && !succ.contains(id)) {
			succ.add(id);
		}
	}
}
