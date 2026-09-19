package BioConcST;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import CoverageInst.CoverageInstRun;
import CoverageInst.RequiredElementsGenerator;
import CoverageInst.RoleLink;
import CoverageInst.Topology;
import CoverageInst.ClassScanner;
import CoverageInst.ProcessInstance;
import CoverageInst.RequiredEdge;
import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;

/**
 * Evaluates one fixed, hand-picked genotype against a GA_COVINST config
 * with per-edge distance diagnostics on ({@code coverage.debugEdgeDistances}),
 * bypassing the genetic algorithm entirely - the same one-off harness used
 * throughout this project's causalDistance/FlagResolver debugging sessions
 * (raft-election, two-phase-commit, paxos, the RIP distance-vector wave),
 * generalized here into a permanent, reproducible tool instead of living
 * only in a scratchpad. Prints one EDGE_DEBUG line per currently-uncovered
 * required edge (see CoverageInstFitnessFunction), plus the aggregate
 * distance/coverage - exactly what a smoke case or a C-vs-D replay
 * comparison needs to report per-edge components, not just the aggregate
 * fitness.
 *
 * java BioConcST.DebugSingleEvaluation &lt;config.json&gt; &lt;gene0&gt; [gene1 ...]
 */
public class DebugSingleEvaluation {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: DebugSingleEvaluation <config.json> <gene0> [gene1 ...]");
            System.exit(1);
        }
        System.setProperty("coverage.debugEdgeDistances", "true");
        ExperimentConfig config = ExperimentConfig.load(args[0]);
        BenchmarkConfig benchmark = config.benchmark;
        CoverageInstConfig ci = benchmark.coverageInst;

        File workDir = new File("/tmp/debug-single-evaluation-work");
        org.apache.commons.io.FileUtils.deleteQuietly(workDir);
        CoverageInstRun run = CoverageInstRun.prepare(new File(benchmark.path), workDir);

        List<ProcessInstance> processes = new ArrayList<>();
        List<Integer> processIds = new ArrayList<>();
        for (ProcessSpec spec : benchmark.testSetupProcesses) {
            List<String> classNames = ci.extraClassesByRole != null && ci.extraClassesByRole.containsKey(spec.className)
                    ? ci.extraClassesByRole.get(spec.className) : List.of(spec.className);
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

        int execTimeLimitMs = benchmark.execTimeLimitMs != null ? benchmark.execTimeLimitMs
                : BenchmarkConfig.DEFAULT_EXEC_TIME_LIMIT_MS;
        CoverageInstFitnessFunction ff = new CoverageInstFitnessFunction(run, benchmark.testSetupProcesses, required,
                processIds, execTimeLimitMs, List.of(), benchmark.argumentsLength, topology.causalDistance);

        List<Integer> genes = new ArrayList<>();
        List<IntegerChromosome> chroms = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            int value = Integer.parseInt(args[i]);
            genes.add(value);
            chroms.add(IntegerChromosome.of(IntegerGene.of(value, benchmark.argumentRanges.get(i - 1).min,
                    benchmark.argumentRanges.get(i - 1).max)));
        }
        Genotype<IntegerGene> genotype = Genotype.of(chroms);

        System.out.println("genotype=" + genes.stream().map(String::valueOf).collect(Collectors.joining(",")));
        TestFitness fitness = ff.evaluate(genotype);
        System.out.println("distance=" + fitness.getDistance() + " coverage=" + fitness.getCoverage());
    }
}
