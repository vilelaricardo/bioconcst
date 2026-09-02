package BioConcST;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;

/**
 * CLI entry point for running an experiment described by a JSON config
 * (default: config/gcdmaster-ga.json, or pass a path as args[0]). The
 * benchmark, GA hyperparameters, and search strategy all come from that
 * file - none of it is hardcoded here - so pointing this at a different
 * benchmark, or (once added) switching "strategy" to the LLM-hint approach,
 * doesn't require touching this class.
 */
public class TestDataGeneration {

	public static void main(String[] args) throws IOException {
		String configPath = args.length > 0 ? args[0] : "config/gcdmaster-ga.json";
		ExperimentConfig config = ExperimentConfig.load(configPath);

		if (config.benchmarks != null && !config.benchmarks.isEmpty()) {
			// Suite run: same ga/strategy/output settings applied to every
			// benchmark, so the treatment can't drift between benchmarks.
			for (BenchmarkConfig benchmark : config.benchmarks) {
				runBenchmark(config, benchmark, benchmark.name + "-" + config.strategy.toLowerCase());
			}
		} else {
			runBenchmark(config, config.benchmark, config.output.runName);
		}

		System.exit(0);
	}

	private static void runBenchmark(ExperimentConfig config, BenchmarkConfig benchmark, String runName)
			throws IOException {
		File filesPath = new File(benchmark.path);
		ProcessBuilder instrumentation = buildInstrumentation(benchmark);
		String[] testSetup = buildTestSetup(benchmark);
		SearchStrategy strategy = SearchStrategy.resolve(config.strategy);

		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");

		for (int i = 0; i < config.ga.executions; i++) {

			System.out.println("Starting " + runName + ": " + formatter.format(new Date(System.currentTimeMillis())));

			SolutionResult result = strategy.run(config, benchmark, filesPath, instrumentation, testSetup);

			ResultsWriter.writeGenerations(result, config.output.directory, runName + "-execution" + i + ".csv");

			System.out.println("Ending " + runName + ": " + formatter.format(new Date(System.currentTimeMillis())));

			compressResults(i, runName);
		}
	}

	private static ProcessBuilder buildInstrumentation(BenchmarkConfig benchmark) {
		List<String> command = new ArrayList<>(List.of("valipar", "inst", "-t", "-l", "-p"));
		command.addAll(benchmark.processes);
		command.add("-f");
		command.addAll(benchmark.parseFiles);
		if (benchmark.ignoreFiles != null && !benchmark.ignoreFiles.isEmpty()) {
			command.add("-i");
			command.addAll(benchmark.ignoreFiles);
		}
		return new ProcessBuilder(command);
	}

	private static String[] buildTestSetup(BenchmarkConfig benchmark) {
		List<String> command = new ArrayList<>(List.of("valipar", "testcase", "-n"));
		for (ProcessSpec process : benchmark.testSetupProcesses) {
			command.add("-p");
			command.add(String.valueOf(process.id));
			command.add(process.className);
			command.add(process.args);
		}
		return command.toArray(new String[0]);
	}

	public static void compressResults(int execution, String runName) {
		File file = new File("./" + runName);
		if (!file.exists()) {
			try {
				FileUtils.forceMkdir(file);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}

		try {
			compress("execution" + execution + ".tar.gz", new File("./experiment"));
			Process p = new ProcessBuilder("mv", "execution" + execution + ".tar.gz", file.getPath()).start();
			p.waitFor();
			p = new ProcessBuilder("rm", "-rf", "experiment").start();
			p.waitFor();

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void compress(String name, File file) throws IOException {
		try (TarArchiveOutputStream out = getTarArchiveOutputStream(name)) {

			addToArchiveCompression(out, file, ".");

		}
	}

	private static TarArchiveOutputStream getTarArchiveOutputStream(String name) throws IOException {
		TarArchiveOutputStream taos = new TarArchiveOutputStream(
				new GzipCompressorOutputStream(new FileOutputStream(name)));
		// TAR has an 8 gig file limit by default, this gets around that
		taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
		// TAR originally didn't support long file names, so enable the support for it
		taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
		taos.setAddPaxHeadersForNonAsciiNames(true);
		return taos;
	}

	private static void addToArchiveCompression(TarArchiveOutputStream out, File file, String dir) throws IOException {
		String entry = dir + File.separator + file.getName();
		if (file.isFile()) {
			out.putArchiveEntry(new TarArchiveEntry(file, entry));
			try (FileInputStream in = new FileInputStream(file)) {
				IOUtils.copy(in, out);
			}
			out.closeArchiveEntry();
		} else if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) {
					addToArchiveCompression(out, child, entry);
				}
			}
		} else {
			System.out.println(file.getName() + " is not supported");
		}
	}

}
