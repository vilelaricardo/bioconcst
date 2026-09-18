package BioConcST;

import io.jenetics.Chromosome;
import io.jenetics.Genotype;
import io.jenetics.IntegerGene;

/**
 * A fast, in-memory stand-in for the real fitness function, used to tune GA
 * hyperparameters (population size, survivorsFraction, mutation/crossover
 * rates) and the fuzzy selector's calibration cheaply, before spending real
 * (expensive - real process execution, real Docker containers) evaluations
 * confirming the tuning transfers to the actual benchmarks.
 *
 * Mirrors the exact "flag problem" shape of the three synthetic benchmarks
 * (quorum-handshake, threshold-handshake, combined-handshake): each gene
 * only contributes coverage when its own value lands inside a secret
 * window, and a further block of coverage only unlocks when EVERY gene is
 * in its window at once - the hard, compound target. Distance is a pure
 * step function of how many required elements are covered, with zero
 * sensitivity to how numerically close an untaken gene's value is to its
 * window - deliberately, since that's the real BFS-distance metric's own
 * behavior (see FitnessFunction/DistanceElem) and the whole reason these
 * benchmarks are hard for the GA. A stub with a smoother gradient would
 * tune hyperparameters against an easier problem than the real one.
 */
public final class StubFitnessFunction {

	public static final int WINDOW_LOW = 480;
	public static final int WINDOW_HIGH = 519;

	private static final int ALWAYS_COVERED = 6;
	private static final int PER_GENE_ELEMENTS = 3;
	private static final int COMPOUND_ELEMENTS = 5;

	private StubFitnessFunction() {
	}

	public static TestFitness evaluate(Genotype<IntegerGene> genotype) {
		int geneCount = 0;
		int inWindowCount = 0;
		for (Chromosome<IntegerGene> chromosome : genotype) {
			for (IntegerGene gene : chromosome) {
				geneCount++;
				int value = gene.allele();
				if (value >= WINDOW_LOW && value <= WINDOW_HIGH) {
					inWindowCount++;
				}
			}
		}

		int totalElements = ALWAYS_COVERED + geneCount * PER_GENE_ELEMENTS + COMPOUND_ELEMENTS;

		int covered = ALWAYS_COVERED + inWindowCount * PER_GENE_ELEMENTS;
		boolean allInWindow = geneCount > 0 && inWindowCount == geneCount;
		if (allInWindow) {
			covered += COMPOUND_ELEMENTS;
		}

		double coverage = (covered * 100.0) / totalElements;

		int uncovered = totalElements - covered;
		double distance = uncovered > 0 ? (0.02 * uncovered) : 0.01;

		return new TestFitness(distance, coverage, buildRequiredElementsJson(covered, totalElements));
	}

	private static String buildRequiredElementsJson(int coveredCount, int total) {
		StringBuilder json = new StringBuilder("[");
		for (int i = 0; i < total; i++) {
			if (i > 0) {
				json.append(",");
			}
			json.append("{\"state\":\"").append(i < coveredCount ? "COVERED" : "UNCOVERED").append("\"}");
		}
		return json.append("]").toString();
	}
}
