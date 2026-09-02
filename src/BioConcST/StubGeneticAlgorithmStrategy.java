package BioConcST;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;
import io.jenetics.Phenotype;
import io.jenetics.Selector;
import io.jenetics.SinglePointCrossover;
import io.jenetics.SwapMutator;
import io.jenetics.engine.Codec;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionInterceptor;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.engine.Limits;
import io.jenetics.engine.Problem;
import io.jenetics.util.ISeq;

/**
 * Runs the exact same Engine/FuzzySelector/HallOfFame wiring as
 * GeneticAlgorithmStrategy, but against StubFitnessFunction instead of the
 * real ValiPar-backed one - no process spawning, no Docker, no file I/O.
 * Lets GA hyperparameters and the fuzzy selector's calibration be tuned in
 * milliseconds per generation instead of seconds, before spending real
 * (expensive) evaluations confirming the tuning actually transfers to the
 * real benchmarks. Deliberately kept separate from BioConcSTCore/
 * FitnessFunction rather than making the real path pluggable, so the
 * already-validated production path can't be affected by this at all.
 */
public class StubGeneticAlgorithmStrategy implements SearchStrategy {

	@Override
	public SolutionResult run(ExperimentConfig config, BenchmarkConfig benchmark, File filesPath,
			ProcessBuilder instrumentation, String[] testSetup) {
		GAConfig ga = config.ga;

		Genotype<IntegerGene> genotype;
		if (benchmark.argumentRanges != null && !benchmark.argumentRanges.isEmpty()) {
			IntegerChromosome[] chromosomes = new IntegerChromosome[benchmark.argumentRanges.size()];
			for (int i = 0; i < benchmark.argumentRanges.size(); i++) {
				chromosomes[i] = IntegerChromosome.of(benchmark.argumentRanges.get(i).min,
						benchmark.argumentRanges.get(i).max, 1);
			}
			genotype = Genotype.of(chromosomes[0], java.util.Arrays.copyOfRange(chromosomes, 1, chromosomes.length));
		} else {
			genotype = Genotype.of(IntegerChromosome.of(ga.min, ga.max, benchmark.argumentsLength));
		}

		Problem<Genotype<IntegerGene>, IntegerGene, TestFitness> problem = Problem.of(StubFitnessFunction::evaluate,
				Codec.of(genotype, gt -> gt));

		Selector<IntegerGene, TestFitness> survivorsSelector = new FuzzySelector<>();
		Selector<IntegerGene, TestFitness> offspringSelector = new FuzzySelector<>();

		// Hall-of-fame first, dedup second - see BioConcSTCore.generatorEvolution()
		// for why the order matters (dedup leaves replaced individuals
		// unevaluated on purpose; hall-of-fame calling .fitness() on those
		// before Jenetics evaluates them throws).
		EvolutionInterceptor<IntegerGene, TestFitness> uniqueInterceptor = EvolutionResult.toUniquePopulation();
		EvolutionInterceptor<IntegerGene, TestFitness> hallOfFameInterceptor = HallOfFame.interceptor();
		EvolutionInterceptor<IntegerGene, TestFitness> combinedInterceptor = EvolutionInterceptor
				.ofAfter(result -> uniqueInterceptor.after(hallOfFameInterceptor.after(result)));

		Engine<IntegerGene, TestFitness> engine = Engine.builder(problem).minimizing()
				.survivorsFraction(ga.survivorsFraction).offspringFraction(ga.offspringFraction)
				.survivorsSelector(survivorsSelector).offspringSelector(offspringSelector)
				.populationSize(ga.populationSize)
				.alterers(new SwapMutator<>(ga.mutationRate), new SinglePointCrossover<>(ga.crossoverRate))
				.interceptor(combinedInterceptor).build();

		EvolutionStatistics<TestFitness, ?> statistics = EvolutionStatistics.ofNumber();
		List<Double> syncCoverageHistory = new ArrayList<>();
		double[] bestCoverageSoFar = { -1.0 };
		AtomicReference<ISeq<Phenotype<IntegerGene, TestFitness>>> bestPopulation = new AtomicReference<>(
				ISeq.empty());

		ISeq<Phenotype<IntegerGene, TestFitness>> results = engine.stream()
				.limit(Limits.byFixedGeneration(ga.generations)).peek(statistics).peek(result -> {
					double genCoverage = SuiteCoverage.unionCoveragePercent(result.population());
					syncCoverageHistory.add(genCoverage);
					if (genCoverage > bestCoverageSoFar[0]) {
						bestCoverageSoFar[0] = genCoverage;
						bestPopulation.set(result.population());
					}
				}).map(EvolutionResult::bestPhenotype).collect(ISeq.toISeq());

		return new SolutionResult(syncCoverageHistory, statistics, results, bestPopulation.get());
	}
}
