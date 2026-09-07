package CoverageInst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import CoverageInst.CoverageInstRun.ProcessLaunchSpec;
import CoverageInst.CoverageInstRun.TestCaseResult;

/**
 * The one deliberately "slow" tier of this suite (it launches real JVMs) -
 * kept to a couple of cases by design, not a place to re-cover individual
 * primitives (ClassInstrumenterTest/BasicBlockInstrumenterTest already do
 * that in-process). This exists to catch regressions in the integration
 * itself: compile -> instrument -> actually run a process -> evaluate its
 * real trace against a required-edge set derived the same way
 * RequiredElementsMain derives one for a real benchmark, plus the
 * documented hung-process force-kill budget every migrated benchmark this
 * session depended on.
 */
class CoverageInstRunTest {

	private static File writeSource(String className, String source) throws Exception {
		File sourceDir = Files.createTempDirectory("coverageinst-run-test-src").toFile();
		Files.writeString(new File(sourceDir, className + ".java").toPath(), source);
		return sourceDir;
	}

	@Test
	void compilesInstrumentsRunsAndEvaluatesASingleProcessEndToEnd() throws Exception {
		File sourceDir = writeSource("Solo", """
				import java.util.concurrent.Semaphore;

				public class Solo {
				    public static void main(String[] args) throws InterruptedException {
				        Semaphore sem = new Semaphore(1);
				        sem.acquire();
				        sem.release();
				    }
				}
				""");
		File workDir = Files.createTempDirectory("coverageinst-run-test-work").toFile();

		CoverageInstRun run = CoverageInstRun.prepare(sourceDir, workDir);

		// Required edges derived the same way RequiredElementsMain derives
		// them for a real benchmark: scan the ORIGINAL (uninstrumented)
		// compiled classes prepare() produced, no roleLinks needed since
		// this is a same-process identity pairing.
		ProcessInstance process = ClassScanner.scanProcess(new File(workDir, "compiled"), 0, "Solo", List.of("Solo"));
		List<RequiredEdge> required = RequiredElementsGenerator.generate(List.of(process),
				new Topology(List.of(), Map.of(), Map.of(), Map.of(), Map.of()));
		assertEquals(1, required.size(), "acquire+release in one process is exactly one identity edge");

		TestCaseResult result = run.runTestCase(0, List.of(new ProcessLaunchSpec(0, "Solo", new String[0])), 10_000);

		assertTrue(result.allProcessesCompleted);
		CoverageEvaluator.Result evaluation = CoverageEvaluator.evaluate(required, result.coverageDir, List.of(0));
		assertEquals(100.0, evaluation.coveragePercent(), () -> "expected full coverage, anomalies: " + evaluation.anomalies);
	}

	@Test
	void aHungProcessIsForciblyKilledAtTheTimeBudgetInsteadOfHangingTheRun() throws Exception {
		File sourceDir = writeSource("Hang", """
				public class Hang {
				    public static void main(String[] args) throws InterruptedException {
				        Thread.sleep(60_000);
				    }
				}
				""");
		File workDir = Files.createTempDirectory("coverageinst-run-test-work").toFile();
		CoverageInstRun run = CoverageInstRun.prepare(sourceDir, workDir);

		long start = System.currentTimeMillis();
		TestCaseResult result = run.runTestCase(0, List.of(new ProcessLaunchSpec(0, "Hang", new String[0])), 500);
		long elapsedMs = System.currentTimeMillis() - start;

		assertFalse(result.allProcessesCompleted);
		assertTrue(elapsedMs < 10_000,
				"a hung process must be force-killed near the configured budget, not waited out - took " + elapsedMs + "ms");
	}
}
