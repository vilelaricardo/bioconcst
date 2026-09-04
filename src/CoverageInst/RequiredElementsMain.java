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
 * Demo/CLI: scans a benchmark's classes for sync points and prints the
 * required-elements set generated from its config's "coverageInst" block -
 * the CoverageInst equivalent of `valipar elem`. Genuinely generic: works
 * for any benchmark config, no per-benchmark code here.
 *
 * java CoverageInst.RequiredElementsMain <config.json> <compiledClassesDir>
 */
public class RequiredElementsMain {

	public static void main(String[] args) throws IOException {
		if (args.length < 2) {
			System.err.println("Usage: RequiredElementsMain <config.json> <compiledClassesDir>");
			System.exit(1);
		}

		ExperimentConfig config = ExperimentConfig.load(args[0]);
		BenchmarkConfig benchmark = config.benchmark;
		File classDir = new File(args[1]);
		CoverageInstConfig ci = benchmark.coverageInst;
		if (ci == null) {
			System.err.println("benchmark.coverageInst is missing in " + args[0]);
			System.exit(1);
		}

		List<ProcessInstance> processes = new ArrayList<>();
		for (ProcessSpec spec : benchmark.testSetupProcesses) {
			List<String> classNames = ci.extraClassesByRole != null
					&& ci.extraClassesByRole.containsKey(spec.className) ? ci.extraClassesByRole.get(spec.className)
							: List.of(spec.className);
			ProcessInstance process = ClassScanner.scanProcess(classDir, spec.id, spec.className, classNames);
			processes.add(process);
			System.out.println("p" + spec.id + " (" + spec.className + "): " + process.syncPoints);
		}

		List<RoleLink> roleLinks = new ArrayList<>();
		if (ci.roleLinks != null) {
			for (RoleLinkSpec link : ci.roleLinks) {
				roleLinks.add(new RoleLink(link.from, link.to));
			}
		}
		Topology topology = new Topology(roleLinks, ci.fixedMessageTargets != null ? ci.fixedMessageTargets : Map.of(),
				ci.fixedMessageSources != null ? ci.fixedMessageSources : Map.of(),
				ci.identityGroups != null ? ci.identityGroups : Map.of());

		List<RequiredEdge> required = RequiredElementsGenerator.generate(processes, topology);
		System.out.println();
		System.out.println("Required edges (" + required.size() + "):");
		for (RequiredEdge edge : required) {
			System.out.println("  " + edge);
		}
	}
}
