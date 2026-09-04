package BioConcST;

import java.util.List;

/**
 * One champion individual's test input plus a controlled-execution replay
 * schedule that reproduces the exact sync trace CoverageInst measured its
 * coverage from - CoverageInst's analogue of ValiPar's own
 * "chromosome + PCFG path" pairing: a test input alone doesn't guarantee a
 * concurrent program reproduces the same coverage on a later re-run, so the
 * schedule is what makes the recorded coverage independently
 * reproducible/verifiable afterwards, without needing to trust the number
 * in a CSV. See CoverageInst.ReplaySchedule for how the schedule itself
 * works and CoverageInst.ReplayMain for how to actually replay one.
 *
 * Plain public-field POJO (matching ProcessSpec/BenchmarkConfig's style) so
 * Jackson can both write it (CoverageInstStrategy/ResultsWriter) and read it
 * back (ReplayMain) with no custom (de)serializer.
 */
public final class ReplayBundle {

	public int[] genotype;
	public List<ProcessLaunch> processes;
	public double coveragePercent;
	public int coveredCount;
	public int totalRequired;
	public String replaySchedule;

	public ReplayBundle() {
	}

	public ReplayBundle(int[] genotype, List<ProcessLaunch> processes, double coveragePercent, int coveredCount,
			int totalRequired, String replaySchedule) {
		this.genotype = genotype;
		this.processes = processes;
		this.coveragePercent = coveragePercent;
		this.coveredCount = coveredCount;
		this.totalRequired = totalRequired;
		this.replaySchedule = replaySchedule;
	}

	public static final class ProcessLaunch {
		public int id;
		public String className;
		public String[] args;

		public ProcessLaunch() {
		}

		public ProcessLaunch(int id, String className, String[] args) {
			this.id = id;
			this.className = className;
			this.args = args;
		}
	}
}
