package BioConcST;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import CoverageInst.CoverageEvaluator;
import CoverageInst.CoverageInstRun;
import CoverageInst.GraphDistance;
import CoverageInst.GraphDistance.ChainedSource;
import CoverageInst.RacePoint;
import CoverageInst.ReplaySchedule;
import CoverageInst.RequiredEdge;
import io.jenetics.Chromosome;
import io.jenetics.Genotype;
import io.jenetics.IntegerGene;

/**
 * CoverageInst's equivalent of FitnessFunction: turns one individual's
 * genotype into a real, isolated, native execution (no ValiPar, no Docker)
 * and reads back a TestFitness from CoverageEvaluator's result.
 *
 * The distance formula mirrors the real ValiPar path's
 * FitnessFunction/DistanceElem exactly (sum of per-uncovered-required-edge
 * graph distances, divided by the TOTAL required element count): CoverageInst
 * now has its own real PCFG-equivalent (CoverageInst.ControlFlowGraph, built
 * from bytecode at basic-block granularity - see BasicBlockInstrumenter),
 * computed once by CoverageInstRun.prepare() and reused for every individual
 * via GraphDistance.
 */
public final class CoverageInstFitnessFunction {

	private final CoverageInstRun run;
	private final List<ProcessSpec> testSetupProcesses;
	private final List<RequiredEdge> required;
	private final List<Integer> processIds;
	private final int execTimeLimitMs;
	private final List<RacePoint> racePoints;
	private final int inputGeneCount;
	private final Map<String, List<ChainedSource>> chainedDistance;
	private final AtomicInteger testIdCounter = new AtomicInteger(0);

	public CoverageInstFitnessFunction(CoverageInstRun run, List<ProcessSpec> testSetupProcesses,
			List<RequiredEdge> required, List<Integer> processIds, int execTimeLimitMs) {
		this(run, testSetupProcesses, required, processIds, execTimeLimitMs, List.of(), 0, Map.of());
	}

	/**
	 * racePoints/inputGeneCount are empty/0 for every benchmark that doesn't
	 * opt into coverageInst.raceGene - see CoverageInstStrategy, the only
	 * caller. inputGeneCount marks where the input-data chromosomes end and
	 * the race-choice chromosomes (one per racePoints element, appended in
	 * the same order) begin in the flat Genotype - needed so
	 * buildLaunchSpecs never substitutes a race gene's value into a
	 * TESTDATA placeholder.
	 */
	public CoverageInstFitnessFunction(CoverageInstRun run, List<ProcessSpec> testSetupProcesses,
			List<RequiredEdge> required, List<Integer> processIds, int execTimeLimitMs, List<RacePoint> racePoints,
			int inputGeneCount) {
		this(run, testSetupProcesses, required, processIds, execTimeLimitMs, racePoints, inputGeneCount, Map.of());
	}

	/**
	 * chainedDistance is empty for every benchmark that doesn't declare
	 * coverageInst.chainedDistance - see Topology's own javadoc and
	 * GraphDistance.compute's javadoc for the full mechanism (cross-process
	 * "flag problem" gradient for a MESSAGE edge with no local numeric
	 * predicate of its own).
	 */
	public CoverageInstFitnessFunction(CoverageInstRun run, List<ProcessSpec> testSetupProcesses,
			List<RequiredEdge> required, List<Integer> processIds, int execTimeLimitMs, List<RacePoint> racePoints,
			int inputGeneCount, Map<String, List<ChainedSource>> chainedDistance) {
		this.run = run;
		this.testSetupProcesses = testSetupProcesses;
		this.required = required;
		this.processIds = processIds;
		this.execTimeLimitMs = execTimeLimitMs;
		this.racePoints = racePoints;
		this.inputGeneCount = inputGeneCount;
		this.chainedDistance = chainedDistance;
	}

