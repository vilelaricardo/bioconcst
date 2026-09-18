package CoverageInst;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import BioConcST.BenchmarkConfig;
import BioConcST.CoverageInstConfig;
import BioConcST.ExperimentConfig;
import BioConcST.ProcessSpec;
import BioConcST.ReplayBundle;
import BioConcST.RoleLinkSpec;

/**
 * Answers, for a completed GA search's exported champion bundles, whether
 * each remaining uncovered MESSAGE edge at an ambiguous receive point
 * (RacePoints.detect) is closeable by SOME sender arrival order at all -
 * distinguishing "scheduling non-determinism the GA never happened to hit"
 * from "structurally unreachable even under total control of arrival
 * order". GraphDistance alone cannot tell these apart: its identity-based
 * correlation collapses to 0.0 distance as soon as both sides of a race
 * have already been physically reached, so there is no gradient left to
 * follow toward "try a different arrival order" (see
 * debug-geracao-quorum-handshake.md). Rather than ask the GA to *guess* the
 * right order via an evolved gene (the raceGene approach), this tool forces
 * every relevant order deterministically and observes what got covered -
 * no LLM, no ambiguity, since the space of orders to try is fully enumerable
 * from data RacePoints.detect already computes.
 *
 * Runs strictly AFTER a search, over already-captured "-replay.json"
 * bundles (CoverageInstStrategy.captureReplayBundles) - it never touches
 * CoverageInstStrategy/CoverageInstFitnessFunction/GraphDistance/
 * CoverageTracer/ReplaySchedule. For each bundle and each RacePoint with a
 * still-uncovered related edge, it tries every (or, when K! exceeds
 * permutationCap, a random sample of) permutation of that RacePoint's
 * candidate senders by writing a single-destination replay-schedule.txt and
 * re-running the exact same test input under CoverageInstRun's controlled
 * mode - the same mechanism ReplayMain already uses to reproduce a recorded
 * schedule, just applied here to schedules nobody has observed yet.
 *
 * java CoverageInst.CoverageSweepMain <config.json> <bundles.json> [permutationCap=40]
 */
