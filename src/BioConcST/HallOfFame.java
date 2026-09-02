package BioConcST;

import java.util.concurrent.atomic.AtomicReference;

import io.jenetics.IntegerGene;
import io.jenetics.Phenotype;
import io.jenetics.engine.EvolutionInterceptor;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.util.ISeq;
import io.jenetics.util.MSeq;

/**
 * Selection can only make a good individual MORE LIKELY to survive, never
 * guarantee it: crossover and mutation reshape most of the population every
 * generation, so an individual that covers a rare, hard-to-reach target can
 * be found by the search and lost again a generation or two later with
 * nothing forcing it to stay. This tracks the best-coverage individual ever
 * seen across the whole run and, whenever it's missing from a generation's
 * population, injects it back in - replacing whichever individual
 * currently contributes the least coverage. The champion's fitness is
 * already known, so re-inserting it costs no extra (expensive) evaluation.
 */
public final class HallOfFame {

	private HallOfFame() {
	}

	public static EvolutionInterceptor<IntegerGene, TestFitness> interceptor() {
		AtomicReference<Phenotype<IntegerGene, TestFitness>> champion = new AtomicReference<>();

		return EvolutionInterceptor.ofAfter(result -> {
			ISeq<Phenotype<IntegerGene, TestFitness>> population = result.population();

			Phenotype<IntegerGene, TestFitness> bestThisGeneration = null;
			for (Phenotype<IntegerGene, TestFitness> candidate : population) {
				if (bestThisGeneration == null
						|| candidate.fitness().getCoverage() > bestThisGeneration.fitness().getCoverage()) {
					bestThisGeneration = candidate;
				}
			}

			Phenotype<IntegerGene, TestFitness> current = champion.get();
			if (bestThisGeneration != null
					&& (current == null || bestThisGeneration.fitness().getCoverage() > current.fitness().getCoverage())) {
				current = bestThisGeneration;
				champion.set(current);
			}

			if (current == null) {
				return result;
			}

			boolean present = false;
			for (Phenotype<IntegerGene, TestFitness> individual : population) {
				if (individual.genotype().equals(current.genotype())) {
					present = true;
					break;
				}
			}
			if (present) {
				return result;
			}

			MSeq<Phenotype<IntegerGene, TestFitness>> newPopulation = population.copy();
			int worstIndex = 0;
			double worstCoverage = newPopulation.get(0).fitness().getCoverage();
			for (int i = 1; i < newPopulation.size(); i++) {
				double coverage = newPopulation.get(i).fitness().getCoverage();
				if (coverage < worstCoverage) {
					worstCoverage = coverage;
					worstIndex = i;
				}
			}
			newPopulation.set(worstIndex, Phenotype.of(current.genotype(), result.generation(), current.fitness()));

			return EvolutionResult.of(result.optimize(), newPopulation.toISeq(), result.generation(),
					result.totalGenerations(), result.durations(), result.killCount(), result.invalidCount(),
					result.alterCount());
		});
	}
}
