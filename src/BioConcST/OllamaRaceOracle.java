package BioConcST;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Direct Java port of llm-mutation/test_prompt_variants.py's call_ollama +
 * extract_choice - the exact HTTP contract validated empirically this
 * session (100% accuracy on a verifiable ground-truth task with a
 * chain-of-thought prompt): POST /api/generate, JSON body
 * {model, prompt, stream:false, options:{num_predict, temperature}},
 * response's "response" field holds raw text, the LAST "CHOICE: <label>"
 * match wins (chain-of-thought reasoning precedes the final answer).
 *
 * Any failure (timeout, non-200, unparseable/missing CHOICE line) returns
 * Optional.empty() - RaceChoiceMutator falls back to a uniform-random pick
 * rather than ever letting a flaky/unreachable model stall or crash the GA.
 */
public final class OllamaRaceOracle {

	private final HttpClient client = HttpClient.newHttpClient();
	private final ObjectMapper mapper = new ObjectMapper();
	private final String endpoint;
	private final String model;
	private final int timeoutMs;

	public OllamaRaceOracle(String endpoint, String model, int timeoutMs) {
		this.endpoint = endpoint;
		this.model = model;
		this.timeoutMs = timeoutMs > 0 ? timeoutMs : 30000;
	}

	/**
	 * candidateLabels are matched verbatim against "CHOICE: &lt;label&gt;" in
	 * the model's response; the returned Optional, when present, is an index
	 * into candidateLabels (never a value outside its bounds).
	 */
	public Optional<Integer> chooseWinner(String prompt, List<String> candidateLabels) {
		try {
			// 350 (the prototype scripts' value, tuned against much smaller
			// synthetic snippets) was observed this session to cut off a
			// real benchmark's chain-of-thought reasoning before it ever
			// reached the final CHOICE line - see DebugRacePrompt's
			// diagnostic run. Real source is longer, so the budget needs to
			// scale with it.
			Map<String, Object> body = Map.of("model", model, "prompt", prompt, "stream", false, "options",
					Map.of("num_predict", 700, "temperature", 0.7));
			HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
					.timeout(Duration.ofMillis(timeoutMs)).header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				return Optional.empty();
			}
			JsonNode root = mapper.readTree(response.body());
			String text = root.path("response").asText("");

			String escapedAlternatives = candidateLabels.stream().map(Pattern::quote)
					.reduce((a, b) -> a + "|" + b).orElse("");
			Matcher matcher = Pattern.compile("CHOICE:\\s*(" + escapedAlternatives + ")").matcher(text);
			String last = null;
			while (matcher.find()) {
				last = matcher.group(1);
			}
			if (last == null) {
				return Optional.empty();
			}
			int index = candidateLabels.indexOf(last);
			return index >= 0 ? Optional.of(index) : Optional.empty();
		} catch (Exception e) {
			return Optional.empty();
		}
	}
}
