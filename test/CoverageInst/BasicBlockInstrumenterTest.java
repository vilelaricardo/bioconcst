package CoverageInst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import CoverageInst.BasicBlockInstrumenter.ClassResult;
import CoverageInst.support.FixtureCompiler;

/**
 * BasicBlockInstrumenter is what GraphDistance walks - a wrong block
 * boundary, a swapped taken/fallthrough successor, or a broken
 * interprocedural link silently corrupts the search gradient rather than
 * crashing anything, so these tests check the documented contracts directly:
 * block/successor shape for a branch, sync-edge-to-block mapping, the
 * "no sync point, no instrumentation at all" skip (the busy-wait-loop
 * overhead guard), and the shared-merged-graph contract interprocedural
 * inlining depends on.
 */
class BasicBlockInstrumenterTest {

	private static ClassResult instrument(String className, String source) throws Exception {
		File classFile = FixtureCompiler.compileOne(className, source);
		return BasicBlockInstrumenter.instrument(classFile, className);
	}

	@Test
	void aRecognizedBranchProducesATakenThenFallthroughSuccessorPair() throws Exception {
		ClassResult result = instrument("BranchFixture", """
				import java.util.concurrent.Semaphore;

				public class BranchFixture {
				    public static void main(String[] args) throws InterruptedException {
				        Semaphore sem = new Semaphore(1);
				        sem.acquire();
				        if (args.length > 0) {
				            System.out.println("has args");
				        } else {
				            System.out.println("no args");
				        }
				        sem.release();
				    }
				}
				""");

		assertEquals(1, result.branchPredicates.size(), "expected exactly one recognized branch: " + result.branchPredicates);
		Map.Entry<String, Integer> entry = result.branchPredicates.entrySet().iterator().next();
		assertTrue(BranchDistance.isRecognized(entry.getValue()), "opcode must be one BranchDistance recognizes");

		ControlFlowGraph graph = result.graphs.get("BranchFixture#main");
		List<String> branchSuccessors = graph.successors().get(entry.getKey());
		assertEquals(2, branchSuccessors.size(), "a two-way predicate must have exactly [taken, fallthrough]");
	}

	@Test
	void twoSyncPointsInOneStraightLineBlockShareThatBlock() throws Exception {
		// No branch, no local call, no return in between - acquire and
		// release are two statements in a row, so they belong to the SAME
		// basic block by construction (a block only ends at a jump, switch,
		// local call, or terminal instruction).
		ClassResult result = instrument("SemFixture", """
				import java.util.concurrent.Semaphore;

				public class SemFixture {
				    public static void main(String[] args) throws InterruptedException {
				        Semaphore sem = new Semaphore(1);
				        sem.acquire();
				        System.out.println("in between");
				        sem.release();
				    }
				}
				""");

		assertEquals(2, result.syncEdgeBlocks.size());
		String acquireBlock = result.syncEdgeBlocks.get("SemFixture#main:0");
		String releaseBlock = result.syncEdgeBlocks.get("SemFixture#main:1");
		assertTrue(acquireBlock != null && releaseBlock != null, "both edge ids must map to a block: " + result.syncEdgeBlocks);
		assertEquals(acquireBlock, releaseBlock);
	}

	@Test
	void syncPointsSeparatedByABranchLandInDifferentBlocks() throws Exception {
		ClassResult result = instrument("SplitFixture", """
				import java.util.concurrent.Semaphore;

				public class SplitFixture {
				    public static void main(String[] args) throws InterruptedException {
				        Semaphore sem = new Semaphore(1);
				        sem.acquire();
				        if (args.length > 0) {
				            System.out.println("has args");
				        }
				        sem.release();
				    }
				}
				""");

		String acquireBlock = result.syncEdgeBlocks.get("SplitFixture#main:0");
		String releaseBlock = result.syncEdgeBlocks.get("SplitFixture#main:1");
		assertFalse(acquireBlock.equals(releaseBlock),
				"a branch in between must put acquire and release in different blocks");
	}

	@Test
	void aMethodWithNoSyncPointIsSkippedEntirelyEvenWhenCalled() throws Exception {
		// Regression guard for the busy-wait-loop overhead concern documented
		// on instrumentMethod: a callee with no sync point of its own must
		// never appear in the graph at all, and the call into it must not be
		// treated as an interprocedural call-boundary.
		ClassResult result = instrument("SkipFixture", """
				import java.util.concurrent.Semaphore;

				public class SkipFixture {
				    static Semaphore sem = new Semaphore(1);

				    public static void main(String[] args) throws InterruptedException {
				        sem.acquire();
				        busyWait();
				        sem.release();
				    }

				    static void busyWait() {
				        for (int i = 0; i < 1000; i++) { }
				    }
				}
				""");

		assertTrue(result.graphs.containsKey("SkipFixture#main"));
		assertFalse(result.graphs.containsKey("SkipFixture#busyWait"),
				"a method with no sync point of its own must never be instrumented or graphed");
	}

	@Test
	void interproceduralCallsShareOneMergedGraphAcrossCallerAndCallee() throws Exception {
		ClassResult result = instrument("CallerFixture", """
				import java.util.concurrent.Semaphore;

				public class CallerFixture {
				    static Semaphore sem = new Semaphore(0);

				    public static void main(String[] args) throws InterruptedException {
				        helper();
				        sem.acquire();
				    }

				    static void helper() {
				        sem.release();
				    }
				}
				""");

		ControlFlowGraph mainGraph = result.graphs.get("CallerFixture#main");
		ControlFlowGraph helperGraph = result.graphs.get("CallerFixture#helper");
		// Each member gets its own ControlFlowGraph wrapper instance, but all
		// of a component's wrappers share the SAME underlying successors map
		// and entry block id - that's the "one merged graph" contract, not
		// wrapper identity.
		assertSame(mainGraph.successors(), helperGraph.successors(),
				"caller and callee must share the exact same underlying successors map");
		assertEquals(mainGraph.entryBlockId(), helperGraph.entryBlockId());

		// main() is the canonical entry (matches main([Ljava/lang/String;)V),
		// even though the query below is anchored on the CALLEE's own edge.
		assertEquals("CallerFixture#main:B0", mainGraph.entryBlockId());

		String helperReleaseBlock = result.syncEdgeBlocks.get("CallerFixture#helper:1");
		List<String> pathToHelper = mainGraph.shortestPathTo(helperReleaseBlock);
		assertEquals(List.of("CallerFixture#main:B0", helperReleaseBlock), pathToHelper,
				"the call site must link straight to the callee's entry block");

		String mainAcquireBlock = result.syncEdgeBlocks.get("CallerFixture#main:0");
		List<String> pathBackToMain = mainGraph.shortestPathTo(mainAcquireBlock);
		assertEquals(List.of("CallerFixture#main:B0", helperReleaseBlock, mainAcquireBlock), pathBackToMain,
				"the callee's exit must link back to the caller's continuation, so reaching main's own next "
						+ "statement still goes through the callee first (a safe over-approximation, not a "
						+ "precise call-stack-aware analysis)");
	}
}
