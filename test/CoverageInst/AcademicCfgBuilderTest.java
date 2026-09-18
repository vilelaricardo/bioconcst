package CoverageInst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import CoverageInst.BasicBlockInstrumenter.ClassResult;
import CoverageInst.support.FixtureCompiler;

/**
 * AcademicCfgBuilder trades BasicBlockInstrumenter's safe-but-tangled
 * over-approximation for a diagram a human would actually draw by hand - the
 * two documented differences (follow every same-class call, sync point or
 * not; duplicate a callee's body once per call site instead of sharing one
 * node) are exactly what these tests pin down, alongside the noise filters
 * (no <init>/<clinit> nodes) and the multi-component case
 * (Buffer_With_Lock_Condition's shape: several mutually-uncalled methods,
 * each with its own sync point, must land in separate, non-overlapping
 * graphs rather than one contaminating the other).
 */
class AcademicCfgBuilderTest {

	private static ClassResult build(String className, String source) throws Exception {
		File classFile = FixtureCompiler.compileOne(className, source);
		return AcademicCfgBuilder.build(classFile, className);
	}

	@Test
	void followsASameClassCallEvenWhenTheCalleeHasNoSyncPointOfItsOwn() throws Exception {
		// The opposite of BasicBlockInstrumenterTest's
		// aMethodWithNoSyncPointIsSkippedEntirely - this is the whole point
		// of the academic builder existing separately.
		ClassResult result = build("Fixture1", """
				public class Fixture1 {
				    public static void main(String[] args) {
				        helper();
				    }

				    static void helper() {
				        System.out.println("no sync here");
				    }
				}
				""");

		assertTrue(result.graphs.containsKey("Fixture1#helper"),
				"a callee with no sync point of its own must still be followed and graphed");
	}

	@Test
	void duplicatesACalleesBodyOncePerCallSiteInsteadOfSharingOneNode() throws Exception {
		ClassResult result = build("Fixture2", """
				public class Fixture2 {
				    public static void main(String[] args) {
				        helper();
				        helper();
				    }

				    static void helper() {
				        System.out.println("shared");
				    }
				}
				""");

		ControlFlowGraph mainGraph = result.graphs.get("Fixture2#main");
		List<String> firstCallSuccessors = mainGraph.successors().get("Fixture2#main:B0");
		List<String> secondCallSuccessors = mainGraph.successors().get("Fixture2#main:B1");

		assertEquals(1, firstCallSuccessors.size());
		assertEquals(1, secondCallSuccessors.size());
		assertNotEquals(firstCallSuccessors.get(0), secondCallSuccessors.get(0),
				"each call site must get its OWN copy of helper's entry block, not share one node");
	}

	@Test
	void neverIncludesConstructorOrStaticInitializerNodes() throws Exception {
		ClassResult result = build("Fixture3", """
				import java.util.concurrent.Semaphore;

				public class Fixture3 {
				    static Semaphore sem = new Semaphore(1);

				    public Fixture3() {
				        System.out.println("constructed");
				    }

				    public static void main(String[] args) throws InterruptedException {
				        sem.acquire();
				    }
				}
				""");

		assertFalse(result.graphs.keySet().stream().anyMatch(key -> key.contains("<init>") || key.contains("<clinit>")),
				"constructors and static initializers are never real process behavior, must not appear as nodes: "
						+ result.graphs.keySet());
	}

	@Test
	void selfRecursionExpandsOnceThenStopsInsteadOfHangingForever() throws Exception {
		// Documented but previously unverified: "a call whose target is
		// never re-entered along the current call path is expanded,
		// otherwise it's left as a dead end - no current benchmark
		// recurses, but this must not hang if one someday does." The guard
		// checks activeOnPath (the state BEFORE entering the current
		// frame), not the frame's own updated set, so a method calling
		// itself gets unrolled exactly ONCE (two copies total: the original
		// plus one recursive copy) before the second attempted recursive
		// call is cut off - not zero (which would misrepresent a real call
		// as an immediate dead end) and not unbounded (which would hang).
		ClassResult result = assertTimeout(Duration.ofSeconds(5), () -> build("RecursiveFixture", """
				public class RecursiveFixture {
				    public static void main(String[] args) {
				        recurse();
				    }

				    static void recurse() {
				        recurse();
				    }
				}
				"""));

		ControlFlowGraph mainGraph = result.graphs.get("RecursiveFixture#main");
		long recurseEntryCopies = mainGraph.successors().keySet().stream()
				.filter(key -> key.startsWith("RecursiveFixture#recurse:B0")).count();

		assertEquals(2, recurseEntryCopies);
	}

	@Test
	void mutuallyUncalledMethodsWithTheirOwnSyncPointsBecomeSeparateNonOverlappingComponents() throws Exception {
		// Buffer_With_Lock_Condition's shape: main() never calls
		// neverCalledFromMain() at all (a different class calls it in the
		// real benchmark) - both still need their own graph, and those
		// graphs must not share blocks with each other.
		ClassResult result = build("MultiComponent", """
				import java.util.concurrent.Semaphore;

				public class MultiComponent {
				    public static void main(String[] args) throws InterruptedException {
				        Semaphore sem = new Semaphore(0);
				        sem.acquire();
				    }

				    static void neverCalledFromMain() throws InterruptedException {
				        Semaphore sem2 = new Semaphore(0);
				        sem2.release();
				    }
				}
				""");

		ControlFlowGraph mainGraph = result.graphs.get("MultiComponent#main");
		ControlFlowGraph otherGraph = result.graphs.get("MultiComponent#neverCalledFromMain");
		assertTrue(mainGraph != null && otherGraph != null, "both methods need their own graph");
		assertNotSame(mainGraph.successors(), otherGraph.successors(),
				"unconnected components must not share the same underlying successors map");
		assertFalse(mainGraph.successors().containsKey(otherGraph.entryBlockId()),
				"one component's blocks must not leak into the other's");
	}
}
