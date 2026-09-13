package CoverageInst;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import BioConcST.BenchmarkConfig;
import BioConcST.CoverageInstConfig;
import BioConcST.ExperimentConfig;
import BioConcST.ProcessSpec;
import BioConcST.ReplayBundle;
import BioConcST.RoleLinkSpec;

/**
 * Independently re-verifies one champion individual exported by
 * CoverageInstStrategy (a "-replay.json" file next to the usual
 * "-execution<n>.csv"): recompiles/instruments the benchmark fresh, replays
 * the individual's exact test input under its recorded ReplaySchedule (see
 * ReplaySchedule's javadoc for why sender order needs to be forced at all),
 * and reports whether the resulting coverage matches what was recorded
 * during the original search - proof the reported number is real and
 * reproducible, not a one-off race that happened to look good, matching
 * what ValiPar's own controlled-execution/replay feature is for.
 *
 * java CoverageInst.ReplayMain <config.json> <bundle.json> <bundleIndex> [workDir]
 */
public class ReplayMain {

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length < 3) {
			System.err.println("Usage: ReplayMain <config.json> <bundle.json> <bundleIndex> [workDir]");
			System.exit(1);
		}

		ExperimentConfig config = ExperimentConfig.load(args[0]);
		BenchmarkConfig benchmark = config.benchmark;
		CoverageInstConfig ci = benchmark.coverageInst;
		if (ci == null) {
			System.err.println("benchmark.coverageInst is missing in " + args[0]);
			System.exit(1);
		}

		List<ReplayBundle> bundles = new ObjectMapper().readValue(new File(args[1]),
				new TypeReference<List<ReplayBundle>>() {
				});
		int index = Integer.parseInt(args[2]);
		if (index < 0 || index >= bundles.size()) {
			System.err.println("bundleIndex " + index + " out of range (0.." + (bundles.size() - 1) + ")");
			System.exit(1);
		}
		ReplayBundle bundle = bundles.get(index);

		File workDir = new File(args.length > 3 ? args[3] : "./cov-replay-" + benchmark.name);
		org.apache.commons.io.FileUtils.deleteQuietly(workDir);
		CoverageInstRun run = CoverageInstRun.prepare(new File(benchmark.path), workDir);

		List<ProcessInstance> processes = new ArrayList<>();
		List<Integer> processIds = new ArrayList<>();
		for (ProcessSpec spec : benchmark.testSetupProcesses) {
			List<String> classNames = ci.extraClassesByRole != null
					&& ci.extraClassesByRole.containsKey(spec.className) ? ci.extraClassesByRole.get(spec.className)
							: List.of(spec.className);
			processes.add(ClassScanner.scanProcess(run.instrumentedDir(), spec.id, spec.className, classNames));
			processIds.add(spec.id);
		}

		List<RoleLink> roleLinks = new ArrayList<>();
		if (ci.roleLinks != null) {
			for (RoleLinkSpec link : ci.roleLinks) {
				roleLinks.add(new RoleLink(link.from, link.to));
			}
		}
		Topology topology = new Topology(roleLinks, ci.fixedMessageTargets != null ? ci.fixedMessageTargets : Map.of(),
				ci.fixedMessageSources != null ? ci.fixedMessageSources : Map.of(),
				ci.identityGroups != null ? ci.identityGroups : Map.of(),
				ci.causalDistance != null ? ci.causalDistance : Map.of());
		List<RequiredEdge> required = RequiredElementsGenerator.generate(processes, topology);

		File scheduleFile = new File(workDir, "replay-schedule.txt");
		ReplaySchedule.fromInlineString(bundle.replaySchedule).save(scheduleFile);

		List<CoverageInstRun.ProcessLaunchSpec> launchSpecs = new ArrayList<>();
		for (ReplayBundle.ProcessLaunch process : bundle.processes) {
			launchSpecs.add(new CoverageInstRun.ProcessLaunchSpec(process.id, process.className, process.args));
		}

		int execTimeLimitMs = benchmark.execTimeLimitMs != null ? benchmark.execTimeLimitMs
				: BenchmarkConfig.DEFAULT_EXEC_TIME_LIMIT_MS;
		CoverageInstRun.TestCaseResult result = run.runTestCase(0, launchSpecs, execTimeLimitMs, scheduleFile);

		if (!result.allProcessesCompleted) {
			System.out.println("REPLAY FAILED: a process had to be force-killed (timeout) - could not verify");
			System.exit(2);
		}

		CoverageEvaluator.Result evalResult = CoverageEvaluator.evaluate(required, result.coverageDir, processIds);

		System.out.printf("Recorded:  %.2f%% (%d/%d)%n", bundle.coveragePercent, bundle.coveredCount,
				bundle.totalRequired);
		System.out.printf("Replayed:  %.2f%% (%d/%d)%n", evalResult.coveragePercent(), evalResult.covered.size(),
				evalResult.totalRequired);

		boolean matches = evalResult.covered.size() == bundle.coveredCount
				&& evalResult.totalRequired == bundle.totalRequired;
		System.out.println(matches ? "RESULT: MATCH - coverage independently reproduced"
				: "RESULT: MISMATCH - replayed coverage differs from the recorded value");
		System.exit(matches ? 0 : 1);
	}
}
