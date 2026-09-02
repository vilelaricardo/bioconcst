package BioConcST;

/**
 * Fitness value for a concurrent test data individual: the distance to the
 * uncovered sync-edge requirements (Section 4.2.1 of the thesis), plus the
 * coverage percentage and the raw sync_edge_requirements needed by
 * FuzzySelector. Carrying this on the fitness value itself (instead of on
 * Genotype/Engine) is what lets this run on stock Jenetics without a fork.
 *
 * Extends Number so it still satisfies EvolutionStatistics.ofNumber()'s
 * bound; natural ordering (and Number's value) is the distance, since that's
 * what the GA minimizes.
 */
public final class TestFitness extends Number implements Comparable<TestFitness> {

	private static final long serialVersionUID = 1L;

	private final double distance;
	private final double coverage;
	private final String syncEdgeRequirements;

	public TestFitness(double distance, double coverage, String syncEdgeRequirements) {
		this.distance = distance;
		this.coverage = coverage;
		this.syncEdgeRequirements = syncEdgeRequirements;
	}

	public double getDistance() {
		return distance;
	}

	public double getCoverage() {
		return coverage;
	}

	public String getSyncEdgeRequirements() {
		return syncEdgeRequirements;
	}

	@Override
	public int compareTo(TestFitness other) {
		return Double.compare(distance, other.distance);
	}

	@Override
	public int intValue() {
		return (int) distance;
	}

	@Override
	public long longValue() {
		return (long) distance;
	}

	@Override
	public float floatValue() {
		return (float) distance;
	}

	@Override
	public double doubleValue() {
		return distance;
	}

	@Override
	public String toString() {
		return Double.toString(distance);
	}
}
