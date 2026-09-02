package BioConcST;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jenetics.IntegerGene;
import io.jenetics.Phenotype;
import io.jenetics.util.Seq;

/**
 * A single test case only ever covers part of the "all sync edges" target -
 * what the tool is actually meant to deliver is a population of test cases
 * whose union covers as much of it as possible. Per-individual coverage
 * (TestFitness.getCoverage()) can't see that: two individuals can each sit
 * at 20% while covering two entirely different halves of the requirements.
 */
public final class SuiteCoverage {

	private SuiteCoverage() {
	}

	public static double unionCoveragePercent(Seq<Phenotype<IntegerGene, TestFitness>> population) {
		ObjectMapper mapper = new ObjectMapper();
		boolean[] covered = null;

		for (Phenotype<IntegerGene, TestFitness> individual : population) {
			JsonNode elements;
			try {
				elements = mapper.readTree(individual.fitness().getSyncEdgeRequirements());
			} catch (Exception e) {
				continue;
			}

			if (covered == null) {
				covered = new boolean[elements.size()];
			}

			for (int i = 0; i < elements.size() && i < covered.length; i++) {
				if ("COVERED".equals(elements.get(i).get("state").asText())) {
					covered[i] = true;
				}
			}
		}

		if (covered == null || covered.length == 0) {
			return 0.0;
		}

		int coveredCount = 0;
		for (boolean c : covered) {
			if (c) {
				coveredCount++;
			}
		}

		return (coveredCount * 100.0) / covered.length;
	}
}
