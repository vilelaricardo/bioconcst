package ValiPar;

import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;

import io.jenetics.Genotype;
import io.jenetics.IntegerGene;

public final class ValiParRun {

	public ValiParRun() {

	}

	public void newExperiment() {

		File file = new File("./experiment");
		try {
			if (file.isDirectory()) {
				FileUtils.deleteDirectory(file);
			}
			FileUtils.forceMkdir(new File("experiment"));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		// Any already-running container pool is bind-mounted to the directory
		// instance that was just deleted - force it to restart against the
		// fresh one (see ValiParContainerPool.reset()).
		ValiParContainerPool.reset();

	}

	// Instrumentation only depends on the program under test, never on the test
	// data of an individual, so it's done once per generatorEvolution() run
	// instead of once per fitness evaluation.
	public void createBaseline(ProcessBuilder instrumentation, File filesPath) {
		File baselineDir = new File("./experiment/baseline");
		try {
			FileUtils.forceMkdir(baselineDir);
			FileUtils.copyDirectory(filesPath, baselineDir,
					FileFilterUtils.and(FileFileFilter.FILE, FileFilterUtils.suffixFileFilter(".class")));

			Process process = new ProcessBuilder("valipar", "project", "--setup").directory(baselineDir).start();
			process.waitFor();

			process = instrumentation.directory(baselineDir).start();
			process.waitFor();

			process = new ProcessBuilder("valipar", "elem").directory(baselineDir).start();
			process.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}

		if (isBaselineEmpty()) {
			createBaseline(instrumentation, filesPath);
		}
	}

	// Copies the pre-instrumented baseline into this individual's own project
	// directory - a plain file copy instead of re-running instrumentation.
	// "tests" and "source_files" are created fresh (empty) since those are
	// per-individual, not part of the reusable baseline.
	public void createProjectFromBaseline(int testID) {
		File baselineValipar = new File("./experiment/baseline/valipar");
		File projectDir = new File("./experiment/test" + testID);
		File projectValipar = new File(projectDir, "valipar");
		try {
			FileUtils.copyDirectory(new File("./experiment/baseline"), projectDir,
					FileFilterUtils.and(FileFileFilter.FILE, FileFilterUtils.suffixFileFilter(".class")));
			FileUtils.copyDirectory(new File(baselineValipar, "config"), new File(projectValipar, "config"));
			FileUtils.copyDirectory(new File(baselineValipar, "instrumented"), new File(projectValipar, "instrumented"));
			FileUtils.copyDirectory(new File(baselineValipar, "pcfgs"), new File(projectValipar, "pcfgs"));
			FileUtils.copyDirectory(new File(baselineValipar, "required_elements"),
					new File(projectValipar, "required_elements"));
			FileUtils.forceMkdir(new File(projectValipar, "tests"));
			FileUtils.forceMkdir(new File(projectValipar, "source_files"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void deleteProject(int testID) {
		try {
			Process process = new ProcessBuilder("valipar", "project", "--delete")
					.directory(new File("./experiment/test" + testID)).start();
			process.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void newTestCase(Genotype<IntegerGene> testdata, int testID, String[] testSetup) {
		// testdata.chromosome() would only return the genotype's first
		// chromosome, silently dropping every argument past the first when a
		// benchmark uses one length-1 chromosome per argument position (see
		// BioConcSTCore's per-argument ArgumentRange handling) - so every
		// chromosome's genes are concatenated here instead.
		StringBuilder dataBuilder = new StringBuilder();
		for (io.jenetics.Chromosome<IntegerGene> chromosome : testdata) {
			for (IntegerGene gene : chromosome) {
				if (dataBuilder.length() > 0) {
					dataBuilder.append(" ");
				}
				dataBuilder.append(gene.allele());
			}
		}
		String data = dataBuilder.toString();
		String[] newtestCase = testSetup.clone();

		for (int i = 0; i < testSetup.length; i++) {
			newtestCase[i] = testSetup[i].replace("TESTDATA", data);
		}

		try {

			Process process = new ProcessBuilder(newtestCase).directory(new File("./experiment/test" + testID)).start();

			process.waitFor();

		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void evaluation(int testID) {
		try {

			Process process = new ProcessBuilder("valipar", "eval").directory(new File("./experiment/test" + testID))
					.start();
			process.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	public static void execution(int testID) {
		ValiParContainerPool.getInstance(new File("./experiment")).execute(testID);
	}

	public static synchronized boolean isPortinUse(int port) {

		try {
			(new DatagramSocket(port)).close();
			return true;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;

	}
	
	public boolean isBaselineEmpty() {
		try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get("./experiment/baseline/valipar/instrumented"))) {
			return !dirStream.iterator().hasNext();
		} catch (IOException e) {
			e.printStackTrace();
			return true;
		}
	}

}
