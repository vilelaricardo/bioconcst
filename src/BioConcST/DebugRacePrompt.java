package BioConcST;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import CoverageInst.RacePoint;
import CoverageInst.RequiredEdge;

/**
 * One-off diagnostic: builds the exact same prompt RaceChoiceMutator would
 * send for quorum-handshake's race point, calls the real Ollama oracle, and
 * prints BOTH the prompt and the raw response - used to debug why the pilot
 * run's LLM success rate was near zero (almost every mutation attempt fell
 * back to random).
 *
 * Usage: java BioConcST.DebugRacePrompt <ollamaEndpoint> <model>
 */
public class DebugRacePrompt {

	public static void main(String[] args) throws Exception {
		String endpoint = args[0];
		String model = args[1];

		RacePoint racePoint = new RacePoint(0, List.of(1, 2),
				List.of(new RequiredEdge(RequiredEdge.Kind.MESSAGE, 1, "Peer#main:0", 0, "Coordinator#main:0"),
						new RequiredEdge(RequiredEdge.Kind.MESSAGE, 2, "Peer#main:0", 0, "Coordinator#main:0"),
						new RequiredEdge(RequiredEdge.Kind.MESSAGE, 1, "Peer#main:1", 0, "Coordinator#main:1")));

		ProcessSpec coordinator = new ProcessSpec();
		coordinator.id = 0;
		coordinator.className = "Coordinator";
		ProcessSpec peer1 = new ProcessSpec();
		peer1.id = 1;
		peer1.className = "Peer";
		ProcessSpec peer2 = new ProcessSpec();
		peer2.id = 2;
		peer2.className = "Peer";
		List<ProcessSpec> testSetupProcesses = List.of(coordinator, peer1, peer2);

		File benchmarkSourceDir = new File("./benchmark/synthetic/001_quorum_handshake");

		// Peer1's value (500) is inside the [480,519] window (votes QUORUM);
		// Peer2's value (200) is outside it (votes NORMAL) - a real
		// individual's actual launch args, so the model has enough
		// information to determine which branch each candidate takes.
		List<CoverageInst.CoverageInstRun.ProcessLaunchSpec> launchSpecs = List.of(
				new CoverageInst.CoverageInstRun.ProcessLaunchSpec(0, "Coordinator", new String[0]),
				new CoverageInst.CoverageInstRun.ProcessLaunchSpec(1, "Peer", new String[] { "1", "500" }),
				new CoverageInst.CoverageInstRun.ProcessLaunchSpec(2, "Peer", new String[] { "2", "200" }));

		String prompt = RacePromptBuilder.build(racePoint, racePoint.relatedEdges, testSetupProcesses,
				benchmarkSourceDir, launchSpecs);
		System.out.println("=== PROMPT ===");
		System.out.println(prompt);
		System.out.println("=== END PROMPT (length=" + prompt.length() + " chars) ===");

		List<String> labels = RacePromptBuilder.candidateLabels(racePoint);
		System.out.println("Candidate labels: " + labels);

		System.out.println("=== RAW OLLAMA CALL (to see exactly what the model answers) ===");
		ObjectMapper mapper = new ObjectMapper();
		Map<String, Object> body = Map.of("model", model, "prompt", prompt, "stream", false, "options",
				Map.of("num_predict", 700, "temperature", 0.7));
		HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofMillis(60000))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
		HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
		System.out.println("HTTP status: " + response.statusCode());
		JsonNode root = mapper.readTree(response.body());
		String text = root.path("response").asText("");
		System.out.println("--- raw response text ---");
		System.out.println(text);
		System.out.println("--- end raw response text ---");

		OllamaRaceOracle oracle = new OllamaRaceOracle(endpoint, model, 60000);
		var choice = oracle.chooseWinner(prompt, labels);
		System.out.println("=== ORACLE RESULT (via the real production code path) ===");
		System.out.println(choice.isPresent() ? "Parsed choice index=" + choice.get() + " (" + labels.get(choice.get()) + ")"
				: "EMPTY - either HTTP failure or no CHOICE line matched");
	}
}
