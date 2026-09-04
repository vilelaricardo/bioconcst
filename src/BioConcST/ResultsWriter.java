package BioConcST;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.jenetics.IntegerGene;
import io.jenetics.Phenotype;
import io.jenetics.util.ISeq;

/**
 * Writes one execution's generation-by-generation distance/coverage as CSV -
 * the same shape used to chart the GA's evolution, now produced directly by
 * the pipeline instead of a one-off script, and comparable across strategies
 * (GA, and later the LLM-hint one) and benchmarks by construction.
 */
public final class ResultsWriter {

	private ResultsWriter() {
	}

	public static void writeGenerations(SolutionResult result, String directory, String fileName) {
		ISeq<Phenotype<IntegerGene, TestFitness>> bestList = result.getBestList();
		List<Double> coverage = result.getSyncCoverage();

		File dir = new File(directory);
		dir.mkdirs();
		File out = new File(dir, fileName);

		try (FileWriter writer = new FileWriter(out)) {
			writer.write("generation,distance,coverage\n");
			for (int i = 0; i < bestList.size(); i++) {
				writer.write((i + 1) + "," + bestList.get(i).fitness().getDistance() + "," + coverage.get(i) + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * The "chromosome with path and test input" export: one JSON object per
	 * champion individual, each pairing its test input with a replay
	 * schedule (see ReplayBundle/CoverageInst.ReplaySchedule). A no-op for
	 * strategies that don't populate SolutionResult.replayBundles (only
	 * GA_COVINST does, currently).
	 */
	public static void writeReplayBundles(SolutionResult result, String directory, String fileName) {
		List<ReplayBundle> bundles = result.getReplayBundles();
		if (bundles == null || bundles.isEmpty()) {
			return;
		}

		File dir = new File(directory);
		dir.mkdirs();
		File out = new File(dir, fileName);

		try {
			new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(out, bundles);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
