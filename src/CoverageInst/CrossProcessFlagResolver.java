package CoverageInst;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Automatically discovers the causalDistance CROSS-PROCESS resolution (see
 * GraphDistance.CausalSource's viaReceiverEdge/targetSenderEdge shape) that a
 * human currently has to find by hand, for the specific case where a p-use
 * (Rapps &amp; Weyuker 1985) at a receiver process is fed by a value that
 * crossed a process boundary as message content.
 *
 * Theoretical grounding, precise: this automates identification of an
 * INTER-M-P-USE ASSOCIATION as defined by Souza et al., "Data Flow Testing in
 * Concurrent Programs with Message Passing and Shared Memory Paradigms"
 * (Procedia Computer Science 18, 2013) - see sync-claude-codex.md
 * (2026-09-15/16 entries) for the full derivation and the exact mapping:
 * their m-use node (n2, the send) is our targetSenderEdge; their sync edge's
 * receive endpoint (n3) is our viaReceiverEdge; their p-use edge (n4,n5) is
 * the predicate we are trying to explain. We do NOT implement their
 * all-inter-message-uses COVERAGE CRITERION - our required elements remain
 * the existing sync-edge model (souza2008pcfg lineage); we reuse their
 * association-identification technique purely as a MECHANISM for deciding
 * where to redirect an existing required element's fitness distance.
 *
 * Deliberately conservative, matching FlagResolver's own stated philosophy:
 * a correct "unresolved" is safe (falls back to today's manual-config path);
 * a wrong chain would silently redirect gradient towards the WRONG sender
 * branch, which is worse than not resolving at all (see the
 * quorum-handshake-style ambiguity below). Scope, deliberate, for this first
 * version:
 * <ul>
 * <li>Only straight-line (no intervening branch) definition chains are
 * traced, both for the receiver's "does this predicate's operand come from a
 * receive?" question and the sender's "what literal did this candidate send
 * site actually transmit?" question - real reaching-definitions across
 * merges/loops is out of scope for v1 (see FlagResolver's own analogous
 * local-only scope note).</li>
 * <li>Only a String constant payload compared via String#equals is
 * recognized on the receiver side, and only a String constant fed through
 * String#getBytes() is recognized on the sender side - the same idiom this
 * project's own benchmarks use (quorum-handshake's QUORUM/NORMAL votes,
 * FlagChainStress's TRIGGER/SKIP).</li>
 * <li>Disambiguation between multiple structurally-compatible candidate
 * senders is done by literal payload matching only; anything else
 * (aliasing, reused buffers, non-inlined helpers, non-literal payloads) is
 * reported UNRESOLVED rather than guessed at.</li>
 * <li>"Does this predicate control reachability of the target sync edge?"
 * is approximated by requiring the sync-edge call site to be in the
 * straight-line fallthrough body of the predicate's own IFEQ jump (the
 * javac shape for {@code if (received.equals("X")) { send(); }}) with no
 * other incoming edge in between - true dominance analysis is future work;
 * negated IFNE bodies and anything more complex are UNRESOLVED.</li>
 * </ul>
 */
public final class CrossProcessFlagResolver {

	public enum Status {
		RESOLVED, AMBIGUOUS, UNRESOLVED
	}

	/**
	 * One candidate cross-process resolution for a required MESSAGE edge.
	 * When status is RESOLVED, viaReceiverEdge/targetSenderEdge/
	 * senderLocalSources are populated exactly as GraphDistance.CausalSource
	 * (the cross-process shape) plus FlagResolver.LocalSource (the sender's
	 * own local shape, if FlagResolver found one - may be empty if the
	 * sender's own value isn't itself flag-masked).
	 */
	public record Suggestion(String targetEdgeId, Status status, String reason, String viaReceiverEdge,
			String targetSenderEdge, List<FlagResolver.LocalSource> senderLocalSources) {

		static Suggestion unresolved(String targetEdgeId, String reason) {
			return new Suggestion(targetEdgeId, Status.UNRESOLVED, reason, null, null, List.of());
		}

		static Suggestion ambiguous(String targetEdgeId, String reason) {
			return new Suggestion(targetEdgeId, Status.AMBIGUOUS, reason, null, null, List.of());
		}

		static Suggestion resolved(String targetEdgeId, String viaReceiverEdge, String targetSenderEdge,
				List<FlagResolver.LocalSource> senderLocalSources) {
			return new Suggestion(targetEdgeId, Status.RESOLVED, null, viaReceiverEdge, targetSenderEdge,
					senderLocalSources);
		}
	}

	private CrossProcessFlagResolver() {
	}

	/**
	 * @param receiverClassFile   the compiled (not instrumented) .class file
	 *                            containing the candidate p-use
	 * @param receiverClassName   its internal (slash-separated) name, e.g.
	 *                            "Relay"
	 * @param senderClassFiles    every candidate sender class file a role
	 *                            link says could send to this receiver's
	 *                            role - e.g. {"Source": new File(...)}, keyed
	 *                            by internal class name
	 * @return one Suggestion per receiver-side sync edge whose reachability
	 *         appears gated by a message-derived p-use; edges with no such
	 *         pattern at all are simply absent (not reported as unresolved -
	 *         "no p-use of a received value" isn't a failure, it just means
	 *         this analysis has nothing to say about that edge).
	 */
	public static List<Suggestion> resolve(File receiverClassFile, String receiverClassName,
			Map<String, File> senderClassFiles) throws IOException {
		ClassNode receiverClass = readClass(receiverClassFile);
		Map<MethodNode, Map<Integer, String>> syncEdgeIdsByMethod = syncEdgeIdsByMethod(receiverClass,
				receiverClassName);
		List<Suggestion> out = new ArrayList<>();

		for (MethodNode method : receiverClass.methods) {
			out.addAll(resolveMethod(receiverClassName, method, syncEdgeIdsByMethod.getOrDefault(method, Map.of()),
					senderClassFiles));
		}
		return out;
	}

	private static List<Suggestion> resolveMethod(String receiverClassName, MethodNode method,
			Map<Integer, String> syncEdgeIdByInsnIndex, Map<String, File> senderClassFiles) throws IOException {
		List<Suggestion> out = new ArrayList<>();
		if (method.instructions == null || method.instructions.size() == 0) {
			return out;
		}
		AbstractInsnNode[] insns = method.instructions.toArray();

		// Pass 1: assign sync-edge ids in exactly the order/scheme
		// ClassInstrumenter.nextEdgeId does (a single counter per class,
		// incrementing per recognized call site in visitation order). We
		// first build a class-wide map from method instruction index to edge
		// id, then each method can reason locally while still using the same
		// ids the real instrumentation writes into traces.
		Map<Integer, Integer> receivePacketSlotByInsnIndex = new HashMap<>();
		Map<Integer, String> receiveEdgeIdByInsnIndex = new HashMap<>();
		for (int i = 0; i < insns.length; i++) {
			if (!(insns[i] instanceof MethodInsnNode call)) {
				continue;
			}
			SyncPoint.Kind kind = SyncPointMatcher.matchKind(call.getOpcode(), call.owner, call.name, call.desc);
			if (kind == null) {
				continue;
			}
			String edgeId = syncEdgeIdByInsnIndex.get(i);
			if (edgeId == null) {
				continue;
			}
			if (kind == SyncPoint.Kind.RECEIVE) {
				receiveEdgeIdByInsnIndex.put(i, edgeId);
				// DatagramSocket#receive(DatagramPacket) takes exactly one
				// reference argument - the packet whose contents it
				// populates - pushed by the ALOAD immediately before this
				// call (after the socket receiver ALOAD earlier on the
				// stack, which we don't need).
				if (i > 0 && insns[i - 1] instanceof VarInsnNode packetLoad
						&& packetLoad.getOpcode() == Opcodes.ALOAD) {
					receivePacketSlotByInsnIndex.put(i, packetLoad.var);
				}
			}
		}
		if (receiveEdgeIdByInsnIndex.isEmpty()) {
			return out;
		}

		// Pass 2: linear taint propagation - a local slot is "tainted" if
		// its value was, or transitively derives from, a receive() call's
		// packet argument. Deliberately linear/no-branch-aware (see class
		// javadoc scope note): correct for the straight-line
		// receive-then-decode-then-test shape this version targets, unsound
		// in general (a taint could be introduced or lost across a branch
		// this scan ignores) - acceptable because a wrong ANSWER here can
		// only make us look for a p-use pattern that isn't really there
		// (leading to fewer Suggestions, never a wrong one), not report a
		// wrong resolution.
		Set<Integer> taintedSlots = new HashSet<>();
		for (Map.Entry<Integer, Integer> e : receivePacketSlotByInsnIndex.entrySet()) {
			taintedSlots.add(e.getValue());
		}
		for (int i = 0; i < insns.length; i++) {
			if (insns[i] instanceof VarInsnNode store && store.getOpcode() == Opcodes.ASTORE) {
				if (definingChainReadsTaintedSlot(insns, i, taintedSlots)) {
					taintedSlots.add(store.var);
				}
			}
		}

			// Pass 3: find IFEQ whose tested boolean came from a
			// method call (INVOKEVIRTUAL/INVOKEINTERFACE/INVOKESPECIAL, boolean
			// return) invoked on a tainted receiver - this is our p-use of a
			// message-derived value. FlagResolver already handles the OTHER
		// shape (ILOAD of a previously-numeric-compared boolean) - this is
		// deliberately the complementary case it does not attempt (see
		// FlagResolver.resolveMethod's own iloadSlotByBlock requirement).
		for (int i = 0; i < insns.length; i++) {
			if (!(insns[i] instanceof JumpInsnNode jump)) {
				continue;
			}
				if (jump.getOpcode() != Opcodes.IFEQ) {
					continue;
				}
			if (!(insns[i - 1] instanceof MethodInsnNode predicateCall)) {
				continue;
			}
			if (!predicateCall.desc.endsWith(")Z")) {
				continue;
			}
			Integer receiverSlot = findReceiverSlotForInvoke(insns, i - 1);
			if (receiverSlot == null || !taintedSlots.contains(receiverSlot)) {
				continue;
			}

			// Which receive() call could this predicate's value have come
			// from? Conservatively: the closest preceding recognized
			// receive in program order (no branch-aware "which receive
			// actually dominates this point" reasoning yet).
			String viaReceiverEdge = closestPrecedingReceiveEdge(i, receiveEdgeIdByInsnIndex);
			if (viaReceiverEdge == null) {
				continue;
			}

			// Which sync edge does this predicate actually gate? MVP
			// dominance approximation (see class javadoc): only accept the
			// simple case where the very next recognized sync point after
			// this predicate's jump is the one being explained, i.e. the
			// predicate is immediately followed (mod the jump itself) by
			// the guarded call - this matches FlagChainStress's Relay
			// exactly (IFEQ then, in the fallthrough/taken block, the SEND
			// with nothing else in between) and is exactly the case we
			// refuse to over-generalize from per Codex's review.
			String targetEdgeId = findImmediatelyGuardedSyncEdge(insns, i, syncEdgeIdByInsnIndex);
			if (targetEdgeId == null) {
				out.add(Suggestion.unresolved(receiverClassName + "#" + method.name + ":predicate@" + i,
						"predicate does not immediately, unambiguously guard a single recognized sync edge - dominance not proven, refusing to guess"));
				continue;
			}

			// Literal the receiver-side predicate compares against, e.g.
			// "TRIGGER" in received.equals("TRIGGER") - used below purely
			// to disambiguate between multiple structurally-compatible
			// candidate senders, never to change WHICH edge we think is
			// gated.
			String comparedLiteral = findComparedStringLiteral(insns, i - 1);

			out.add(resolveSender(targetEdgeId, viaReceiverEdge, comparedLiteral, senderClassFiles));
		}
		return out;
	}

	private static Suggestion resolveSender(String targetEdgeId, String viaReceiverEdge, String comparedLiteral,
			Map<String, File> senderClassFiles) throws IOException {
		record Candidate(String className, String edgeId) {
		}
		List<Candidate> matchingLiteral = new ArrayList<>();
		List<Candidate> allCandidates = new ArrayList<>();

		for (Map.Entry<String, File> senderClass : senderClassFiles.entrySet()) {
			ClassNode senderNode = readClass(senderClass.getValue());
			Map<MethodNode, Map<Integer, String>> senderSyncIdsByMethod = syncEdgeIdsByMethod(senderNode,
					senderClass.getKey());
			for (MethodNode method : senderNode.methods) {
				if (method.instructions == null || method.instructions.size() == 0) {
					continue;
				}
				AbstractInsnNode[] insns = method.instructions.toArray();
				Map<Integer, String> syncEdgeIdByInsnIndex = senderSyncIdsByMethod.getOrDefault(method, Map.of());
				for (int i = 0; i < insns.length; i++) {
					if (!(insns[i] instanceof MethodInsnNode call)) {
						continue;
					}
					SyncPoint.Kind kind = SyncPointMatcher.matchKind(call.getOpcode(), call.owner, call.name,
							call.desc);
					if (kind == null) {
						continue;
					}
					String edgeId = syncEdgeIdByInsnIndex.get(i);
					if (edgeId == null) {
						continue;
					}
					if (kind != SyncPoint.Kind.SEND) {
						continue;
					}
					allCandidates.add(new Candidate(senderClass.getKey(), edgeId));
					String sentLiteral = findSentStringLiteral(insns, i);
					if (comparedLiteral != null && comparedLiteral.equals(sentLiteral)) {
						matchingLiteral.add(new Candidate(senderClass.getKey(), edgeId));
					}
				}
			}
		}

		List<Candidate> winners = !matchingLiteral.isEmpty() ? matchingLiteral : allCandidates;
		if (winners.isEmpty()) {
			return Suggestion.unresolved(targetEdgeId, "no candidate SEND call site found in any sender class");
		}
		if (winners.size() > 1) {
			return Suggestion.ambiguous(targetEdgeId,
					"multiple structurally-compatible sender edges and no distinguishing literal payload matched ("
							+ winners.size() + " candidates) - refusing to pick arbitrarily");
		}

		String targetSenderEdge = winners.get(0).edgeId();
		File senderFile = senderClassFiles.get(winners.get(0).className());
		// FlagResolver.flagBlockId names the FLAG-CONSUMING block (e.g.
		// "Source#main:B4"), a different numbering scheme than a sync
		// edge id (e.g. "Source#main:0") - there is no direct way from ids
		// alone to confirm this resolution is the one gating
		// targetSenderEdge specifically. MVP simplification: take this
		// sender class's first (typically only, per this suite's own
		// evaluation - see main.tex's automatic-inference table, 6
		// resolutions across 78 classes) resolution as the correlated one;
		// a class with more than one independently-resolvable flag would
		// need real correlation between the flag's own reachability and
		// targetSenderEdge's block, which is future work.
		List<FlagResolver.Resolution> senderLocal = FlagResolver.resolve(senderFile);
		List<FlagResolver.LocalSource> localSources = senderLocal.isEmpty() ? List.of()
				: senderLocal.get(0).sources();

		return Suggestion.resolved(targetEdgeId, viaReceiverEdge, targetSenderEdge, localSources);
	}

	// ---- small bytecode-pattern helpers, each deliberately narrow (see class javadoc) ----

	private static boolean definingChainReadsTaintedSlot(AbstractInsnNode[] insns, int astoreIndex,
			Set<Integer> taintedSlots) {
		// Walks backward from the ASTORE to the start of the current
		// straight-line run (stopping at a REAL control-flow merge point,
		// i.e. a label some jump/switch instruction actually targets - a
		// LabelNode on its own is usually just a LineNumberNode anchor, see
		// findImmediatelyGuardedSyncEdge's own javadoc for why treating
		// every label as a boundary is wrong), looking for an ALOAD of an
		// already-tainted slot anywhere in the value computation that fed
		// this store.
		Set<org.objectweb.asm.Label> realJumpTargets = collectJumpTargets(insns);
		for (int i = astoreIndex - 1; i >= 0; i--) {
			AbstractInsnNode insn = insns[i];
			if (insn instanceof org.objectweb.asm.tree.LabelNode label && realJumpTargets.contains(label.getLabel())) {
				break;
			}
			if (insn instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD
					&& taintedSlots.contains(load.var)) {
				return true;
			}
			if (insn instanceof VarInsnNode priorStore && priorStore.getOpcode() == Opcodes.ASTORE) {
				// A previous, unrelated store - the value chain for THIS
				// astore starts after it.
				break;
			}
		}
		return false;
	}

	private static Integer findReceiverSlotForInvoke(AbstractInsnNode[] insns, int invokeIndex) {
		MethodInsnNode call = (MethodInsnNode) insns[invokeIndex];
		int argCount = countArgs(call.desc);
		// Walk back past exactly argCount REAL value-producing instructions
		// (skipping pseudo Label/LineNumber/Frame nodes, which carry no
		// stack effect - see FlagResolver's own isPseudo for the same
		// distinction) to find the receiver - only handles the simple case
		// where each argument is a single real instruction (ALOAD/LDC),
		// matching this project's own idiom (`x.equals("LITERAL")`);
		// anything with multi-instruction argument expressions is out of
		// MVP scope and will simply fail to find an ALOAD at the computed
		// position, returning null (unresolved).
		int pos = invokeIndex;
		for (int remaining = argCount + 1; remaining > 0; remaining--) {
			pos = previousReal(insns, pos);
			if (pos < 0) {
				return null;
			}
		}
		if (!(insns[pos] instanceof VarInsnNode receiver) || receiver.getOpcode() != Opcodes.ALOAD) {
			return null;
		}
		return receiver.var;
	}

	private static int previousReal(AbstractInsnNode[] insns, int fromExclusive) {
		for (int i = fromExclusive - 1; i >= 0; i--) {
			if (!isPseudo(insns[i])) {
				return i;
			}
		}
		return -1;
	}

	private static boolean isPseudo(AbstractInsnNode insn) {
		return insn instanceof org.objectweb.asm.tree.LabelNode || insn instanceof org.objectweb.asm.tree.LineNumberNode
				|| insn instanceof org.objectweb.asm.tree.FrameNode;
	}

	private static int countArgs(String descriptor) {
		org.objectweb.asm.Type[] argTypes = org.objectweb.asm.Type.getArgumentTypes(descriptor);
		return argTypes.length;
	}

	private static String closestPrecedingReceiveEdge(int beforeIndex, Map<Integer, String> receiveEdgeIdByInsnIndex) {
		String best = null;
		int bestIndex = -1;
		for (Map.Entry<Integer, String> e : receiveEdgeIdByInsnIndex.entrySet()) {
			if (e.getKey() < beforeIndex && e.getKey() > bestIndex) {
				bestIndex = e.getKey();
				best = e.getValue();
			}
		}
		return best;
	}

	private static String findImmediatelyGuardedSyncEdge(AbstractInsnNode[] insns, int ifInsnIndex,
			Map<Integer, String> syncEdgeIdByInsnIndex) {
		JumpInsnNode jump = (JumpInsnNode) insns[ifInsnIndex];
		int target = indexOf(insns, jump.label);
		// A LabelNode by itself is not a real control-flow merge point -
		// javac/ASM emit one at nearly every source line purely to anchor
		// LineNumberNode debug info, with no other instruction ever
		// jumping to it. Only a label that some OTHER jump/switch
		// instruction in the method actually targets is a genuine merge -
		// same distinction FlagResolver's own isPseudo/leader-finding
		// logic relies on, just computed directly here instead of via a
		// full leader pass (see class javadoc scope note).
		Set<org.objectweb.asm.Label> realJumpTargets = collectJumpTargets(insns);

		// MVP dominance check (see class javadoc): accept only if the
		// fallthrough path (IFEQ not taken -> guarded body runs
		// immediately next) reaches a recognized sync-point call before
		// any genuine merge point or another branch, i.e. the guarded body
		// is a single straight-line run ending in the sync call.
		for (int i = ifInsnIndex + 1; i < target; i++) {
			if (insns[i] instanceof org.objectweb.asm.tree.LabelNode label
					&& realJumpTargets.contains(label.getLabel())) {
				return null;
			}
			if (insns[i] instanceof JumpInsnNode) {
				// A nested branch inside the guarded body - out of MVP
				// scope (see class javadoc), refuse rather than guess.
				return null;
			}
			if (insns[i] instanceof MethodInsnNode call) {
				SyncPoint.Kind kind = SyncPointMatcher.matchKind(call.getOpcode(), call.owner, call.name, call.desc);
				if (kind != null) {
					return syncEdgeIdByInsnIndex.get(i);
				}
			}
		}
		return null;
	}

	private static Map<MethodNode, Map<Integer, String>> syncEdgeIdsByMethod(ClassNode classNode, String className) {
		Map<MethodNode, Map<Integer, String>> out = new HashMap<>();
		int edgeCounter = 0;
		for (MethodNode method : classNode.methods) {
			if (method.instructions == null || method.instructions.size() == 0) {
				continue;
			}
			AbstractInsnNode[] insns = method.instructions.toArray();
			Map<Integer, String> ids = new HashMap<>();
			for (int i = 0; i < insns.length; i++) {
				if (!(insns[i] instanceof MethodInsnNode call)) {
					continue;
				}
				SyncPoint.Kind kind = SyncPointMatcher.matchKind(call.getOpcode(), call.owner, call.name, call.desc);
				if (kind == null) {
					continue;
				}
				ids.put(i, className + "#" + method.name + ":" + (edgeCounter++));
			}
			if (!ids.isEmpty()) {
				out.put(method, ids);
			}
		}
		return out;
	}

	private static Set<org.objectweb.asm.Label> collectJumpTargets(AbstractInsnNode[] insns) {
		Set<org.objectweb.asm.Label> targets = new HashSet<>();
		for (AbstractInsnNode insn : insns) {
			if (insn instanceof JumpInsnNode j) {
				targets.add(j.label.getLabel());
			} else if (insn instanceof org.objectweb.asm.tree.TableSwitchInsnNode t) {
				targets.add(t.dflt.getLabel());
				for (org.objectweb.asm.tree.LabelNode l : t.labels) {
					targets.add(l.getLabel());
				}
			} else if (insn instanceof org.objectweb.asm.tree.LookupSwitchInsnNode l2) {
				targets.add(l2.dflt.getLabel());
				for (org.objectweb.asm.tree.LabelNode l : l2.labels) {
					targets.add(l.getLabel());
				}
			}
		}
		return targets;
	}

	private static int indexOf(AbstractInsnNode[] insns, org.objectweb.asm.tree.LabelNode label) {
		for (int i = 0; i < insns.length; i++) {
			if (insns[i] == label) {
				return i;
			}
		}
		return insns.length;
	}

	private static String findComparedStringLiteral(AbstractInsnNode[] insns, int invokeIndex) {
		int pos = previousReal(insns, invokeIndex);
		if (pos >= 0 && insns[pos] instanceof LdcInsnNode ldc && ldc.cst instanceof String s) {
			return s;
		}
		return null;
	}

	private static String findSentStringLiteral(AbstractInsnNode[] insns, int sendInsnIndex) {
		// Rather than precisely re-deriving DatagramPacket's constructor
		// argument boundaries, scan the straight-line run of instructions
		// immediately before the send (stopping at the first label, i.e.
		// never crossing a control-flow merge - see class javadoc scope
		// note) for a `LDC "literal"; INVOKEVIRTUAL String#getBytes` pair.
		// Correct for this project's own idiom (one literal-derived byte[]
		// built right before the send it feeds, one branch per outcome -
		// see quorum-handshake's Peer or FlagChainStress's Source/Relay)
		// and conservatively null otherwise (multiple sends sharing a
		// helper, a reused buffer, a non-literal payload, etc.) - a null
		// here only disables literal-based disambiguation, it can never
		// produce a wrong resolution.
		Set<org.objectweb.asm.Label> realJumpTargets = collectJumpTargets(insns);
		for (int i = sendInsnIndex - 1; i >= 0; i--) {
			if (insns[i] instanceof org.objectweb.asm.tree.LabelNode label && realJumpTargets.contains(label.getLabel())) {
				break;
			}
			if (insns[i] instanceof MethodInsnNode getBytes && getBytes.getOpcode() == Opcodes.INVOKEVIRTUAL
					&& getBytes.owner.equals("java/lang/String") && getBytes.name.equals("getBytes")
					&& i - 1 >= 0 && insns[i - 1] instanceof LdcInsnNode ldc && ldc.cst instanceof String s) {
				return s;
			}
		}
		return null;
	}

	private static ClassNode readClass(File classFile) throws IOException {
		ClassNode classNode = new ClassNode();
		try (FileInputStream in = new FileInputStream(classFile)) {
			new ClassReader(in).accept(classNode, 0);
		}
		return classNode;
	}
}