public class CoverageSweepMain {

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length < 2) {
			System.err.println("Usage: CoverageSweepMain <config.json> <bundles.json> [permutationCap=40]");
			System.exit(1);
		}
		int permutationCap = args.length > 2 ? Integer.parseInt(args[2]) : 40;

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

		File workDir = new File("./cov-sweep-" + benchmark.name);
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
		List<RacePoint> racePoints = RacePoints.detect(required);

		Set<String> raceEdgeKeys = new LinkedHashSet<>();
		for (RacePoint racePoint : racePoints) {
			for (RequiredEdge edge : racePoint.relatedEdges) {
				raceEdgeKeys.add(edge.toString());
			}
		}

		int execTimeLimitMs = benchmark.execTimeLimitMs != null ? benchmark.execTimeLimitMs
				: BenchmarkConfig.DEFAULT_EXEC_TIME_LIMIT_MS;
		File scheduleFile = new File(workDir, "replay-schedule.txt");

		// First, recompute what the GA bundles cover on their own, fresh
		// against the CURRENT `required` set (not the bundle's own recorded
		// coveredCount, which could be stale relative to a config that
		// changed since the search ran) - each bundle replayed once under
		// its own recorded schedule, exactly like ReplayMain does.
		Set<String> coveredByGA = new LinkedHashSet<>();
		for (ReplayBundle bundle : bundles) {
			ReplaySchedule.fromInlineString(bundle.replaySchedule).save(scheduleFile);
			CoverageInstRun.TestCaseResult result = run.runTestCase(0, launchSpecsOf(bundle), execTimeLimitMs,
					scheduleFile);
			if (!result.allProcessesCompleted) {
				System.err.println(
						"WARNING: bundle genotype=" + Arrays.toString(bundle.genotype)
								+ " timed out re-running its own recorded schedule; skipping");
				continue;
			}
			CoverageEvaluator.Result evalResult = CoverageEvaluator.evaluate(required, result.coverageDir, processIds);
			for (RequiredEdge edge : evalResult.covered) {
				coveredByGA.add(edge.toString());
			}
		}

		System.out.printf("GA-only coverage: %d/%d edges%n", coveredByGA.size(), required.size());

		Set<String> coveredAfterSweep = new LinkedHashSet<>(coveredByGA);
		Map<String, String> closingScheduleByEdge = new LinkedHashMap<>();

		for (RacePoint racePoint : racePoints) {
			Set<String> stillUncovered = new LinkedHashSet<>();
			for (RequiredEdge edge : racePoint.relatedEdges) {
				if (!coveredAfterSweep.contains(edge.toString())) {
					stillUncovered.add(edge.toString());
				}
			}
			if (stillUncovered.isEmpty()) {
				continue;
			}

			List<String> schedules = RaceScheduleSweep.candidateSchedules(racePoint, permutationCap);

			outer: for (ReplayBundle bundle : bundles) {
				for (String scheduleLine : schedules) {
					ReplaySchedule.fromInlineString(scheduleLine).save(scheduleFile);
					CoverageInstRun.TestCaseResult result = run.runTestCase(0, launchSpecsOf(bundle), execTimeLimitMs,
							scheduleFile);
					if (!result.allProcessesCompleted) {
						continue;
					}
					CoverageEvaluator.Result evalResult = CoverageEvaluator.evaluate(required, result.coverageDir,
							processIds);
					for (RequiredEdge edge : evalResult.covered) {
						String key = edge.toString();
						if (stillUncovered.remove(key)) {
							coveredAfterSweep.add(key);
							closingScheduleByEdge.put(key,
									"dest=" + racePoint.destinationProcessId + " order=" + scheduleLine);
						}
					}
					if (stillUncovered.isEmpty()) {
						break outer;
					}
				}
			}
		}

		System.out.printf("GA + deterministic sweep coverage: %d/%d edges%n", coveredAfterSweep.size(),
				required.size());
		System.out.println();
		System.out.println("Edges closed by the sweep (uncovered by any GA bundle alone):");
		for (Map.Entry<String, String> entry : closingScheduleByEdge.entrySet()) {
			System.out.println("  " + entry.getKey() + "  <-  " + entry.getValue());
		}

		List<String> unreachableRaceEdges = new ArrayList<>();
		List<String> unreachableNonRaceEdges = new ArrayList<>();
		for (RequiredEdge edge : required) {
			String key = edge.toString();
			if (coveredAfterSweep.contains(key)) {
				continue;
			}
			(raceEdgeKeys.contains(key) ? unreachableRaceEdges : unreachableNonRaceEdges).add(key);
		}

		System.out.println();
		System.out.println("Race-related edges STILL uncovered after exhausting every candidate arrival order"
				+ " (structurally unreachable candidates, not scheduling non-determinism):");
		unreachableRaceEdges.forEach(edge -> System.out.println("  " + edge));

		System.out.println();
		System.out.println("Non-race edges left uncovered (out of scope for this sweep - a data/distance"
				+ " problem, not an arrival-order problem):");
		unreachableNonRaceEdges.forEach(edge -> System.out.println("  " + edge));

		Map<String, Object> report = new LinkedHashMap<>();
		report.put("totalRequired", required.size());
		report.put("gaOnlyCovered", coveredByGA.size());
		report.put("sweepCovered", coveredAfterSweep.size());
		report.put("closedBySweep", closingScheduleByEdge);
		report.put("stillUncoveredRaceEdges", unreachableRaceEdges);
		report.put("stillUncoveredNonRaceEdges", unreachableNonRaceEdges);
		File reportFile = new File(args[1].replaceAll("\\.json$", "") + "-sweep-report.json");
		new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportFile, report);
		System.out.println();
		System.out.println("Report written to " + reportFile.getPath());
	}

	private static List<CoverageInstRun.ProcessLaunchSpec> launchSpecsOf(ReplayBundle bundle) {
		List<CoverageInstRun.ProcessLaunchSpec> launchSpecs = new ArrayList<>();
		for (ReplayBundle.ProcessLaunch process : bundle.processes) {
			launchSpecs.add(new CoverageInstRun.ProcessLaunchSpec(process.id, process.className, process.args));
		}
		return launchSpecs;
	}
}
