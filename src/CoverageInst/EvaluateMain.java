package CoverageInst;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import BioConcST.BenchmarkConfig;
import BioConcST.CoverageInstConfig;
import BioConcST.ExperimentConfig;
import BioConcST.ProcessSpec;
import BioConcST.RoleLinkSpec;

/**
 * Ties the whole prototype together: scan classes for sync points, generate
 * required edges from a benchmark config's "coverageInst" block, evaluate a
 * real run's trace logs against them, print coverage and any anomalies.
 * Generic - no per-benchmark code here.
 *
 * java CoverageInst.EvaluateMain <config.json> <compiledClassesDir> <coverageDir>
 */
public class EvaluateMain {

	public static void main(String[] args) throws IOException {
		if (args.length < 3) {
			System.err.println("Usage: EvaluateMain <config.json> <compiledClassesDir> <coverageDir>");
			System.exit(1);
		}

		ExperimentConfig config = ExperimentConfig.load(args[0]);
		BenchmarkConfig benchmark = config.benchmark;
		File classDir = new File(args[1]);
		File coverageDir = new File(args[2]);
		CoverageInstConfig ci = benchmark.coverageInst;
		if (ci == null) {
			System.err.println("benchmark.coverageInst is missing in " + args[0]);
			System.exit(1);
		}

		List<ProcessInstance> processes = new ArrayList<>();
		List<Integer> processIds = new ArrayList<>();
		for (ProcessSpec spec : benchmark.testSetupProcesses) {
			List<String> classNames = ci.extraClassesByRole != null
					&& ci.extraClassesByRole.containsKey(spec.className) ? ci.extraClassesByRole.get(spec.className)
							: List.of(spec.className);
			processes.add(ClassScanner.scanProcess(classDir, spec.id, spec.className, classNames));
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

		CoverageEvaluator.Result result = CoverageEvaluator.evaluate(required, coverageDir, processIds);

		System.out.printf("Coverage: %.2f%% (%d/%d)%n", result.coveragePercent(), result.covered.size(),
				result.totalRequired);
		System.out.println("Covered:");
		for (RequiredEdge edge : result.covered) {
			System.out.println("  [x] " + edge);
		}
		System.out.println("Uncovered:");
		for (RequiredEdge edge : result.uncovered) {
			System.out.println("  [ ] " + edge);
		}
		if (result.anomalies.isEmpty()) {
			System.out.println("Anomalies: none");
		} else {
			System.out.println("Anomalies (" + result.anomalies.size() + "):");
			for (String anomaly : result.anomalies) {
				System.out.println("  !! " + anomaly);
			}
		}
	}
}
