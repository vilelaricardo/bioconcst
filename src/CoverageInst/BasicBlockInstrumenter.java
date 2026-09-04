package CoverageInst;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

/**
 * Second ASM pass (tree API), run over bytecode ClassInstrumenter has
 * already instrumented for sync-point hooks. Computes each method's real
 * basic-block control-flow graph and injects a
 * CoverageTracer.atNode(blockId) hook at the start of every block, so
 * GraphDistance can compare a shortest-path-to-target against what a run
 * actually executed - the bytecode-derived equivalent of the PCFG Souza
 * et al. 2008 define (every statement is a node, not just send/receive)
 * and ConcurrentTesting.Graph/BreadthFirstSearch/DistanceElem already
 * implement for the ValiPar path.
 *
 * Why a separate pass instead of folding this into ClassInstrumenter:
 * ClassInstrumenter's streaming MethodVisitor already correctly injects
 * sync-point hooks, validated in production this session - resolving jump
 * labels to compute basic-block boundaries is far simpler and safer with
 * the tree API's ready-made label resolution than replicating it by hand in
 * a streaming visitor, and running it as its own pass means the already-
 * validated sync-point injection is never touched. This is safe because
 * pass 1's injected instructions (DUP/SWAP/INVOKESTATIC CoverageTracer.*)
 * never introduce a new jump or label, so block boundaries computed here on
 * the already-instrumented bytecode are identical to what they'd be on the
 * original - a block containing a sync point just has more instructions in
 * it. Sync points are re-identified via SyncPointMatcher (never re-injected
 * - pass 1 already did that) purely to record which block each
 * already-assigned edge id lives in; the class-wide counter here mirrors
 * ClassInstrumenter's own exactly (methods in declaration order,
 * instructions in bytecode order) so both passes' ids agree without either
 * one telling the other - SyncPointMatcher's predicates only ever match
 * CoverageInst's small recognized-primitive owner set, never the injected
 * calls to CoverageInst/CoverageTracer itself, so there's no risk of this
 * pass re-matching pass 1's own instrumentation as a new sync point.
 *
 * Interprocedural inlining (same-class calls only): when a method calls
 * another method of the SAME class that itself has a sync point (the only
 * case that can ever matter to GraphDistance - a method with no sync point
 * is never a search target), the caller's call-site block is linked to the
 * callee's entry block, and every one of the callee's own exit blocks is
 * linked back to the caller's continuation block - instead of leaving the
 * two methods' graphs disconnected. This is flow-insensitive by design: if
 * the callee has several call sites, its exit connects to ALL of their
 * continuations, not just whichever one actually ran - a safe
 * over-approximation (never under-states reachability), not a precise
 * call-stack-aware analysis, which the benchmarks validated so far don't
 * need. All call-connected methods in a class are grouped into one
 * component and share one canonical entry block (preferring a member named
 * main([Ljava/lang/String;)V or run()V, matching how the JVM/a Thread
 * actually starts; else whichever member is never itself a local callee;
 * else the first declared) so a distance search anchored on a callee's own
 * edge still measures from the process's real starting point, not the
 * callee's own trivial local entry. Calls across different classes are not
 * followed - no benchmark validated so far needs that; revisit if one does.
 * Exception-handler edges (a try/catch's handler isn't linked in) remain
 * out of scope.
 */
public final class BasicBlockInstrumenter {

	private static final String TRACER = "CoverageInst/CoverageTracer";

	public static final class ClassResult {
		public final byte[] bytes;
		/** Key: "className#methodName". */
		public final Map<String, ControlFlowGraph> graphs;
		/** Key: sync edge id (as assigned by ClassInstrumenter), value: containing block id. */
		public final Map<String, String> syncEdgeBlocks;
		/** Key: block id whose last instruction is a recognized numeric predicate - see BranchDistance. */
		public final Map<String, Integer> branchPredicates;

		ClassResult(byte[] bytes, Map<String, ControlFlowGraph> graphs, Map<String, String> syncEdgeBlocks,
				Map<String, Integer> branchPredicates) {
			this.bytes = bytes;
			this.graphs = graphs;
			this.syncEdgeBlocks = syncEdgeBlocks;
			this.branchPredicates = branchPredicates;
		}
	}

	/** A method's own blocks/successors, computed independently of any call it makes into another method. */
	private static final class MethodBlocks {
		final String entryBlockId;
		final Map<String, List<String>> successors;
		final List<String> exitBlocks;

		MethodBlocks(String entryBlockId, Map<String, List<String>> successors, List<String> exitBlocks) {
			this.entryBlockId = entryBlockId;
			this.successors = successors;
			this.exitBlocks = exitBlocks;
		}
	}

