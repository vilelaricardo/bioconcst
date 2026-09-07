package BioConcST;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import CoverageInst.ClassScanner;
import CoverageInst.CoverageInstRun;
import CoverageInst.ProcessInstance;
import CoverageInst.RacePoint;
import CoverageInst.RacePoints;
import CoverageInst.RequiredEdge;
import CoverageInst.RequiredElementsGenerator;
import CoverageInst.RoleLink;
import CoverageInst.Topology;
import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;

/**
 * One-off diagnostic: for a FIXED pair of Peer input values, evaluates the
 * SAME genotype twice - once with the race gene forcing Peer1 first, once
 * forcing Peer2 first - and prints both TestFitness results side by side.
 * Used to test the hypothesis (raised after raceMutationRate=0.1 AND 0.3
 * both converged 16/16 champions to the exact same race-gene value across
 * every pilot execution) that there's a real, reproducible ASYMMETRY in how
 * GraphDistance/fitness scores the two arrival orders - not just an
 * exploration/mutation-rate problem.
 *
 * Usage: java BioConcST.DebugRaceFitnessComparison <config.json> <peer1Value> <peer2Value>
 */
public class DebugRaceFitnessComparison {

	public static void main(String[] args) throws Exception {
		String configPath = args[0];
		int peer1Value = Integer.parseInt(args[1]);
		int peer2Value = Integer.parseInt(args[2]);

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
				ci.chainedDistance != null ? ci.chainedDistance : Map.of());

		File workDir = new File("./cov-debug-race-fitness-" + benchmark.name);
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
		System.out.println("Race points: " + racePoints.size());

		int execTimeLimitMs = benchmark.execTimeLimitMs != null ? benchmark.execTimeLimitMs
				: BenchmarkConfig.DEFAULT_EXEC_TIME_LIMIT_MS;
		int inputGeneCount = 2;
		CoverageInstFitnessFunction fitnessFn = new CoverageInstFitnessFunction(run, benchmark.testSetupProcesses,
				required, processIds, execTimeLimitMs, racePoints, inputGeneCount);

		for (int raceGeneValue = 0; raceGeneValue <= 1; raceGeneValue++) {
			Genotype<IntegerGene> genotype = Genotype.of(
					IntegerChromosome.of(IntegerGene.of(peer1Value, 0, 1000)),
					IntegerChromosome.of(IntegerGene.of(peer2Value, 0, 1000)),
					IntegerChromosome.of(IntegerGene.of(raceGeneValue, 0, 2)));
			TestFitness fitness = fitnessFn.evaluate(genotype);
			System.out.println("raceGene=" + raceGeneValue + " (winner=" + racePoints.get(0).candidateSenderIds.get(raceGeneValue)
					+ ") -> distance=" + fitness.getDistance() + " coverage=" + fitness.getCoverage()
					+ " replaySchedule=" + fitness.getReplaySchedule() + " syncEdgeRequirements="
					+ fitness.getSyncEdgeRequirements());
		}
	}
}
