package BioConcST;

import java.util.List;

import io.jenetics.IntegerGene;
import io.jenetics.Phenotype;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.util.ISeq;

public class SolutionResult {

	private Engine<IntegerGene, Double> syncCoverage;
	private EvolutionStatistics<Double, ?> statistics;
	private ISeq<Phenotype<IntegerGene, Double>> bestList;
	private ISeq<Phenotype<IntegerGene, Double>> bestPop;

	public SolutionResult(Engine<IntegerGene, Double> syncCoverage, EvolutionStatistics<Double, ?> statistics,
			ISeq<Phenotype<IntegerGene, Double>> bestList, ISeq<Phenotype<IntegerGene, Double>> bestPop) {
		super();
		this.syncCoverage = syncCoverage;
		this.statistics = statistics;
		this.bestList = bestList;
		this.bestPop =  bestPop; 
	}

	
	
	public ISeq<Phenotype<IntegerGene, Double>> getBestPop() {
		return bestPop;
	}



	public void setBestPop(ISeq<Phenotype<IntegerGene, Double>> bestPop) {
		this.bestPop = bestPop;
	}



	public List<Double> getSyncCoverage() {
		return syncCoverage.getSync_coverage();
	}

	public void setSyncCoverage(Engine<IntegerGene, Double> syncCoverage) {
		this.syncCoverage = syncCoverage;
	}

	public EvolutionStatistics<Double, ?> getStatistics() {
		return statistics;
	}

	public void setStatistics(EvolutionStatistics<Double, ?> statistics) {
		this.statistics = statistics;
	}

	public ISeq<Phenotype<IntegerGene, Double>> getBestList() {
		return bestList;
	}

	public void setBestList(ISeq<Phenotype<IntegerGene, Double>> bestList) {
		this.bestList = bestList;
	}

	

	
}
