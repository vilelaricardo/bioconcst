package BioConcST;

import java.util.List;

import io.jenetics.IntegerGene;
import io.jenetics.Phenotype;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.util.ISeq;

public class SolutionResult {

	private List<Double> syncCoverage;
	private EvolutionStatistics<TestFitness, ?> statistics;
	private ISeq<Phenotype<IntegerGene, TestFitness>> bestList;
	private ISeq<Phenotype<IntegerGene, TestFitness>> bestPop;
	// Null for strategies that don't have a replay concept (GA/GA_STUB against
	// ValiPar or the in-memory stub) - only GA_COVINST populates this, with
	// one entry per individual in bestPop. See ReplayBundle's own javadoc.
	private List<ReplayBundle> replayBundles;

	public SolutionResult(List<Double> syncCoverage, EvolutionStatistics<TestFitness, ?> statistics,
			ISeq<Phenotype<IntegerGene, TestFitness>> bestList, ISeq<Phenotype<IntegerGene, TestFitness>> bestPop) {
		super();
		this.syncCoverage = syncCoverage;
		this.statistics = statistics;
		this.bestList = bestList;
		this.bestPop = bestPop;
	}

	public List<ReplayBundle> getReplayBundles() {
		return replayBundles;
	}

	public void setReplayBundles(List<ReplayBundle> replayBundles) {
		this.replayBundles = replayBundles;
	}

	public ISeq<Phenotype<IntegerGene, TestFitness>> getBestPop() {
		return bestPop;
	}

	public void setBestPop(ISeq<Phenotype<IntegerGene, TestFitness>> bestPop) {
		this.bestPop = bestPop;
	}

	public List<Double> getSyncCoverage() {
		return syncCoverage;
	}

	public void setSyncCoverage(List<Double> syncCoverage) {
		this.syncCoverage = syncCoverage;
	}

	public EvolutionStatistics<TestFitness, ?> getStatistics() {
		return statistics;
	}

	public void setStatistics(EvolutionStatistics<TestFitness, ?> statistics) {
		this.statistics = statistics;
	}

	public ISeq<Phenotype<IntegerGene, TestFitness>> getBestList() {
		return bestList;
	}

	public void setBestList(ISeq<Phenotype<IntegerGene, TestFitness>> bestList) {
		this.bestList = bestList;
	}

}
