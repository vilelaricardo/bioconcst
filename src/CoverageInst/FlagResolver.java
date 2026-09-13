package CoverageInst;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Standalone static analysis that automatically discovers the causalDistance
 * "local" resolution (see GraphDistance.CausalSource's localBlock/
 * wantedTaken shape) that a human currently has to find by hand - reading a
 * block-graph dump and writing the result into a benchmark's config - and
 * declare manually (see the quorum-handshake case study: Peer#main:B0/B1
 * resolving the inWindow flag consumed by an IFEQ).
 *
 * This is a classic reaching-definitions backward analysis over the same
 * kind of basic-block CFG BasicBlockInstrumenter already builds - computed
 * independently here (not by calling into that class) to keep this a
 * read-only, offline analysis tool with zero risk to the validated
 * instrumentation pipeline. Block numbering is reproduced identically
 * (same leader-finding rules, same B0/B1/... assignment order), so a
 * localBlock id this class reports names the exact same block the real
 * pipeline would.
 *
 * Scope, deliberate: resolves the common compiled pattern for a locally
 * assigned boolean flag defined by a short-circuit conjunction of one or
 * more numeric comparisons - `boolean flag = (a >= b) && (c <= d);` and
 * the single-comparison case `boolean flag = (a >= b);` - later consumed by
 * a plain `if (flag) { ... }` (IFEQ/IFNE on an ILOAD). Disjunctions (||),
 * negated/mixed expressions, flags stored in fields, flags computed from a
 * method call, and flags redefined across a loop are all reported as
 * unresolved rather than guessed at: a correct "could not resolve" is safe
 * for the search (falls back to today's manual-config path), a wrong chain
 * would not be.
 */
public final class FlagResolver {

	public record LocalSource(String block, boolean wantedTaken) {
	}

	public record Resolution(String flagBlockId, List<LocalSource> sources) {
	}

	private FlagResolver() {
	}

	public static List<Resolution> resolve(File classFile) throws IOException {
		ClassNode classNode = new ClassNode();
		try (FileInputStream in = new FileInputStream(classFile)) {
			new ClassReader(in).accept(classNode, 0);
		}
		List<Resolution> results = new ArrayList<>();
		for (MethodNode method : classNode.methods) {
			results.addAll(resolveMethod(classNode.name, method));
		}
		return results;
	}

	private static List<Resolution> resolveMethod(String className, MethodNode method) {
		List<Resolution> out = new ArrayList<>();
		if (method.instructions == null || method.instructions.size() == 0) {
			return out;
		}
		AbstractInsnNode[] insns = method.instructions.toArray();
		int n = insns.length;

		Map<Label, Integer> labelIndex = new HashMap<>();
		for (int i = 0; i < n; i++) {
			if (insns[i] instanceof LabelNode ln) {
				labelIndex.put(ln.getLabel(), i);
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
			return out;
		}
		List<Integer> starts = new ArrayList<>(blockRealStarts);

		String methodKey = className + "#" + method.name;
		Map<Integer, String> blockIdByStart = new HashMap<>();
		for (int k = 0; k < starts.size(); k++) {
			blockIdByStart.put(starts.get(k), methodKey + ":B" + k);
		}

		Map<String, List<String>> successors = new LinkedHashMap<>();
		Map<String, Integer> branchOpcodeByBlock = new HashMap<>();
		Map<String, Integer> istoreFirstSlotByBlock = new HashMap<>();
		Map<String, Integer> constAtEndByBlock = new HashMap<>();
		Map<String, Integer> iloadSlotByBlock = new HashMap<>();

		for (int k = 0; k < starts.size(); k++) {
			int blockEndExclusive = k + 1 < starts.size() ? starts.get(k + 1) : n;
			int lastReal = lastRealInstructionIndex(insns, starts.get(k), blockEndExclusive);
			String blockId = blockIdByStart.get(starts.get(k));
			List<String> succ = new ArrayList<>();

			recordBlockFacts(insns, starts.get(k), blockEndExclusive, istoreFirstSlotByBlock, constAtEndByBlock,
					iloadSlotByBlock, blockId);

			if (lastReal != -1) {
				AbstractInsnNode last = insns[lastReal];
				if (last instanceof JumpInsnNode jump) {
					addSucc(succ, blockIdByStart, starts, resolveToRealStart(insns, labelIndex.get(jump.label.getLabel())));
					if (jump.getOpcode() != Opcodes.GOTO && blockEndExclusive < n) {
						addSucc(succ, blockIdByStart, starts, resolveToRealStart(insns, blockEndExclusive));
						if (BranchDistance.isRecognized(jump.getOpcode())) {
							branchOpcodeByBlock.put(blockId, jump.getOpcode());
						}
					}
				} else if (last instanceof TableSwitchInsnNode tsi) {
					addSucc(succ, blockIdByStart, starts, resolveToRealStart(insns, labelIndex.get(tsi.dflt.getLabel())));
					for (LabelNode l : tsi.labels) {
						addSucc(succ, blockIdByStart, starts, resolveToRealStart(insns, labelIndex.get(l.getLabel())));
					}
				} else if (last instanceof LookupSwitchInsnNode lsi) {
					addSucc(succ, blockIdByStart, starts, resolveToRealStart(insns, labelIndex.get(lsi.dflt.getLabel())));
					for (LabelNode l : lsi.labels) {
						addSucc(succ, blockIdByStart, starts, resolveToRealStart(insns, labelIndex.get(l.getLabel())));
					}
				} else {
					int op = last.getOpcode();
					boolean terminal = op == Opcodes.ATHROW || (op >= Opcodes.IRETURN && op <= Opcodes.RETURN);
					if (!terminal && blockEndExclusive < n) {
						addSucc(succ, blockIdByStart, starts, resolveToRealStart(insns, blockEndExclusive));
					}
				}
			}
			successors.put(blockId, succ);
		}

		Map<String, List<String>> predecessors = invert(successors);

		if (System.getenv("FLAGRESOLVER_DEBUG") != null) {
			System.err.println("=== " + methodKey + " ===");
			System.err.println("branchOpcodeByBlock: " + branchOpcodeByBlock);
			System.err.println("iloadSlotByBlock: " + iloadSlotByBlock);
			System.err.println("istoreFirstSlotByBlock: " + istoreFirstSlotByBlock);
			System.err.println("constAtEndByBlock: " + constAtEndByBlock);
			System.err.println("successors: " + successors);
			System.err.println("predecessors: " + predecessors);
		}

		for (Map.Entry<String, Integer> e : branchOpcodeByBlock.entrySet()) {
			String flagBlockId = e.getKey();
			int opcode = e.getValue();
			if (opcode != Opcodes.IFEQ && opcode != Opcodes.IFNE) {
				continue;
			}
			Integer slot = iloadSlotByBlock.get(flagBlockId);
			if (slot == null) {
				continue;
			}
			List<LocalSource> sources = resolveChain(slot, predecessors, successors, branchOpcodeByBlock,
					istoreFirstSlotByBlock, constAtEndByBlock);
			if (sources != null && !sources.isEmpty()) {
				out.add(new Resolution(flagBlockId, sources));
			}
		}
		return out;
	}

	/**
	 * Finds the block that stores TRUE (1) into {@code slot} - the ISTORE
	 * may sit at the start of a distinct successor block from the one that
	 * pushed the constant, since a jump target (a GOTO's destination, in
	 * particular) always starts a new block even with nothing branching in
	 * between. Once found, walks backward from the constant-pushing
	 * predecessor through the unique-predecessor, fallthrough-only chain
	 * that leads to it - exactly the shape a short-circuit `&&` chain of
	 * comparisons compiles to. Returns null if no store-true edge is
	 * found, or the chain isn't the simple pattern this class resolves.
	 */
	private static List<LocalSource> resolveChain(int slot, Map<String, List<String>> predecessors,
			Map<String, List<String>> successors, Map<String, Integer> branchOpcodeByBlock,
			Map<String, Integer> istoreFirstSlotByBlock, Map<String, Integer> constAtEndByBlock) {
		String anchor = findConstDefiningPredecessor(slot, 1, predecessors, istoreFirstSlotByBlock,
				constAtEndByBlock);
		if (anchor == null) {
			return null;
		}

		List<LocalSource> sources = new ArrayList<>();
		String current = anchor;
		while (true) {
			List<String> preds = predecessors.getOrDefault(current, List.of());
			if (preds.size() != 1) {
				break;
			}
			String pred = preds.get(0);
			Integer predOpcode = branchOpcodeByBlock.get(pred);
			List<String> predSucc = successors.get(pred);
			if (predOpcode == null) {
				// A plain pass-through block (e.g. just a GOTO) between two
				// predicates - not itself a source, skip through it.
				current = pred;
				continue;
			}
			if (predOpcode == Opcodes.IFEQ || predOpcode == Opcodes.IFNE) {
				// This is itself another boolean-flag test - e.g. a chained
				// vote1.equals("YES") && vote2.equals("YES") && ... , where
				// each comparison is a method call, not a numeric predicate.
				// IFEQ/IFNE is specifically the idiom javac emits for
				// testing a boolean's truth value (never for a genuine
				// single-operand numeric test - those use IFLT/IFGE/IFGT/
				// IFLE), so chaining to a block that ends in one would just
				// substitute one gradient-free flag for another. Stop here
				// instead of returning a source that looks resolved but
				// still carries no real information.
				break;
			}
			if (predSucc == null || predSucc.size() != 2 || !predSucc.get(1).equals(current)) {
				// successors[pred] = [taken, fallthrough] - only a
				// fallthrough edge into `current` matches the short-circuit
				// `&&` shape (taken means the chain exits early towards the
				// store-false side).
				break;
			}
			sources.add(new LocalSource(pred, false));
			current = pred;
		}
		return sources;
	}

	/**
	 * Finds a block S whose first real instruction is `ISTORE slot`, and
	 * among S's predecessors, one P whose last real instruction leaves
	 * {@code wantedConst} on the stack (i.e. the P -&gt; S edge is exactly
	 * "slot := wantedConst"). Returns P (the constant-pushing block, the
	 * point to continue the backward chain walk from), not S itself.
	 */
	private static String findConstDefiningPredecessor(int slot, int wantedConst,
			Map<String, List<String>> predecessors, Map<String, Integer> istoreFirstSlotByBlock,
			Map<String, Integer> constAtEndByBlock) {
		for (Map.Entry<String, Integer> e : istoreFirstSlotByBlock.entrySet()) {
			if (e.getValue() != slot) {
				continue;
			}
			String storeBlock = e.getKey();
			for (String pred : predecessors.getOrDefault(storeBlock, List.of())) {
				Integer c = constAtEndByBlock.get(pred);
				if (c != null && c == wantedConst) {
					return pred;
				}
			}
		}
		return null;
	}

	/**
	 * Records, per block, the two facts resolveChain needs: the int
	 * constant (ICONST_0/1/M1, BIPUSH, SIPUSH) left on the stack if this
	 * block's own last real instruction is such a push (constAtEndByBlock);
	 * and the local variable slot this block does an ISTORE into, if that
	 * ISTORE is this block's first real instruction, i.e. nothing else in
	 * this block produced the value being stored (istoreFirstSlotByBlock -
	 * the constant then has to come from a predecessor's own
	 * constAtEndByBlock, handled by findConstDefiningPredecessor). Also
	 * records which slot (if any) is loaded immediately before this
	 * block's own trailing branch instruction (iloadSlotByBlock).
	 */
	private static void recordBlockFacts(AbstractInsnNode[] insns, int fromInclusive, int toExclusive,
			Map<String, Integer> istoreFirstSlotByBlock, Map<String, Integer> constAtEndByBlock,
			Map<String, Integer> iloadSlotByBlock, String blockId) {
		int firstRealIndex = -1;
		int lastRealIndex = -1;
		for (int i = fromInclusive; i < toExclusive; i++) {
			if (!isPseudo(insns[i])) {
				if (firstRealIndex == -1) {
					firstRealIndex = i;
				}
				lastRealIndex = i;
			}
		}
		if (firstRealIndex == -1) {
			return;
		}

		AbstractInsnNode first = insns[firstRealIndex];
		if (first instanceof VarInsnNode vin && vin.getOpcode() == Opcodes.ISTORE) {
			istoreFirstSlotByBlock.put(blockId, vin.var);
		}

		// An unconditional GOTO at the end of a block doesn't itself
		// consume the stack value a preceding const-push left there (it's
		// still there when control reaches the jump target) - so the
		// "value left on the stack when this block is exited" check needs
		// to look past a trailing GOTO to the instruction before it.
		int valueIndex = lastRealIndex;
		if (insns[valueIndex] instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.GOTO) {
			valueIndex = lastRealInstructionIndex(insns, fromInclusive, valueIndex);
		}
		if (valueIndex != -1) {
			Integer lastConst = asIntConstant(insns[valueIndex]);
			if (lastConst != null) {
				constAtEndByBlock.put(blockId, lastConst);
			}
		}

		for (int i = fromInclusive; i < toExclusive; i++) {
			AbstractInsnNode insn = insns[i];
			if (insn instanceof VarInsnNode vin && vin.getOpcode() == Opcodes.ILOAD) {
				iloadSlotByBlock.put(blockId, vin.var);
			}
		}
	}

	private static Integer asIntConstant(AbstractInsnNode insn) {
		int op = insn.getOpcode();
		if (insn instanceof InsnNode) {
			if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5) {
				return op - Opcodes.ICONST_0;
			}
			return null;
		}
		if (insn instanceof IntInsnNode iin && (op == Opcodes.BIPUSH || op == Opcodes.SIPUSH)) {
			return iin.operand;
		}
		return null;
	}

	private static Map<String, List<String>> invert(Map<String, List<String>> successors) {
		Map<String, List<String>> predecessors = new LinkedHashMap<>();
		for (String block : successors.keySet()) {
			predecessors.putIfAbsent(block, new ArrayList<>());
		}
		for (Map.Entry<String, List<String>> e : successors.entrySet()) {
			for (String succ : e.getValue()) {
				predecessors.computeIfAbsent(succ, k -> new ArrayList<>()).add(e.getKey());
			}
		}
		return predecessors;
	}

	private static boolean isPseudo(AbstractInsnNode insn) {
		return insn instanceof LabelNode || insn instanceof LineNumberNode || insn instanceof FrameNode;
	}

	private static void markLeader(boolean[] isLeaderRaw, Integer index) {
		if (index != null && index >= 0 && index < isLeaderRaw.length) {
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

	private static void addSucc(List<String> succ, Map<Integer, String> blockIdByStart, List<Integer> starts,
			int targetRealIndex) {
		if (targetRealIndex == -1) {
			return;
		}
		int idx = floorBlockIndex(starts, targetRealIndex);
		succ.add(blockIdByStart.get(starts.get(idx)));
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
}
