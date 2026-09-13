package CoverageInst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import CoverageInst.support.FixtureCompiler;

/**
 * Pins down FlagResolver's automatic reaching-definitions analysis against
 * the exact quorum-handshake case study documented in
 * debug-geracao-quorum-handshake.md/research_questions.md: `inWindow =
 * (value >= 480 && value <= 519)`, consumed by `if (inWindow) {...}`. The
 * expected result here (Peer#main:B1 and Peer#main:B0, both
 * wantedTaken=false) is the same chainedDistance config a human worked out
 * by hand and validated in that benchmark's config - this test is the
 * automatic-inference counterpart of that manual answer.
 */
class FlagResolverTest {

	@Test
	void resolvesConjunctionOfTwoComparisonsMatchingTheQuorumHandshakeCaseStudy() throws Exception {
		String source = """
				public class InWindowFixture {
				    public static void main(String[] args) {
				        int value = Integer.parseInt(args[0]);
				        boolean inWindow = (value >= 480 && value <= 519);
				        if (inWindow) {
				            System.out.println("QUORUM");
				        } else {
				            System.out.println("NORMAL");
				        }
				    }
				}
				""";
		File classFile = FixtureCompiler.compileOne("InWindowFixture", source);

		List<FlagResolver.Resolution> resolutions = FlagResolver.resolve(classFile);

		assertEquals(1, resolutions.size(), "expected exactly one resolvable flag block");
		FlagResolver.Resolution r = resolutions.get(0);
		assertEquals(2, r.sources().size(), "the conjunction has two comparisons, both should resolve");
		Set<String> blocks = Set.of(r.sources().get(0).block(), r.sources().get(1).block());
		assertTrue(blocks.stream().allMatch(b -> b.startsWith("InWindowFixture#main:B")));
		assertFalse(r.sources().get(0).wantedTaken());
		assertFalse(r.sources().get(1).wantedTaken());
	}

	@Test
	void resolvesASingleComparisonWithNoConjunction() throws Exception {
		String source = """
				public class SingleFlagFixture {
				    public static void main(String[] args) {
				        int value = Integer.parseInt(args[0]);
				        boolean big = (value >= 100);
				        if (big) {
				            System.out.println("BIG");
				        } else {
				            System.out.println("SMALL");
				        }
				    }
				}
				""";
		File classFile = FixtureCompiler.compileOne("SingleFlagFixture", source);

		List<FlagResolver.Resolution> resolutions = FlagResolver.resolve(classFile);

		assertEquals(1, resolutions.size());
		assertEquals(1, resolutions.get(0).sources().size());
		assertFalse(resolutions.get(0).sources().get(0).wantedTaken());
	}

	/**
	 * Regression test for a real false positive found by hand-verifying the
	 * two-phase-commit benchmark's Coordinator: `allYes = vote1.equals(...)
	 * && vote2.equals(...) && ...` compiles to a chain of IFEQ blocks (each
	 * testing a String#equals call's boolean result), not numeric
	 * comparisons. Before the fix, resolveChain accepted those IFEQ blocks
	 * as if they were terminal numeric sources - substituting one
	 * gradient-free flag for another instead of actually recovering a
	 * gradient. IFEQ/IFNE must never be treated as a resolvable source.
	 */
	@Test
	void doesNotChainThroughAnotherFlagWhenTheConjunctsAreMethodCallsNotNumericComparisons() throws Exception {
		String source = """
				public class ChainedEqualsFixture {
				    public static void main(String[] args) {
				        boolean allYes = args[0].equals("YES") && args[1].equals("YES");
				        if (allYes) {
				            System.out.println("COMMIT");
				        } else {
				            System.out.println("ABORT");
				        }
				    }
				}
				""";
		File classFile = FixtureCompiler.compileOne("ChainedEqualsFixture", source);

		List<FlagResolver.Resolution> resolutions = FlagResolver.resolve(classFile);

		assertTrue(resolutions.isEmpty(),
				"a conjunction of String#equals calls has no numeric predicate to chain to - must be left unresolved");
	}

	@Test
	void reportsNothingRatherThanGuessingWhenTheFlagIsNotAConjunctionOfComparisons() throws Exception {
		String source = """
				public class OpaqueFlagFixture {
				    public static void main(String[] args) {
				        boolean ready = Boolean.parseBoolean(args[0]);
				        if (ready) {
				            System.out.println("READY");
				        } else {
				            System.out.println("NOT READY");
				        }
				    }
				}
				""";
		File classFile = FixtureCompiler.compileOne("OpaqueFlagFixture", source);

		List<FlagResolver.Resolution> resolutions = FlagResolver.resolve(classFile);

		assertTrue(resolutions.isEmpty(),
				"a flag defined by a method call, not a numeric comparison chain, must be left unresolved");
	}
}
