package CoverageInst;

import org.objectweb.asm.Opcodes;

/**
 * Korel (1990) / Tracey et al. branch distance for the numeric comparison
 * predicates BasicBlockInstrumenter recognizes (IF_ICMPxx / IFxx-against-
 * zero): "how close were the actual runtime operands to making this
 * predicate go the OTHER way than what was observed." This is the missing
 * half of classical search-based test generation's combined fitness -
 * approach level (distance in the graph, already provided by
 * ControlFlowGraph/GraphDistance) alone can't tell a mutation operator
 * "how close" a numeric value like 700 is to a window like [480,519], since
 * from the graph's point of view a branch is just taken-or-not. Branch
 * distance is what turns that into a smooth, climbable gradient.
 *
 * Deliberately scoped to plain int comparisons (see
 * BasicBlockInstrumenter's own scope note): IF_ACMPEQ/NE (reference
 * equality) and IFNULL/NONNULL have no natural numeric gradient and are
 * simply not recognized here - GraphDistance falls back to its existing
 * "no predicate available" handling for those.
 */
public final class BranchDistance {

	private BranchDistance() {
	}

	public static boolean isRecognized(int opcode) {
		return switch (opcode) {
			case Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT,
					Opcodes.IF_ICMPLE, Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT,
					Opcodes.IFLE ->
				true;
			default -> false;
		};
	}

	public static boolean isTwoOperand(int opcode) {
		return switch (opcode) {
			case Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT,
					Opcodes.IF_ICMPLE ->
				true;
			default -> false;
		};
	}

	/**
	 * @param opcode      the recognized IF_ICMPxx/IFxx opcode (single-operand
	 *                    forms compare {@code a} against zero, i.e. b=0)
	 * @param a           first (or only) observed operand
	 * @param b           second observed operand (0 for single-operand forms)
	 * @param wantedTaken whether reaching the target required this jump to be
	 *                    taken (true) or fallen through (false)
	 * @return a value in [0,1) - 0 only if the wanted direction was in fact
	 *         already taken (not a real divergence point; callers shouldn't
	 *         normally hit this), otherwise strictly positive and smaller
	 *         the closer the operands were to flipping the outcome
	 */
	public static double compute(int opcode, int a, int b, boolean wantedTaken) {
		boolean conditionTrue = evaluate(opcode, a, b);
		if (conditionTrue == wantedTaken) {
			return 0.0;
		}
		double raw = rawDistance(opcode, a, b);
		return raw / (raw + 1.0);
	}

	private static boolean evaluate(int opcode, int a, int b) {
		return switch (opcode) {
			case Opcodes.IF_ICMPEQ, Opcodes.IFEQ -> a == b;
			case Opcodes.IF_ICMPNE, Opcodes.IFNE -> a != b;
			case Opcodes.IF_ICMPLT, Opcodes.IFLT -> a < b;
			case Opcodes.IF_ICMPGE, Opcodes.IFGE -> a >= b;
			case Opcodes.IF_ICMPGT, Opcodes.IFGT -> a > b;
			case Opcodes.IF_ICMPLE, Opcodes.IFLE -> a <= b;
			default -> throw new IllegalArgumentException("Not a recognized numeric predicate opcode: " + opcode);
		};
	}

	// Only ever evaluated when the relation in question is actually false
	// (evaluate() != wanted, checked by compute()), so each branch's
	// arithmetic is always >= 0 by construction - Korel's classic per-
	// relation formulas.
	private static double rawDistance(int opcode, int a, int b) {
		return switch (opcode) {
			case Opcodes.IF_ICMPLT, Opcodes.IFLT -> (a - b) + 1.0;
			case Opcodes.IF_ICMPLE, Opcodes.IFLE -> (a - b) + 1.0;
			case Opcodes.IF_ICMPGT, Opcodes.IFGT -> (b - a) + 1.0;
			case Opcodes.IF_ICMPGE, Opcodes.IFGE -> (b - a) + 1.0;
			case Opcodes.IF_ICMPEQ, Opcodes.IFEQ -> Math.abs((double) a - b);
			// "!=" has no natural numeric gradient toward "being different" -
			// a fixed distance, same as GraphDistance's own no-predicate fallback.
			case Opcodes.IF_ICMPNE, Opcodes.IFNE -> 1.0;
			default -> throw new IllegalArgumentException("Not a recognized numeric predicate opcode: " + opcode);
		};
	}
}
