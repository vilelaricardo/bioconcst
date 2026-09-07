package BioConcST;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import CoverageInst.RacePoint;
import CoverageInst.RequiredEdge;

/**
 * Builds the chain-of-thought prompt validated this session
 * (llm-mutation/test_prompt_variants.py's variant_chain_of_thought - 100%
 * accuracy on a verifiable ground-truth task): real source code of the
 * receiver and every candidate sender, the still-uncovered required edges
 * at this race point, a "think step by step" scaffold, ending in a
 * machine-parseable CHOICE line.
 */
public final class RacePromptBuilder {

	private RacePromptBuilder() {
	}

	/** Stable, regex-safe labels tied to process identity - "Process<id>". */
	public static List<String> candidateLabels(RacePoint racePoint) {
		return racePoint.candidateSenderIds.stream().map(id -> "Process" + id).toList();
	}

	public static String build(RacePoint racePoint, List<RequiredEdge> uncoveredRelatedEdges,
			List<ProcessSpec> testSetupProcesses, File benchmarkSourceDir,
			List<CoverageInst.CoverageInstRun.ProcessLaunchSpec> launchSpecs) {
		Map<Integer, String> classNameByProcessId = new LinkedHashMap<>();
		for (ProcessSpec spec : testSetupProcesses) {
			classNameByProcessId.put(spec.id, spec.className);
		}
		// The ACTUAL argument values this individual will launch each
		// process with (post-TESTDATA substitution) - without this, the
		// model has no way to determine which branch a candidate's code
		// will actually take (e.g. quorum-handshake's QUORUM/NORMAL split
		// depends entirely on the numeric value each Peer was given).
		Map<Integer, String> launchArgsByProcessId = new LinkedHashMap<>();
		for (CoverageInst.CoverageInstRun.ProcessLaunchSpec spec : launchSpecs) {
			launchArgsByProcessId.put(spec.processId, String.join(" ", spec.args));
		}
		List<String> labels = candidateLabels(racePoint);

		StringBuilder prompt = new StringBuilder();
		prompt.append("You are the mutation operator of a genetic algorithm generating test\n")
				.append("schedules for a concurrent Java program. Your job is to pick which sender\n")
				.append("process should be forced to arrive FIRST at an ambiguous receive point, in\n")
				.append("order to make progress on still-uncovered required sync edges.\n\n");

		String receiverClass = classNameByProcessId.get(racePoint.destinationProcessId);
		prompt.append("Source code of the receiving process (process ").append(racePoint.destinationProcessId)
				.append(", class ").append(receiverClass).append("):\n")
				.append(readSource(benchmarkSourceDir, receiverClass)).append("\n\n");

		// Candidates that share the same class (e.g. quorum-handshake's two
		// Peer instances) get that source printed ONCE, not once per
		// candidate - repeating an identical multi-hundred-line file per
		// candidate was observed to push real prompts past num_predict's
		// budget before the model ever reached the final CHOICE line (see
		// DebugRacePrompt's diagnostic run this session).
		Map<String, List<String>> labelsByClass = new LinkedHashMap<>();
		for (int i = 0; i < racePoint.candidateSenderIds.size(); i++) {
			int senderId = racePoint.candidateSenderIds.get(i);
			String senderClass = classNameByProcessId.get(senderId);
			String args = launchArgsByProcessId.getOrDefault(senderId, "?");
			labelsByClass.computeIfAbsent(senderClass, k -> new java.util.ArrayList<>())
					.add(labels.get(i) + " (process " + senderId + ", will run as: java " + senderClass + " " + args
							+ ")");
		}
		for (Map.Entry<String, List<String>> entry : labelsByClass.entrySet()) {
			prompt.append("Source code shared by candidate sender(s) ").append(String.join(", ", entry.getValue()))
					.append(" (class ").append(entry.getKey()).append("):\n")
					.append(readSource(benchmarkSourceDir, entry.getKey())).append("\n\n");
		}

		prompt.append("Required sync edges at this receive point still UNCOVERED across many generations:\n");
		for (RequiredEdge edge : uncoveredRelatedEdges) {
			prompt.append("  - ").append(edge).append("\n");
		}
		prompt.append("\n");

		prompt.append("Task: pick exactly ONE candidate sender to force to arrive first on the next\n")
				.append("test execution, in order to make progress on covering the edges listed above.\n\n");

		prompt.append("Think step by step BEFORE answering:\n")
				.append("Step 1: What does each candidate sender's code actually send/do?\n")
				.append("Step 2: Given the still-uncovered edges above, which candidate arriving\n")
				.append("first would help cover one of them?\n\n");

		prompt.append("After your reasoning, end your response with EXACTLY this final line:\n")
				.append("CHOICE: <").append(String.join(" or ", labels)).append(">\n");
		return prompt.toString();
	}

	private static String readSource(File benchmarkSourceDir, String className) {
		if (className == null) {
			return "(source unavailable)";
		}
		try {
			return Files.readString(new File(benchmarkSourceDir, className + ".java").toPath());
		} catch (IOException e) {
			return "(source unavailable: " + className + ".java)";
		}
	}
}
