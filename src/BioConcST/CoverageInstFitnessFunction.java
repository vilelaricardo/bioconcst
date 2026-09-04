package BioConcST;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import CoverageInst.CoverageEvaluator;
import CoverageInst.CoverageInstRun;
import CoverageInst.GraphDistance;
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
	private final AtomicInteger testIdCounter = new AtomicInteger(0);

	public CoverageInstFitnessFunction(CoverageInstRun run, List<ProcessSpec> testSetupProcesses,
			List<RequiredEdge> required, List<Integer> processIds, int execTimeLimitMs) {
		this.run = run;
		this.testSetupProcesses = testSetupProcesses;
		this.required = required;
		this.processIds = processIds;
		this.execTimeLimitMs = execTimeLimitMs;
	}

	/**
	 * TESTDATA-substitution shared with the capture-and-export step
	 * (CoverageInstStrategy, after the search finishes) so a champion
	 * individual's exported test input is built the exact same way it was
	 * built during the search that found it - not a second, potentially
	 * drifting implementation of the same substitution rule.
	 */
	public static List<CoverageInstRun.ProcessLaunchSpec> buildLaunchSpecs(Genotype<IntegerGene> genotype,
			List<ProcessSpec> testSetupProcesses) {
		List<String> geneValues = new ArrayList<>();
		for (Chromosome<IntegerGene> chromosome : genotype) {
			for (IntegerGene gene : chromosome) {
				geneValues.add(String.valueOf(gene.allele()));
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
		List<CoverageInstRun.ProcessLaunchSpec> launchSpecs = buildLaunchSpecs(genotype, testSetupProcesses);

		try {
			CoverageInstRun.TestCaseResult result = run.runTestCase(testId, launchSpecs, execTimeLimitMs);
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
						result.observedNodesByProcess, result.observedOperandsByProcess);
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
