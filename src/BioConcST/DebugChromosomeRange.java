package BioConcST;

import java.util.HashMap;
import java.util.Map;

import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;

/** One-off: checks whether IntegerChromosome/IntegerGene's max bound is inclusive or exclusive. */
public class DebugChromosomeRange {
	public static void main(String[] args) {
		IntegerChromosome template = IntegerChromosome.of(0, 1, 1);
		Map<Integer, Integer> counts = new HashMap<>();
		for (int i = 0; i < 1000; i++) {
			int value = template.newInstance().get(0).allele();
			counts.merge(value, 1, Integer::sum);
		}
		System.out.println("IntegerChromosome.of(0, 1, 1) over 1000 samples: " + counts);

		IntegerChromosome template2 = IntegerChromosome.of(0, 2, 1);
		Map<Integer, Integer> counts2 = new HashMap<>();
		for (int i = 0; i < 1000; i++) {
			int value = template2.newInstance().get(0).allele();
			counts2.merge(value, 1, Integer::sum);
		}
		System.out.println("IntegerChromosome.of(0, 2, 1) over 1000 samples: " + counts2);

		IntegerGene gene = IntegerGene.of(1, 0, 1);
		System.out.println("IntegerGene.of(1, 0, 1) -> allele=" + gene.allele() + " isValid=" + gene.isValid()
				+ " min=" + gene.min() + " max=" + gene.max());

		IntegerGene gene2 = IntegerGene.of(1, 0, 2);
		System.out.println("IntegerGene.of(1, 0, 2) -> allele=" + gene2.allele() + " isValid=" + gene2.isValid()
				+ " min=" + gene2.min() + " max=" + gene2.max());
	}
}
