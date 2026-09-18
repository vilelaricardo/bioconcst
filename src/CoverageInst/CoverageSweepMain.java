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
 * For a completed search's exported replay bundles, tries every (or, when
 * K! exceeds a cap, a random sample of) sender-arrival-order permutation of
 * each RacePoint's candidates and reports which required edges were
 * observed covered under some tested (genotype, forced schedule) pair.
 *
 * This tool does NOT establish that an edge left uncovered here is
 * structurally unreachable - only that it was not covered under the
 * specific inputs (the exported bundles) and arrival orders actually
 * tried. Precisely, it does NOT explore:
 * - new genotypes beyond the ones already present in the exported bundles;
 * - the full K! space when K! exceeds permutationCap (a random sample is
 *   tried instead - see the exhaustiveEnumeration flag in the report);
 * - combinations of arrival orders across more than one destination at a
 *   time (every RacePoint besides the one currently being swept, and every
 *   non-race destination, runs unconstrained during a given attempt);
 * - repeated sends from the same sender to the same destination beyond
 *   what a bundle's own recorded process launch already produces.
 * An attempt where a process has to be force-killed (timeout) is counted
 * as inconclusive, not as evidence that order fails. "Initial coverage" is
 * the union of the bundles actually replayed here, which is not
 * necessarily identical to whatever cumulative coverage a live search run
 * reported (bundles can fail to reproduce their own recorded schedule in a
 * different environment - see the WARNING lines and initialCoverageTimeouts
 * in the report).
 *
 * Runs strictly AFTER a search, over already-captured "-replay.json"
 * bundles (CoverageInstStrategy.captureReplayBundles) - it never touches
 * CoverageInstStrategy/CoverageInstFitnessFunction/GraphDistance/
 * CoverageTracer/ReplaySchedule. For each bundle and each RacePoint not yet
 * fully covered, it tries candidate schedules from RaceScheduleSweep by
 * writing a single-destination replay-schedule.txt and re-running the
 * exact same test input under CoverageInstRun's controlled mode - the same
 * mechanism ReplayMain already uses to reproduce a recorded schedule, just
 * applied here to schedules nobody has observed yet. Every edge observed
 * covered during any attempt is credited immediately to a single running
 * total, independently of which RacePoint's own target set is currently
 * being checked for a stopping condition - an earlier version of this tool
 * discarded such incidental coverage.
 *
 * java CoverageInst.CoverageSweepMain <config.json> <bundles.json> [permutationCap=40]
 */
public class CoverageSweepMain {

	static final class Witness {
		final int[] genotype;
		final String schedule;
		final int destinationProcessId;

		Witness(int[] genotype, String schedule, int destinationProcessId) {
			this.genotype = genotype;
			this.schedule = schedule;
			this.destinationProcessId = destinationProcessId;
		}
	}

	/**
	 * The accumulation step a single sweep attempt performs, isolated from
	 * process execution so it can be unit-tested directly: credits every
	 * edge in {@code edgesCoveredThisAttempt} to {@code coveredSoFar}
	 * (recording a witness the first time each is seen) regardless of
	 * whether that edge belongs to {@code ownEdges} - the RacePoint
	 * currently being swept - and separately reports whether ownEdges are
	 * now fully covered, which is the caller's own stopping condition.
	 * An earlier version only credited edges found inside ownEdges,
	 * silently discarding coverage of any other edge an attempt happened
	 * to also produce.
	 */
	static boolean recordAttempt(Set<String> coveredSoFar, Map<String, Witness> witnesses,
			Set<String> edgesCoveredThisAttempt, List<RequiredEdge> ownEdges, int[] genotype, String scheduleLine,
			int destinationProcessId) {
		for (String key : edgesCoveredThisAttempt) {
			if (coveredSoFar.add(key)) {
				witnesses.put(key, new Witness(genotype, scheduleLine, destinationProcessId));
			}
		}
		return ownEdges.stream().allMatch(e -> coveredSoFar.contains(e.toString()));
	}

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

		// Coverage of THESE bundles as replayed HERE - not necessarily
		// identical to whatever cumulative coverage the original live
		// search run reported (see class javadoc).
		Set<String> coveredByBundlesAlone = new LinkedHashSet<>();
		int initialTimeouts = 0;
		for (ReplayBundle bundle : bundles) {
			ReplaySchedule.fromInlineString(bundle.replaySchedule).save(scheduleFile);
			CoverageInstRun.TestCaseResult result = run.runTestCase(0, launchSpecsOf(bundle), execTimeLimitMs,
					scheduleFile);
			if (!result.allProcessesCompleted) {
				initialTimeouts++;
				System.err.println(
						"WARNING: bundle genotype=" + Arrays.toString(bundle.genotype)
								+ " timed out re-running its own recorded schedule; skipping");
				continue;
			}
			CoverageEvaluator.Result evalResult = CoverageEvaluator.evaluate(required, result.coverageDir, processIds);
			for (RequiredEdge edge : evalResult.covered) {
				coveredByBundlesAlone.add(edge.toString());
			}
		}

		System.out.printf("Initial coverage (replayed bundles only, %d/%d timed out): %d/%d edges%n",
				initialTimeouts, bundles.size(), coveredByBundlesAlone.size(), required.size());

		// Single running total: every edge observed covered by any attempt
		// below is credited here immediately, regardless of which
		// RacePoint is currently being swept.
		Set<String> coveredAfterSweep = new LinkedHashSet<>(coveredByBundlesAlone);
		Map<String, Witness> closingWitnessByEdge = new LinkedHashMap<>();
		List<Map<String, Object>> racePointDiagnostics = new ArrayList<>();

