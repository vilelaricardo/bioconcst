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
	// Cumulative union of covered required-edge keys across ALL generations
	// seen so far (CoverageInstStrategy's coveredEdgeKeysSoFar, sampled once
	// per generation) - distinct from syncCoverage, which is the union of
	// only ONE generation's population and can go up and down between
	// generations even though the search never forgets an edge it already
	// proved coverable. Null for strategies that don't track this (only
	// GA_COVINST populates it).
	private List<Double> cumulativeCoverage;
	// Real number of native evaluate() calls executed (see
	// CoverageInstFitnessFunction.evaluationCount()) - distinct from the
	// nominal populationSize x generations budget, which overcounts whenever
	// survivors carry fitness across generations instead of being
	// re-evaluated. -1 for strategies that don't track this.
	private int evaluationCount = -1;
	// RequiredEdge.toString() keys for elements NEVER covered by any
	// individual across the whole run (required.size() minus this list's
	// size is the same count the final cumulativeCoverage% already implies,
	// but this names WHICH elements specifically - needed to tell "always
	// the same 4 wrong-pairing elements" from "different elements each
	// time" across executions/arms). Null for strategies that don't track
	// this.
	private List<String> uncoveredElementKeys;
	// Per-generation snapshot of evaluationCount (same real-evaluate()-calls
	// counter as evaluationCount, sampled once per generation instead of only
	// at the end) - lets post-hoc analysis find "evaluations spent by the
	// time cumulativeCoverage first reached 100%" instead of only comparing
	// two arms' final evaluationCount, which conflates search progress with
	// how much budget each arm happened to burn (see sync-claude-codex.md,
	// 2026-09-17 - comparing success rate under unequal evaluation budgets
	// doesn't isolate the mechanism's effect from how much it costs to run).
	// Null for strategies that don't track this.
	private List<Integer> evaluationCountHistory;

	public SolutionResult(List<Double> syncCoverage, EvolutionStatistics<TestFitness, ?> statistics,
			ISeq<Phenotype<IntegerGene, TestFitness>> bestList, ISeq<Phenotype<IntegerGene, TestFitness>> bestPop) {
		super();
		this.syncCoverage = syncCoverage;
		this.statistics = statistics;
		this.bestList = bestList;
		this.bestPop = bestPop;
	}

	public List<Double> getCumulativeCoverage() {
		return cumulativeCoverage;
	}

	public void setCumulativeCoverage(List<Double> cumulativeCoverage) {
		this.cumulativeCoverage = cumulativeCoverage;
	}

	public int getEvaluationCount() {
		return evaluationCount;
	}

	public void setEvaluationCount(int evaluationCount) {
		this.evaluationCount = evaluationCount;
	}

	public List<String> getUncoveredElementKeys() {
		return uncoveredElementKeys;
	}

	public void setUncoveredElementKeys(List<String> uncoveredElementKeys) {
		this.uncoveredElementKeys = uncoveredElementKeys;
	}

	public List<Integer> getEvaluationCountHistory() {
		return evaluationCountHistory;
	}

	public void setEvaluationCountHistory(List<Integer> evaluationCountHistory) {
		this.evaluationCountHistory = evaluationCountHistory;
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
