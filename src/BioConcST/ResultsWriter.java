package BioConcST;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

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
}
