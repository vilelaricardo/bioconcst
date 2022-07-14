package BioConcST;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jenetics.Gene;
import io.jenetics.Optimize;
import io.jenetics.Phenotype;
import io.jenetics.Selector;
import io.jenetics.util.ISeq;
import io.jenetics.util.MSeq;
import io.jenetics.util.RandomRegistry;
import io.jenetics.util.Seq;
import net.sourceforge.jFuzzyLogic.FIS;

public class FuzzySelector<G extends Gene<?, G>, C extends Comparable<? super C>> implements Selector<G, C> {

	private FIS FIS;
	private double originality;

	@Override
	public ISeq<Phenotype<G, C>> select(final Seq<Phenotype<G, C>> population, final int count, final Optimize opt) {
		requireNonNull(population, "Population");
		requireNonNull(opt, "Optimization");
		if (count < 0) {
			throw new IllegalArgumentException(
					format("Selection count must be greater or equal then zero, but was %d.", count));
		}

		final MSeq<Phenotype<G, C>> selection;
		if (count > 0 && !population.isEmpty()) {
			selection = MSeq.ofLength(count);
			final Random random = RandomRegistry.random();
			final int size = population.size();

			List<Survivor> rankedSurvivor = new ArrayList<Survivor>();

			for (int i = 0; i < population.size(); i++) {

				rankedSurvivor.add(new Survivor(population.get(i), testDataSuvivor(population, population.get(i))));
			}

			Collections.sort(rankedSurvivor, Collections.reverseOrder());

			for (int i = 0; i < count; ++i) {
				selection.set(i, rankedSurvivor.get(i).getTestdata());

			}

		} else {
			selection = MSeq.empty();
		}

		return selection.toISeq();
	}

	private double testDataSuvivor(final Seq<Phenotype<G, C>> population, Phenotype<G, C> testdata) {

		startFuzzy();
		FIS.getVariable("originality").setValue(hammingdistance(population, testdata));
		FIS.getVariable("coverage").setValue(testdata.genotype().getCoverage());
		FIS.getVariable("fitness").setValue((double) testdata.fitness());

		FIS.evaluate();

		// System.out.println("survivor: " + FIS.getVariable("survivor").getValue());
		// System.out.println("testdata: " + testdata);
		return FIS.getVariable("survivor").getValue();

	}

	private void startFuzzy() {

		this.FIS = FIS.load("fuzzy/fuzzySelector.FCL");
	}

	private double hammingdistance(final Seq<Phenotype<G, C>> population, Phenotype testdata) {
		double originality;
		List<Double> hamming = new ArrayList<Double>();
		double aux;

		JsonNode syncList = buildSyncList(testdata.genotype().getSync_edge_requirements());

		for (int i = 0; i < population.size(); i++) {
			aux = 0;
			JsonNode syncTest = buildSyncList(population.get(i).genotype().getSync_edge_requirements());

			for (int j = 0; j < syncList.size(); j++) {

				if (syncList.get(j)!=null && syncTest.get(j)!=null) {
					
					if (syncTest.get(j)==null) {
						return 0;
					}
					
				if (syncList.get(j).get("state").equals(syncTest.get(j).get("state"))) {
					aux++;
				}
				}
			}
			hamming.add(aux / syncList.size());
		}

		originality = 1 - hamming.stream().mapToDouble(Double::doubleValue).sum() / hamming.size();

		return originality;
	}

	private JsonNode buildSyncList(String jsonString) {

		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode jsonNode = null;
		try {
			jsonNode = objectMapper.readTree(jsonString);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return jsonNode;
	}

	class Survivor implements Comparable<Survivor> {

		private Phenotype<G, C> testdata;
		private Double survivor;

		public Survivor(Phenotype<G, C> testdata, Double survivor) {
			super();
			this.testdata = testdata;
			this.survivor = survivor;
		}

		public Phenotype<G, C> getTestdata() {
			return testdata;
		}

		public void setTestdata(Phenotype<G, C> testdata) {
			this.testdata = testdata;
		}

		public Double getSurvivor() {
			return survivor;
		}

		public void setSurvivor(Double survivor) {
			this.survivor = survivor;
		}

		@Override
		public int compareTo(FuzzySelector<G, C>.Survivor anotherSurvivor) {

			return this.survivor.compareTo(anotherSurvivor.getSurvivor());
		}

	}
}