	/**
	 * TESTDATA-substitution shared with the capture-and-export step
	 * (CoverageInstStrategy, after the search finishes) so a champion
	 * individual's exported test input is built the exact same way it was
	 * built during the search that found it - not a second, potentially
	 * drifting implementation of the same substitution rule.
	 *
	 * inputGeneCount caps how many leading genes count as real test-input
	 * data - any trailing race-choice genes (see RacePoints.detect) must
	 * never leak into a benchmark's TESTDATA/TESTDATA&lt;n&gt; args.
	 */
	public static List<CoverageInstRun.ProcessLaunchSpec> buildLaunchSpecs(Genotype<IntegerGene> genotype,
			List<ProcessSpec> testSetupProcesses, int inputGeneCount) {
		List<String> geneValues = new ArrayList<>();
		int seen = 0;
		outer: for (Chromosome<IntegerGene> chromosome : genotype) {
			for (IntegerGene gene : chromosome) {
				if (seen >= inputGeneCount) {
					break outer;
				}
				geneValues.add(String.valueOf(gene.allele()));
				seen++;
			}
		}
		String joined = String.join(" ", geneValues);

		List<CoverageInstRun.ProcessLaunchSpec> launchSpecs = new ArrayList<>();
		for (ProcessSpec spec : testSetupProcesses) {
			String argsStr = spec.args;
			for (int g = 0; g < geneValues.size(); g++) {
				argsStr = argsStr.replace("TESTDATA" + g, geneValues.get(g));
			}
			argsStr = argsStr.replace("TESTDATA", joined);
			String[] argsArray = argsStr.isBlank() ? new String[0] : argsStr.split(" ");
			launchSpecs.add(new CoverageInstRun.ProcessLaunchSpec(spec.id, spec.className, argsArray));
		}
		return launchSpecs;
	}

	public TestFitness evaluate(Genotype<IntegerGene> genotype) {
		int testId = testIdCounter.getAndIncrement();
		List<CoverageInstRun.ProcessLaunchSpec> launchSpecs = buildLaunchSpecs(genotype, testSetupProcesses,
				inputGeneCount);

		try {
			java.io.File scheduleFile = null;
			if (!racePoints.isEmpty()) {
				String inline = RaceGeneSupport.toScheduleInlineString(racePoints, genotype, inputGeneCount);
				scheduleFile = Files.createTempFile("race-schedule-" + testId + "-", ".txt").toFile();
				ReplaySchedule.fromInlineString(inline).save(scheduleFile);
			}
			CoverageInstRun.TestCaseResult result = scheduleFile != null
					? run.runTestCase(testId, launchSpecs, execTimeLimitMs, scheduleFile)
					: run.runTestCase(testId, launchSpecs, execTimeLimitMs);
			if (!result.allProcessesCompleted) {
				// Matches FitnessFunction's convention on the ValiPar path:
				// a missing/incomplete execution (here, a process that had
				// to be force-killed after timing out - a deadlocked
				// genotype, most likely) is treated as maximum distance for
				// this individual, not a crash.
				return maxDistanceFitness();
			}
			CoverageEvaluator.Result evalResult = CoverageEvaluator.evaluate(required, result.coverageDir,
					processIds);
			// Captured from THIS SAME run's own trace, right now, while
			// result.coverageDir still reflects the exact execution that's
			// about to be scored - see TestFitness's own javadoc for why
			// this can't be done later (e.g. after the search ends, on a
			// champion) without silently reproducing a different,
			// independently-raced execution instead.
			ReplaySchedule schedule = ReplaySchedule.buildFromTrace(result.coverageDir, processIds);
			return toTestFitness(evalResult, schedule.toInlineString());
		} catch (IOException | InterruptedException e) {
			return maxDistanceFitness();
		}
	}

	private TestFitness toTestFitness(CoverageEvaluator.Result result, String replaySchedule) {
		double distance = 0.0;
		if (result.totalRequired > 0) {
			double sum = 0.0;
			for (RequiredEdge edge : result.uncovered) {
				sum += GraphDistance.compute(edge, run.flowGraphs(), run.syncEdgeBlocks(), run.branchPredicates(),
						result.observedNodesByProcess, result.observedOperandsByProcess,
						result.observedSenderByReceiveEdge, chainedDistance);
			}
			distance = sum / result.totalRequired;
		}
		return new TestFitness(distance, result.coveragePercent(), toRequiredElementsJson(result), replaySchedule);
	}

	private String toRequiredElementsJson(CoverageEvaluator.Result result) {
		StringBuilder json = new StringBuilder("[");
		for (int i = 0; i < required.size(); i++) {
			if (i > 0) {
				json.append(",");
			}
			boolean covered = result.covered.contains(required.get(i));
			json.append("{\"state\":\"").append(covered ? "COVERED" : "UNCOVERED").append("\"}");
		}
		return json.append("]").toString();
	}

	private TestFitness maxDistanceFitness() {
		return new TestFitness(1.0, 0.0, "[]");
	}
}