		for (RacePoint racePoint : racePoints) {
			List<RequiredEdge> ownEdges = racePoint.relatedEdges;
			if (ownEdges.stream().allMatch(e -> coveredAfterSweep.contains(e.toString()))) {
				continue;
			}

			int candidateCount = racePoint.candidateSenderIds.size();
			boolean exhaustive = RaceScheduleSweep.isExhaustive(candidateCount, permutationCap);
			List<String> schedules = RaceScheduleSweep.candidateSchedules(racePoint, permutationCap);

			int attempts = 0;
			int completions = 0;
			int timeouts = 0;
			int sizeBefore = coveredAfterSweep.size();

			outer: for (ReplayBundle bundle : bundles) {
				for (String scheduleLine : schedules) {
					attempts++;
					ReplaySchedule.fromInlineString(scheduleLine).save(scheduleFile);
					CoverageInstRun.TestCaseResult result = run.runTestCase(0, launchSpecsOf(bundle), execTimeLimitMs,
							scheduleFile);
					if (!result.allProcessesCompleted) {
						timeouts++;
						continue;
					}
					completions++;
					CoverageEvaluator.Result evalResult = CoverageEvaluator.evaluate(required, result.coverageDir,
							processIds);
					Set<String> coveredThisAttempt = new LinkedHashSet<>();
					for (RequiredEdge edge : evalResult.covered) {
						coveredThisAttempt.add(edge.toString());
					}
					boolean racePointClosed = recordAttempt(coveredAfterSweep, closingWitnessByEdge,
							coveredThisAttempt, ownEdges, bundle.genotype, scheduleLine,
							racePoint.destinationProcessId);
					if (racePointClosed) {
						break outer;
					}
				}
			}
			int newEdgesClosed = coveredAfterSweep.size() - sizeBefore;

			Map<String, Object> diag = new LinkedHashMap<>();
			diag.put("destinationProcessId", racePoint.destinationProcessId);
			diag.put("candidateSenderIds", racePoint.candidateSenderIds);
			diag.put("relatedEdgeCount", ownEdges.size());
			diag.put("permutationCap", permutationCap);
			diag.put("exhaustiveEnumeration", exhaustive);
			diag.put("schedulesTriedPerBundle", schedules.size());
			diag.put("attempts", attempts);
			diag.put("completions", completions);
			diag.put("timeouts", timeouts);
			diag.put("newEdgesClosed", newEdgesClosed);
			racePointDiagnostics.add(diag);
		}

		System.out.printf("Coverage after the sweep: %d/%d edges%n", coveredAfterSweep.size(), required.size());
		System.out.println();
		System.out.println("Edges newly covered during the sweep (not covered by any replayed bundle alone),"
				+ " each with a witness (genotype + forced schedule):");
		for (Map.Entry<String, Witness> entry : closingWitnessByEdge.entrySet()) {
			Witness w = entry.getValue();
			System.out.println("  " + entry.getKey() + "  <-  genotype=" + Arrays.toString(w.genotype) + " dest="
					+ w.destinationProcessId + " order=" + w.schedule);
		}

		List<String> uncoveredRaceEdges = new ArrayList<>();
		List<String> uncoveredNonRaceEdges = new ArrayList<>();
		for (RequiredEdge edge : required) {
			String key = edge.toString();
			if (coveredAfterSweep.contains(key)) {
				continue;
			}
			(raceEdgeKeys.contains(key) ? uncoveredRaceEdges : uncoveredNonRaceEdges).add(key);
		}

		System.out.println();
		System.out.println("RacePoint edges NOT covered under the inputs and orders tested here - this does"
				+ " NOT establish they are unreachable under other inputs, a larger permutation sample, or"
				+ " combinations across destinations (see racePointDiagnostics in the report for exactly"
				+ " what was and was not tried):");
		uncoveredRaceEdges.forEach(edge -> System.out.println("  " + edge));

		System.out.println();
		System.out.println("Edges outside any detected RacePoint, left uncovered - this sweep does not"
				+ " attempt these and makes no claim about why they are uncovered:");
		uncoveredNonRaceEdges.forEach(edge -> System.out.println("  " + edge));

		Map<String, Object> report = new LinkedHashMap<>();
		report.put("configFile", args[0]);
		report.put("bundlesFile", args[1]);
		report.put("bundleCount", bundles.size());
		report.put("permutationCap", permutationCap);
		report.put("totalRequired", required.size());
		report.put("initialCoverage", coveredByBundlesAlone.size());
		report.put("initialCoverageTimeouts", initialTimeouts);
		report.put("coverageAfterSweep", coveredAfterSweep.size());
		Map<String, Map<String, Object>> witnessOut = new LinkedHashMap<>();
		for (Map.Entry<String, Witness> entry : closingWitnessByEdge.entrySet()) {
			Witness w = entry.getValue();
			Map<String, Object> w2 = new LinkedHashMap<>();
			w2.put("genotype", w.genotype);
			w2.put("schedule", w.schedule);
			w2.put("destinationProcessId", w.destinationProcessId);
			witnessOut.put(entry.getKey(), w2);
		}
		report.put("newlyCoveredBySweep", witnessOut);
		report.put("uncoveredRaceEdgesNotEstablishedUnreachable", uncoveredRaceEdges);
		report.put("uncoveredNonRaceEdges", uncoveredNonRaceEdges);
		report.put("racePointDiagnostics", racePointDiagnostics);
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
