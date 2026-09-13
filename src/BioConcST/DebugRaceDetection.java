package BioConcST;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import CoverageInst.ClassScanner;
import CoverageInst.CoverageInstRun;
import CoverageInst.ProcessInstance;
import CoverageInst.RacePoint;
import CoverageInst.RacePoints;
import CoverageInst.ReplaySchedule;
import CoverageInst.RequiredEdge;
import CoverageInst.RequiredElementsGenerator;
import CoverageInst.RoleLink;
import CoverageInst.Topology;

/**
 * One-off verification tool (plan step "Verificação" 2+3): confirms
 * RacePoints.detect finds the expected race point for a benchmark, then
 * forces a specific winner via a hand-built ReplaySchedule and checks the
 * resulting trace actually reflects that forced order - proving the
 * low-level mechanism (ReplaySchedule.fromInlineString -> save ->
 * CoverageInstRun.runTestCase(..., scheduleFile) ->
 * CoverageTracer.waitForTurnIfReplaying) is correctly reachable from new,
 * hand-picked (not observed) schedule data before running the real GA.
 *
 * Usage: java BioConcST.DebugRaceDetection <config.json> <forcedWinnerProcessId>
 */
public class DebugRaceDetection {

	public static void main(String[] args) throws Exception {
		String configPath = args[0];
		int forcedWinner = Integer.parseInt(args[1]);

		ExperimentConfig config = ExperimentConfig.load(configPath);
		BenchmarkConfig benchmark = config.benchmark;
		CoverageInstConfig ci = benchmark.coverageInst;
		File filesPath = new File(benchmark.path);

		List<RoleLink> roleLinks = new ArrayList<>();
		if (ci.roleLinks != null) {
			for (RoleLinkSpec link : ci.roleLinks) {
				roleLinks.add(new RoleLink(link.from, link.to));
			}
		}
		Topology topology = new Topology(roleLinks,
				ci.fixedMessageTargets != null ? ci.fixedMessageTargets : Map.of(),
				ci.fixedMessageSources != null ? ci.fixedMessageSources : Map.of(),
				ci.identityGroups != null ? ci.identityGroups : Map.of(),
				ci.causalDistance != null ? ci.causalDistance : Map.of());

		File workDir = new File("./cov-debug-race-" + benchmark.name);
		org.apache.commons.io.FileUtils.deleteQuietly(workDir);
		CoverageInstRun run = CoverageInstRun.prepare(filesPath, workDir);

		List<ProcessInstance> processes = new ArrayList<>();
		List<Integer> processIds = new ArrayList<>();
		for (ProcessSpec spec : benchmark.testSetupProcesses) {
			List<String> classNames = ci.extraClassesByRole != null && ci.extraClassesByRole.containsKey(spec.className)
					? ci.extraClassesByRole.get(spec.className)
					: List.of(spec.className);
			processes.add(ClassScanner.scanProcess(run.instrumentedDir(), spec.id, spec.className, classNames));
			processIds.add(spec.id);
		}

		List<RequiredEdge> required = RequiredElementsGenerator.generate(processes, topology);
		List<RacePoint> racePoints = RacePoints.detect(required);

		System.out.println("Detected " + racePoints.size() + " race point(s):");
		for (RacePoint rp : racePoints) {
			System.out.println("  destination=" + rp.destinationProcessId + " candidates=" + rp.candidateSenderIds);
			for (RequiredEdge edge : rp.relatedEdges) {
				System.out.println("    related edge: " + edge);
			}
		}
		if (racePoints.isEmpty()) {
			System.out.println("No race points detected - nothing to force, exiting.");
			return;
		}

		RacePoint racePoint = racePoints.get(0);
		List<Integer> order = new ArrayList<>();
		order.add(forcedWinner);
		for (int candidate : racePoint.candidateSenderIds) {
			if (candidate != forcedWinner) {
				order.add(candidate);
			}
		}
		String inline = racePoint.destinationProcessId + " " + String.join(",", order.stream().map(String::valueOf)
				.toList());
		System.out.println("Forcing schedule: " + inline);

		File scheduleFile = Files.createTempFile("debug-race-schedule-", ".txt").toFile();
		ReplaySchedule.fromInlineString(inline).save(scheduleFile);

		int execTimeLimitMs = benchmark.execTimeLimitMs != null ? benchmark.execTimeLimitMs
				: BenchmarkConfig.DEFAULT_EXEC_TIME_LIMIT_MS;
		List<CoverageInstRun.ProcessLaunchSpec> launchSpecs = new ArrayList<>();
		for (ProcessSpec spec : benchmark.testSetupProcesses) {
			String argsStr = spec.args;
			// Plug in mid-range values for TESTDATA placeholders - this tool
			// only cares about the race outcome, not the input-data genes.
			argsStr = argsStr.replaceAll("TESTDATA\\d*", "500");
			String[] argsArray = argsStr.isBlank() ? new String[0] : argsStr.split(" ");
			launchSpecs.add(new CoverageInstRun.ProcessLaunchSpec(spec.id, spec.className, argsArray));
		}

		CoverageInstRun.TestCaseResult result = run.runTestCase(0, launchSpecs, execTimeLimitMs, scheduleFile);
		System.out.println("allProcessesCompleted=" + result.allProcessesCompleted);

		ReplaySchedule observed = ReplaySchedule.buildFromTrace(result.coverageDir, processIds);
		List<Integer> observedOrder = observed.senderOrderFor(racePoint.destinationProcessId);
		System.out.println("Observed arrival order at destination " + racePoint.destinationProcessId + ": "
				+ observedOrder);
		boolean matches = !observedOrder.isEmpty() && observedOrder.get(0) == forcedWinner;
		System.out.println(matches ? "MATCH: forced winner arrived first, as expected."
				: "MISMATCH: forced winner did NOT arrive first - low-level mechanism is not working as expected.");
	}
}
