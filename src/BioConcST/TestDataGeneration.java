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
import org.apache.poi.EncryptedDocumentException;

import Utilities.BioConcSTStatistics;
import io.jenetics.IntegerGene;
import io.jenetics.MonteCarloSelector;
import io.jenetics.Phenotype;
import io.jenetics.Selector;
import io.jenetics.util.ISeq;

public class TestDataGeneration {

	// Genetic Algorithm Setup
	private static final int populationSize = 6;
	private static final int generations = 100;
	private static final double mutationRate = 0.1;
	private static final double crossoverRate = 0.6;
	private static final double suvivorsFraction = 0.1;
	private static final double offspringFraction = 0.9;
	private static final int executions = 20;

	// Number of arguments of System under Testing
	private static final int argumentsLenght = 3;

	private static final Selector<IntegerGene, Double> survivorsSelector = new FuzzySelector<IntegerGene, Double>();
	private static final Selector<IntegerGene, Double> offspringSelector = new FuzzySelector<IntegerGene, Double>();

	// Range Gene
	private static final int min = -100000;
	private static final int max = 100000;

	// Number of executor threads
	private static final int threadExecutors = 25;

	// List best of repetitions
	private static List<ISeq<Phenotype<IntegerGene, Double>>> bestList = new ArrayList<ISeq<Phenotype<IntegerGene, Double>>>();
	private static List<ArrayList<Double>> coverageList = new ArrayList<ArrayList<Double>>();
	private static List<ISeq<Phenotype<IntegerGene, Double>>> bestPop = new ArrayList<ISeq<Phenotype<IntegerGene, Double>>>();

	public static void main(String[] args) throws EncryptedDocumentException, IOException {

		BioConcSTCore bioCore = null;
		for (int i = 0; i < executions; i++) {

			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
			Date date = new Date(System.currentTimeMillis());
			System.out.println("Starting: " + formatter.format(date));

			// BioConcST Object
			bioCore = new BioConcSTCore(populationSize, generations, mutationRate, crossoverRate, min, max,
					suvivorsFraction, offspringFraction, argumentsLenght, threadExecutors, survivorsSelector,
					offspringSelector);

			// System under testing configuration
			File filesPath = new File("./");
			ProcessBuilder instrumentation = new ProcessBuilder("valipar", "inst", "-t", "-l", "-p", "GcdMaster",
					"GcdSlave", "GcdSlave", "GcdSlave", "-f", "GcdMaster.class", "GcdSlave.class", "-i",
					"HelperClass.class");

			String[] testSetup = { "valipar", "testcase", "-n", "-p", "0", "GcdMaster", "TESTDATA", "-p", "1",
					"GcdSlave", "1", "-p", "2", "GcdSlave", "2", "-p", "3", "GcdSlave", "3" };

			// Call BioConcST Evolution
			bioCore.generatorEvolution(filesPath, instrumentation, testSetup);

			
			bestList.add(bioCore.getSolutionResults().getBestList());
			coverageList.add((ArrayList<Double>) bioCore.getSolutionResults().getSyncCoverage());
			bestPop.add(bioCore.getSolutionResults().getBestPop());
			date = new Date(System.currentTimeMillis());
			System.out.println("Ending: " + formatter.format(date));

			compressResults(i, offspringSelector);

		}

		BioConcSTStatistics statistics = new BioConcSTStatistics(bestList, bestPop, coverageList, offspringSelector,
				generations, executions, populationSize);

		statistics.store();
		System.exit(0);
	}

	public static void compressResults(int execution, Selector<IntegerGene, Double> selector) {
		File file = new File("./" + selector.toString().replaceAll("Selector.*$", ""));
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
		// TarArchiveOutputStream taos = new TarArchiveOutputStream(new
		// FileOutputStream(name));
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
