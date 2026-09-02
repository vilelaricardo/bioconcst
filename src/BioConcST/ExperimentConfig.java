package BioConcST;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Top-level experiment description, loaded from a JSON file instead of being
 * hardcoded per benchmark. "strategy" selects which SearchStrategy generates
 * test data (see SearchStrategy.resolve()) - the same benchmark/GA
 * configuration is reused unchanged when a new strategy (e.g. the LLM-hint
 * one) is added.
 */
public class ExperimentConfig {
	public BenchmarkConfig benchmark;
	public GAConfig ga;
	public String strategy;
	public OutputConfig output;

	public static ExperimentConfig load(String path) throws IOException {
		return new ObjectMapper().readValue(new File(path), ExperimentConfig.class);
	}
}
