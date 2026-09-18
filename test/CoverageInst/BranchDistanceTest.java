package CoverageInst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

/**
 * Locks down the Korel/Tracey branch-distance formulas GraphDistance depends
 * on for its numeric-predicate gradient - a wrong sign or off-by-one here
 * silently degrades the search (a branch that never gets closer looks
 * identical to one already satisfied) instead of failing loudly, so this is
 * exactly the kind of logic worth pinning down directly rather than only
 * observing through GA convergence behavior.
 */
class BranchDistanceTest {

	@Test
	void recognizesEveryIntComparisonOpcode() {
		int[] recognized = { Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE,
				Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE, Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE,
				Opcodes.IFGT, Opcodes.IFLE };
		for (int opcode : recognized) {
			assertTrue(BranchDistance.isRecognized(opcode), "expected opcode " + opcode + " to be recognized");
		}
	}

	@Test
	void doesNotRecognizeReferenceComparisonsOrNullChecks() {
		// IF_ACMPEQ/NE and IFNULL/NONNULL have no natural numeric gradient -
		// see the class javadoc's scope note.
		assertFalse(BranchDistance.isRecognized(Opcodes.IF_ACMPEQ));
		assertFalse(BranchDistance.isRecognized(Opcodes.IF_ACMPNE));
		assertFalse(BranchDistance.isRecognized(Opcodes.IFNULL));
		assertFalse(BranchDistance.isRecognized(Opcodes.IFNONNULL));
		assertFalse(BranchDistance.isRecognized(Opcodes.GOTO));
	}

	@Test
	void onlyTheIcmpFamilyIsTwoOperand() {
		assertTrue(BranchDistance.isTwoOperand(Opcodes.IF_ICMPLT));
		assertFalse(BranchDistance.isTwoOperand(Opcodes.IFLT), "IFLT compares against zero, single operand");
	}

	@Test
	void computeIsZeroWhenTheWantedDirectionAlreadyHappened() {
		// 3 < 5 is true, and that's the direction we wanted - no divergence.
		assertEquals(0.0, BranchDistance.compute(Opcodes.IF_ICMPLT, 3, 5, true));
		// 5 < 3 is false, and that's what we wanted (the fallthrough) - also no divergence.
		assertEquals(0.0, BranchDistance.compute(Opcodes.IF_ICMPLT, 5, 3, false));
	}

	@Test
	void ltAndLeUseKorelsOffByOneDistanceWhenWantingTheJumpTaken() {
		// IF_ICMPLT with a=10, b=5, wanted taken (i.e. wanted 10 < 5, which is
		// false) - raw = (a - b) + 1 = 6, normalized = 6 / 7.
		double distance = BranchDistance.compute(Opcodes.IF_ICMPLT, 10, 5, true);
		assertEquals(6.0 / 7.0, distance, 1e-9);
	}

	@Test
	void ltUsesTheNegatedGeFormulaWhenWantingTheFallthrough() {
		// Regression test for a real bug: a=3, b=5 (3 < 5 is TRUE), but we
		// wanted the fallthrough (wanted 3 >= 5) - the naive "just reuse LT's
		// own formula" gave raw=(3-5)+1=-1, an invalid negative distance.
		// Correct: measure distance from the NEGATED relation (a >= b)
		// instead - raw=(b-a)+1=(5-3)+1=3, normalized=3/4.
		double distance = BranchDistance.compute(Opcodes.IF_ICMPLT, 3, 5, false);
		assertEquals(3.0 / 4.0, distance, 1e-9);
		assertTrue(distance > 0.0 && distance < 1.0, "must stay a valid, non-negative gradient: " + distance);
	}

	@Test
	void gtAndGeMirrorLtAndLeInBothDirections() {
		// IF_ICMPGT with a=5, b=10, wanted taken (wanted 5 > 10, false) -
		// raw = (b - a) + 1 = 6, normalized = 6/7 - same shape as the LT case,
		// operands swapped.
		assertEquals(6.0 / 7.0, BranchDistance.compute(Opcodes.IF_ICMPGT, 5, 10, true), 1e-9);
		// a=10, b=5 (10 > 5 is TRUE), wanted the fallthrough (wanted 10 <= 5) -
		// negated relation is LE: raw=(a-b)+1=(10-5)+1=6, normalized=6/7.
		assertEquals(6.0 / 7.0, BranchDistance.compute(Opcodes.IF_ICMPGT, 10, 5, false), 1e-9);
	}

	@Test
	void eqUsesTheRawAbsoluteDifferenceWhenWantingEquality() {
		// a=7, b=10, wanted taken (wanted 7 == 10, false) - raw = |7-10| = 3,
		// normalized = 3/4.
		double distance = BranchDistance.compute(Opcodes.IF_ICMPEQ, 7, 10, true);
		assertEquals(3.0 / 4.0, distance, 1e-9);
	}

	@Test
	void eqFallsBackToNesFixedDistanceWhenWantingInequality() {
		// Regression test for a real bug: a==b (exact equality - the WORST
		// case for wanting inequality), wanted the fallthrough (wanted a !=
		// b). The naive "reuse EQ's own formula" gave raw=|a-b|=0, i.e.
		// "already basically satisfied" - backwards, since exact equality is
		// as far as possible from being unequal. Correct: negated relation is
		// NE, whose fixed 1.0 distance (no natural gradient for "different")
		// applies instead, normalized to 0.5.
		double distance = BranchDistance.compute(Opcodes.IF_ICMPEQ, 4, 4, false);
		assertEquals(0.5, distance, 1e-9);
	}

	@Test
	void neHasNoGradientAndUsesAFixedDistanceWhenWantingInequality() {
		// a==b, wanted taken (wanted a != b, false) - raw is always 1.0 for NE,
		// regardless of how far apart a and b could get, since "different" has
		// no natural numeric direction to climb toward.
		double distance = BranchDistance.compute(Opcodes.IF_ICMPNE, 4, 4, true);
		assertEquals(0.5, distance, 1e-9);
	}

	@Test
	void neFallsBackToEqsGradientWhenWantingEquality() {
		// Regression test: a and b are far apart, wanted the fallthrough
		// (wanted a == b, i.e. NE's relation false). The naive "reuse NE's
		// own fixed 1.0" couldn't distinguish "1 apart" from "1000 apart" -
		// a real gradient loss. Correct: negated relation is EQ, so distance
		// should shrink as a and b get closer together.
		double farApart = BranchDistance.compute(Opcodes.IF_ICMPNE, 0, 1000, false);
		double closeTogether = BranchDistance.compute(Opcodes.IF_ICMPNE, 0, 1, false);
		assertTrue(closeTogether < farApart,
				"closer operands must yield a smaller distance when wanting equality: " + closeTogether + " vs "
						+ farApart);
	}

	@Test
	void singleOperandFormsCompareAgainstZero() {
		// IFLT(a) is equivalent to IF_ICMPLT(a, 0).
		assertEquals(BranchDistance.compute(Opcodes.IF_ICMPLT, 10, 0, true),
				BranchDistance.compute(Opcodes.IFLT, 10, 0, true));
	}

	@Test
	void rejectsAnUnrecognizedOpcode() {
		assertThrows(IllegalArgumentException.class, () -> BranchDistance.compute(Opcodes.GOTO, 1, 2, true));
	}
}