	/** A same-class call from callerBlockId (in callerMethodKey) into calleeMethodKey's entry, resuming at continuationBlockId. */
	private static final class CallSite {
		final String callerMethodKey;
		final String callerBlockId;
		final String calleeMethodKey;
		final String continuationBlockId;

		CallSite(String callerMethodKey, String callerBlockId, String calleeMethodKey, String continuationBlockId) {
			this.callerMethodKey = callerMethodKey;
			this.callerBlockId = callerBlockId;
			this.calleeMethodKey = calleeMethodKey;
			this.continuationBlockId = continuationBlockId;
		}
	}

	private BasicBlockInstrumenter() {
	}

	public static ClassResult instrument(File classFile, String className) throws IOException {
		byte[] classBytes;
		try (FileInputStream in = new FileInputStream(classFile)) {
			classBytes = in.readAllBytes();
		}

		ClassReader reader = new ClassReader(classBytes);
		ClassNode classNode = new ClassNode(Opcodes.ASM9);
		reader.accept(classNode, 0);

		// Which (name+desc) methods of this class are eligible interprocedural
		// inlining targets - i.e. have a sync point of their own - decided up
		// front so leader/block computation below can treat a call to one of
		// them as a block boundary. methodNodeByKey (first declaration wins on
		// a name clash) is only used later to recognize a canonical
		// main/run entry point per call-connected component.
		Set<String> qualifyingMethods = new HashSet<>();
		Map<String, MethodNode> methodNodeByKey = new LinkedHashMap<>();
		for (MethodNode m : classNode.methods) {
			methodNodeByKey.putIfAbsent(className + "#" + m.name, m);
			if (methodHasSyncPoint(m)) {
				qualifyingMethods.add(m.name + m.desc);
			}
		}

		Map<String, String> syncEdgeBlocks = new LinkedHashMap<>();
		Map<String, Integer> branchPredicates = new LinkedHashMap<>();
		Map<String, MethodBlocks> methodBlocksByKey = new LinkedHashMap<>();
		List<CallSite> callSites = new ArrayList<>();

		int counter = 0;
		for (MethodNode method : classNode.methods) {
			if (method.instructions == null || method.instructions.size() == 0) {
				continue;
			}
			counter = instrumentMethod(className, method, counter, qualifyingMethods, methodBlocksByKey, callSites,
					syncEdgeBlocks, branchPredicates);
		}

		Map<String, ControlFlowGraph> graphs = linkCallGraph(methodBlocksByKey, callSites, methodNodeByKey);

		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
		classNode.accept(writer);
		return new ClassResult(writer.toByteArray(), graphs, syncEdgeBlocks, branchPredicates);
	}

