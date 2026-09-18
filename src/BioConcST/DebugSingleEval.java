package BioConcST;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ValiPar.ValiParRun;
import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;

/**
 * One-off tool: evaluates a single, fixed test data vector against a
 * benchmark and leaves ./experiment/test0/valipar/required_elements/ on disk
 * (skips the normal compressResults()/rm -rf cleanup) so the raw per-element
 * sync_edge_requirements.json can be inspected directly - used to find out
 * WHICH specific requirement never gets covered, instead of guessing from
 * the aggregate coverage percentage alone.
 */
public class DebugSingleEval {

	public static void main(String[] args) throws Exception {
		String configPath = args[0];
		int gene0 = Integer.parseInt(args[1]);
		int gene1 = Integer.parseInt(args[2]);

		ExperimentConfig config = ExperimentConfig.load(configPath);
		BenchmarkConfig benchmark = config.benchmark;

		File filesPath = new File(benchmark.path);

		List<String> command = new ArrayList<>(List.of("valipar", "inst", "-t", "-l", "-p"));
		command.addAll(benchmark.processes);
		command.add("-f");
		command.addAll(benchmark.parseFiles);
		if (benchmark.ignoreFiles != null && !benchmark.ignoreFiles.isEmpty()) {
			command.add("-i");
			command.addAll(benchmark.ignoreFiles);
		}
		ProcessBuilder instrumentation = new ProcessBuilder(command);

		List<String> setupCmd = new ArrayList<>(List.of("valipar", "testcase", "-n"));
		for (ProcessSpec process : benchmark.testSetupProcesses) {
			setupCmd.add("-p");
			setupCmd.add(String.valueOf(process.id));
			setupCmd.add(process.className);
			setupCmd.add(process.args);
		}
		String[] testSetup = setupCmd.toArray(new String[0]);

		ValiParRun valipar = new ValiParRun();
		valipar.newExperiment();
		valipar.createBaseline(instrumentation, filesPath);

		IntegerChromosome c0 = IntegerChromosome.of(
				IntegerGene.of(gene0, benchmark.argumentRanges.get(0).min, benchmark.argumentRanges.get(0).max));
		IntegerChromosome c1 = IntegerChromosome.of(
				IntegerGene.of(gene1, benchmark.argumentRanges.get(1).min, benchmark.argumentRanges.get(1).max));
		Genotype<IntegerGene> genotype = Genotype.of(c0, c1);

		int execTimeLimitMs = benchmark.execTimeLimitMs != null ? benchmark.execTimeLimitMs
				: BenchmarkConfig.DEFAULT_EXEC_TIME_LIMIT_MS;

		FitnessFunction ff = new FitnessFunction(genotype, 0, testSetup, execTimeLimitMs);
		System.out.println(ff.getFitness());
		System.out.println("Inspect: ./experiment/test0/valipar/required_elements/sync_edge_requirements.json");
	}
}
