package BioConcST;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Top-level experiment description, loaded from a JSON file instead of being
 * hardcoded per benchmark. "strategy" selects which SearchStrategy generates
 * test data (see SearchStrategy.resolve()) - the same benchmark/GA
 * configuration is reused unchanged when a new strategy (e.g. the LLM-hint
 * one) is added.
 *
 * Two shapes are supported: a single "benchmark" (one program, e.g. for a
 * quick smoke test) or a "benchmarks" list (a full experiment run - the same
 * ga/strategy/output settings applied across every benchmark in the suite,
 * so hyperparameters live in exactly one place instead of being duplicated
 * and risking drift between benchmark config files).
 */
public class ExperimentConfig {
	public BenchmarkConfig benchmark;
	public List<BenchmarkConfig> benchmarks;
	public GAConfig ga;
	public String strategy;
	public OutputConfig output;

	public static ExperimentConfig load(String path) throws IOException {
		return new ObjectMapper().readValue(new File(path), ExperimentConfig.class);
	}
}