	private static boolean methodHasSyncPoint(MethodNode method) {
		if (method.instructions == null) {
			return false;
		}
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode min
					&& SyncPointMatcher.matchKind(min.getOpcode(), min.owner, min.name, min.desc) != null) {
				return true;
			}
		}
		return false;
	}

	private static boolean isLocalCall(MethodInsnNode min, String className, Set<String> qualifyingMethods) {
		return min.owner.equals(className) && qualifyingMethods.contains(min.name + min.desc);
	}

	private static int instrumentMethod(String className, MethodNode method, int counter,
			Set<String> qualifyingMethods, Map<String, MethodBlocks> methodBlocksByKey, List<CallSite> callSites,
			Map<String, String> syncEdgeBlocks, Map<String, Integer> branchPredicates) {
		InsnList insnList = method.instructions;
		AbstractInsnNode[] insns = insnList.toArray();
		int n = insns.length;

		// GraphDistance only ever looks up a graph by the method-key prefix
		// of an actual RequiredEdge's sync edge id, so a method with no sync
		// point of its own can never be queried - skip it entirely rather
		// than instrument it. This matters far beyond saved work: helper
		// methods with a busy-wait polling loop (e.g. HelperClass.waitPeers,
		// spinning until a peer's address file appears) would otherwise get
		// a NODE trace event injected into that loop's own body, generating
		// tens of thousands of trace lines per run purely from polling
		// timing - real overhead that can push otherwise-fine test cases
		// into the harness's execTimeLimitMs and get them wrongly scored as
		// failed, which is a correctness problem for the search, not just a
		// performance one.
		if (!methodHasSyncPoint(method)) {
			return counter;
		}

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
			} else if (insn instanceof MethodInsnNode min && isLocalCall(min, className, qualifyingMethods)) {
				// A call into another sync-point-bearing method of this same
				// class: flow doesn't fall straight through to the next
				// instruction here, it goes through the callee first - cut a
				// block boundary right after it, same as after a jump/switch,
				// so the call is always the last instruction of its block.
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
			return counter;
		}
		List<Integer> starts = new ArrayList<>(blockRealStarts);

		String methodKey = className + "#" + method.name;
		Map<Integer, String> blockIdByStart = new HashMap<>();
		for (int k = 0; k < starts.size(); k++) {
			blockIdByStart.put(starts.get(k), methodKey + ":B" + k);
		}

		// Re-identify sync points BEFORE injecting node hooks (so raw indices
		// still refer to the untouched instruction array) - map each edge id
		// (same numbering ClassInstrumenter itself used) to its block.
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

		// Successor graph, computed over the untouched instruction array -
		// done before hook insertion purely so "last real instruction of a
		// block" bookkeeping isn't complicated by instructions this method
		// itself is about to add. A block ending in a local call gets no
		// successor here - callSites records it instead, and linkCallGraph()
		// wires it once every method in the class has its own blocks.
		Map<String, List<String>> successors = new LinkedHashMap<>();
		Map<String, JumpInsnNode> branchInsnByBlock = new HashMap<>();
		List<String> exitBlocks = new ArrayList<>();
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
						// Recognized two-way numeric predicate - see
						// BranchDistance. succ is [taken, fallthrough] in
						// that exact order by construction (taken added
						// first, just above; fallthrough second, here).
						if (BranchDistance.isRecognized(jump.getOpcode())) {
							branchPredicates.put(blockId, jump.getOpcode());
							branchInsnByBlock.put(blockId, jump);
						}
					}
				} else if (last instanceof TableSwitchInsnNode tsi) {
					addSuccessor(succ, blockIdByStart, starts,
							resolveToRealStart(insns, labelIndex.get(tsi.dflt.getLabel())));
					for (LabelNode l : tsi.labels) {
						addSuccessor(succ, blockIdByStart, starts,
								resolveToRealStart(insns, labelIndex.get(l.getLabel())));
					}
				} else if (last instanceof LookupSwitchInsnNode lsi) {
					addSuccessor(succ, blockIdByStart, starts,
							resolveToRealStart(insns, labelIndex.get(lsi.dflt.getLabel())));
					for (LabelNode l : lsi.labels) {
						addSuccessor(succ, blockIdByStart, starts,
								resolveToRealStart(insns, labelIndex.get(l.getLabel())));
					}
				} else if (last instanceof MethodInsnNode min && isLocalCall(min, className, qualifyingMethods)) {
					if (k + 1 < starts.size()) {
						String continuationId = blockIdByStart.get(starts.get(k + 1));
						callSites.add(new CallSite(methodKey, blockId, className + "#" + min.name, continuationId));
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
		methodBlocksByKey.put(methodKey, new MethodBlocks(blockIdByStart.get(starts.get(0)), successors, exitBlocks));

		// Inject atNode(blockId) before each block's real first instruction,
		// last (after both scans above are done reading the original
		// instruction array) - iterate in reverse purely so the intent
		// ("insert before this specific original instruction") stays
		// obviously unambiguous even though insertBefore takes a node
		// reference, not an index.
		for (int k = starts.size() - 1; k >= 0; k--) {
			AbstractInsnNode anchor = insns[starts.get(k)];
			InsnList hook = new InsnList();
			hook.add(new LdcInsnNode(blockIdByStart.get(starts.get(k))));
			hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TRACER, "atNode", "(Ljava/lang/String;)V", false));
			insnList.insertBefore(anchor, hook);
		}

		// Capture the real operands of every recognized numeric predicate,
		// right before it runs - DUP/DUP2 so the original comparison still
		// consumes its own untouched operands, same discipline as every
		// other hook in this codebase (see ClassInstrumenter). Which
		// direction ended up taken doesn't need to be logged separately -
		// GraphDistance/BranchDistance derive that from the operands
		// themselves plus the statically-known opcode.
		for (Map.Entry<String, JumpInsnNode> entry : branchInsnByBlock.entrySet()) {
			JumpInsnNode jump = entry.getValue();
			InsnList hook = new InsnList();
			if (BranchDistance.isTwoOperand(jump.getOpcode())) {
				hook.add(new InsnNode(Opcodes.DUP2));
				hook.add(new LdcInsnNode(entry.getKey()));
				hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TRACER, "atBranch2", "(IILjava/lang/String;)V",
						false));
			} else {
				hook.add(new InsnNode(Opcodes.DUP));
				hook.add(new LdcInsnNode(entry.getKey()));
				hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TRACER, "atBranch1", "(ILjava/lang/String;)V",
						false));
			}
			insnList.insertBefore(jump, hook);
		}

		return counter;
	}

	/**
	 * Wires same-class call/return edges into each method's own successors
	 * map, groups call-connected methods into components (union-find over the
	 * undirected caller<->callee relation, so chains of several calls end up
	 * together, not just direct pairs), and gives every member of a
	 * component one shared, merged successors map plus one canonical entry
	 * block - so a shortest-path search anchored on ANY member's edge
	 * traverses the whole component, measured from the same real starting
	 * point regardless of which member the target edge happens to live in.
	 */
	private static Map<String, ControlFlowGraph> linkCallGraph(Map<String, MethodBlocks> methodBlocksByKey,
			List<CallSite> callSites, Map<String, MethodNode> methodNodeByKey) {
		for (CallSite cs : callSites) {
			MethodBlocks caller = methodBlocksByKey.get(cs.callerMethodKey);
			MethodBlocks callee = methodBlocksByKey.get(cs.calleeMethodKey);
			if (caller == null || callee == null) {
				continue;
			}
			caller.successors.get(cs.callerBlockId).add(callee.entryBlockId);
			for (String exitBlockId : callee.exitBlocks) {
				callee.successors.get(exitBlockId).add(cs.continuationBlockId);
			}
		}

		Map<String, String> parent = new HashMap<>();
		for (String key : methodBlocksByKey.keySet()) {
			parent.put(key, key);
		}
		Set<String> everCalled = new HashSet<>();
		for (CallSite cs : callSites) {
			if (methodBlocksByKey.containsKey(cs.callerMethodKey) && methodBlocksByKey.containsKey(cs.calleeMethodKey)) {
				union(parent, cs.callerMethodKey, cs.calleeMethodKey);
				everCalled.add(cs.calleeMethodKey);
			}
		}

		Map<String, List<String>> membersByRoot = new LinkedHashMap<>();
		for (String key : methodBlocksByKey.keySet()) {
			membersByRoot.computeIfAbsent(find(parent, key), r -> new ArrayList<>()).add(key);
		}

		Map<String, ControlFlowGraph> graphs = new LinkedHashMap<>();
		for (List<String> members : membersByRoot.values()) {
			String canonicalEntryMethod = pickCanonicalEntry(members, methodNodeByKey, everCalled);
			String canonicalEntryBlockId = methodBlocksByKey.get(canonicalEntryMethod).entryBlockId;

			Map<String, List<String>> merged = new LinkedHashMap<>();
			for (String member : members) {
				merged.putAll(methodBlocksByKey.get(member).successors);
			}
			for (String member : members) {
				graphs.put(member, new ControlFlowGraph(canonicalEntryBlockId, merged));
			}
		}
		return graphs;
	}

	private static String pickCanonicalEntry(List<String> members, Map<String, MethodNode> methodNodeByKey,
			Set<String> everCalled) {
		for (String member : members) {
			MethodNode m = methodNodeByKey.get(member);
			if (m != null && "main".equals(m.name) && "([Ljava/lang/String;)V".equals(m.desc)) {
				return member;
			}
		}
		for (String member : members) {
			MethodNode m = methodNodeByKey.get(member);
			if (m != null && "run".equals(m.name) && "()V".equals(m.desc)) {
				return member;
			}
		}
		for (String member : members) {
			if (!everCalled.contains(member)) {
				return member;
			}
		}
		return members.get(0);
	}

	private static String find(Map<String, String> parent, String key) {
		String root = key;
		while (!parent.get(root).equals(root)) {
			root = parent.get(root);
		}
		String cur = key;
		while (!cur.equals(root)) {
			String next = parent.get(cur);
			parent.put(cur, root);
			cur = next;
		}
		return root;
	}

	private static void union(Map<String, String> parent, String a, String b) {
		String ra = find(parent, a);
		String rb = find(parent, b);
		if (!ra.equals(rb)) {
			parent.put(ra, rb);
		}
	}

	private static boolean isPseudo(AbstractInsnNode insn) {
		return insn instanceof LabelNode || insn instanceof LineNumberNode || insn instanceof FrameNode;
	}

	private static void markLeader(boolean[] isLeaderRaw, int index) {
		if (index >= 0 && index < isLeaderRaw.length) {
			isLeaderRaw[index] = true;
		}
	}

	// Labels/line-numbers/frames carry no runtime instruction of their own -
	// walk forward to the first node that's an actual bytecode instruction.
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
