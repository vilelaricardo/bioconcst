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
		// Null for strategies that don't track it (only GA_COVINST does) -
		// fall back to the per-generation column so the CSV shape stays
		// uniform across strategies instead of writing a blank/missing cell.
		List<Double> cumulativeCoverage = result.getCumulativeCoverage();
		// Per-generation evaluationCount snapshot - lets post-hoc analysis
		// compare arms at a common evaluation budget instead of only at a
		// common generation count, which silently favors whichever arm's
		// fitness landscape causes more Jenetics survivor-cache hits (see
		// SolutionResult.evaluationCountHistory's javadoc). -1 when a
		// strategy doesn't track this.
		List<Integer> evaluationCountHistory = result.getEvaluationCountHistory();

		File dir = new File(directory);
		dir.mkdirs();
		File out = new File(dir, fileName);

		try (FileWriter writer = new FileWriter(out)) {
			writer.write("generation,distance,coverage,cumulativeCoverage,evaluationCount\n");
			for (int i = 0; i < bestList.size(); i++) {
				double cumulative = cumulativeCoverage != null ? cumulativeCoverage.get(i) : coverage.get(i);
				int evalCount = evaluationCountHistory != null ? evaluationCountHistory.get(i) : -1;
				writer.write((i + 1) + "," + bestList.get(i).fitness().getDistance() + "," + coverage.get(i) + ","
						+ cumulative + "," + evalCount + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Small companion file recording the real number of native evaluate()
	 * calls executed - see SolutionResult.getEvaluationCount()'s javadoc for
	 * why this is not simply populationSize x generations - and, when
	 * tracked, which required elements were never covered by any individual
	 * across the whole run (see getUncoveredElementKeys()'s javadoc). A
	 * no-op for strategies that don't track evaluationCount (stays -1).
	 */
	public static void writeMeta(SolutionResult result, String directory, String fileName) {
		if (result.getEvaluationCount() < 0) {
			return;
		}
		File dir = new File(directory);
		dir.mkdirs();
		File out = new File(dir, fileName);
		try {
			java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
			meta.put("evaluationCount", result.getEvaluationCount());
			meta.put("uncoveredElementKeys",
					result.getUncoveredElementKeys() != null ? result.getUncoveredElementKeys() : List.of());
			new ObjectMapper().writeValue(out, meta);
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
