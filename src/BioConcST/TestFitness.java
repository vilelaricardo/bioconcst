package BioConcST;

/**
 * Fitness value for a concurrent test data individual: the distance to the
 * uncovered sync-edge requirements (Section 4.2.1 of the thesis), plus the
 * coverage percentage and the raw sync_edge_requirements needed by
 * FuzzySelector. Carrying this on the fitness value itself (instead of on
 * Genotype/Engine) is what lets this run on stock Jenetics without a fork.
 *
 * replaySchedule (CoverageInst path only - "" for ValiPar/Stub, which don't
 * have a replay mechanism) is the controlled-execution schedule built from
 * THIS SAME evaluation's own trace, captured right when the individual is
 * actually run and scored - see CoverageInstFitnessFunction. It has to live
 * here, on the fitness Jenetics caches per phenotype, because a surviving
 * individual is never re-evaluated in a later generation: if the schedule
 * were captured any other way (e.g. a fresh re-run after the search ends),
 * it would reproduce a completely different, independently-raced execution
 * of the same test input, not the one that actually earned the individual
 * its fitness - exactly the "cromossomo com caminho e com entrada de teste"
 * requirement a chromosome alone can't satisfy for a concurrent program.
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
	private final String replaySchedule;

	public TestFitness(double distance, double coverage, String syncEdgeRequirements) {
		this(distance, coverage, syncEdgeRequirements, "");
	}

	public TestFitness(double distance, double coverage, String syncEdgeRequirements, String replaySchedule) {
		this.distance = distance;
		this.coverage = coverage;
		this.syncEdgeRequirements = syncEdgeRequirements;
		this.replaySchedule = replaySchedule;
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

	public String getReplaySchedule() {
		return replaySchedule;
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
