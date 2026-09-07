package CoverageInst;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Opcodes;

import BioConcST.BenchmarkConfig;
import BioConcST.CoverageInstConfig;
import BioConcST.ExperimentConfig;
import BioConcST.ProcessSpec;
import BioConcST.RoleLinkSpec;

/**
 * Renders a benchmark's real PCFG as ONE combined Graphviz diagram - one
 * vertical "swim lane" per process instance, plain circular nodes, solid
 * edges for intra-process control flow, DASHED edges crossing (or, for
 * IDENTITY primitives, staying within) lanes for the actual synchronization
 * edges - deliberately matching the convention Souza et al. 2008 use in
 * their own Figure 4 (processes as labeled vertical lanes, numbered circles,
 * dotted inter-process edges) rather than inventing a new one: this is the
 * established, recognizable way this exact research area draws a PCFG, and
 * the user asked for that fidelity specifically after seeing an earlier,
 * fragmented (one image per process, no message edges at all) version.
 *
 * A required edge that's been covered (either from a single real run's
 * CoverageEvaluator result, or the cumulative union CoverageInstStrategy
 * tracks live across GA generations) is drawn bold green; everything
 * declared-but-not-yet-covered stays a thin gray dashed line - this puts the
 * coverage signal on the actual thing CoverageInst measures (sync EDGES),
 * rather than approximating it via node color the way the very first version
 * of this tool did.
 *
 * java CoverageInst.PcfgVisualizer <config.json> <outputDir> [coverageDir]
 *
 * Renders pcfg.svg alongside pcfg.dot automatically when a `dot` binary
 * (Graphviz) is found on PATH.
 */
public class PcfgVisualizer {

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("Usage: PcfgVisualizer <config.json> <outputDir> [coverageDir]");
			System.exit(1);
		}
		ExperimentConfig config = ExperimentConfig.load(args[0]);
		BenchmarkConfig benchmark = config.benchmark;
		File outputDir = new File(args[1]);
		File coverageDir = args.length > 2 ? new File(args[2]) : null;

		CoverageInstConfig ci = benchmark.coverageInst;
		if (ci == null) {
			System.err.println("benchmark.coverageInst is missing in " + args[0]);
			System.exit(1);
		}

		File workDir = new File("./pcfg-viz-work-" + benchmark.name);
		org.apache.commons.io.FileUtils.deleteQuietly(workDir);
		CoverageInstRun run = CoverageInstRun.prepare(new File(benchmark.path), workDir);

		List<ProcessInstance> processes = new ArrayList<>();
		List<Integer> processIds = new ArrayList<>();
		for (ProcessSpec spec : benchmark.testSetupProcesses) {
			List<String> classNames = ci.extraClassesByRole != null
					&& ci.extraClassesByRole.containsKey(spec.className) ? ci.extraClassesByRole.get(spec.className)
							: List.of(spec.className);
			processes.add(ClassScanner.scanProcess(run.instrumentedDir(), spec.id, spec.className, classNames));
			processIds.add(spec.id);
		}

		List<RoleLink> roleLinks = new ArrayList<>();
		if (ci.roleLinks != null) {
			for (RoleLinkSpec link : ci.roleLinks) {
				roleLinks.add(new RoleLink(link.from, link.to));
			}
		}
		Topology topology = new Topology(roleLinks, ci.fixedMessageTargets != null ? ci.fixedMessageTargets : Map.of(),
				ci.fixedMessageSources != null ? ci.fixedMessageSources : Map.of(),
				ci.identityGroups != null ? ci.identityGroups : Map.of(),
				ci.chainedDistance != null ? ci.chainedDistance : Map.of());
		List<RequiredEdge> required = RequiredElementsGenerator.generate(processes, topology);

		Set<String> coveredEdgeKeys = new HashSet<>();
		if (coverageDir != null) {
			CoverageEvaluator.Result result = CoverageEvaluator.evaluate(required, coverageDir, processIds);
			for (RequiredEdge edge : result.covered) {
				coveredEdgeKeys.add(edge.toString());
			}
		}

		// Publication-oriented rendering: a separate, more complete graph
		// than the one GraphDistance actually searches with (see
		// AcademicCfgBuilder's own javadoc) - follows every same-class call
		// regardless of whether the callee has a sync point, and duplicates
		// a method called from several sites once per call site instead of
		// sharing one node, matching how a person would draw this PCFG by
		// hand rather than the search's flow-insensitive shortcut.
		Map<String, ControlFlowGraph> academicGraphs = new LinkedHashMap<>();
		Map<String, String> academicSyncEdgeBlocks = new LinkedHashMap<>();
		Map<String, Integer> academicBranchPredicates = new LinkedHashMap<>();
		File[] classFiles = run.instrumentedDir().listFiles((dir, name) -> name.endsWith(".class"));
		if (classFiles != null) {
			for (File classFile : classFiles) {
				String cn = classFile.getName().substring(0, classFile.getName().length() - ".class".length());
				BasicBlockInstrumenter.ClassResult academic = AcademicCfgBuilder.build(classFile, cn);
				academicGraphs.putAll(academic.graphs);
				academicSyncEdgeBlocks.putAll(academic.syncEdgeBlocks);
				academicBranchPredicates.putAll(academic.branchPredicates);
			}
		}

		renderCombined(outputDir, processes, academicGraphs, academicSyncEdgeBlocks, academicBranchPredicates, required,
				coveredEdgeKeys);

		System.out.println("Wrote " + new File(outputDir, "pcfg.dot").getAbsolutePath()
				+ (isDotAvailable() ? " and pcfg.svg" : " (install Graphviz's `dot` to also render pcfg.svg)"));
	}

	/**
	 * Renders (and overwrites) outputDir/pcfg.dot [+ .svg] - safe to call
	 * repeatedly (e.g. once per GA generation) for a live "what has the
	 * search found so far" view.
	 */
	public static void renderCombined(File outputDir, List<ProcessInstance> processes,
			Map<String, ControlFlowGraph> graphs, Map<String, String> syncEdgeBlocks,
			Map<String, Integer> branchPredicates, List<RequiredEdge> required, Set<String> coveredEdgeKeys)
			throws IOException {
		Files.createDirectories(outputDir.toPath());
		File dotFile = new File(outputDir, "pcfg.dot");
		writeCombinedDot(dotFile, processes, graphs, syncEdgeBlocks, branchPredicates, required, coveredEdgeKeys);
		if (isDotAvailable()) {
			renderSvg(dotFile);
		}
	}

	private static void writeCombinedDot(File dotFile, List<ProcessInstance> processes,
			Map<String, ControlFlowGraph> graphs, Map<String, String> syncEdgeBlocks,
			Map<String, Integer> branchPredicates, List<RequiredEdge> required, Set<String> coveredEdgeKeys)
			throws IOException {
		try (PrintWriter w = new PrintWriter(dotFile)) {
			w.println("digraph PCFG {");
			w.println("  rankdir=TB;");
			w.println(
					"  node [fontname=\"Helvetica\", fontsize=11, shape=circle, style=filled, fillcolor=white, fixedsize=true, width=0.45, height=0.45];");
			w.println("  edge [fontname=\"Helvetica\", fontsize=9];");

			for (ProcessInstance process : processes) {
				LinkedHashSet<String> methodKeys = new LinkedHashSet<>();
				for (SyncPoint sp : process.syncPoints) {
					methodKeys.add(sp.edgeId.substring(0, sp.edgeId.lastIndexOf(':')));
				}

				w.println("  subgraph cluster_p" + process.processId + " {");
				w.println("    label=\"" + escape(process.role) + " (p" + process.processId + ")\";");
				w.println("    fontname=\"Helvetica\"; fontsize=12; style=dashed; color=gray50;");

				// Several method keys in one process can share the exact
				// same (merged) ControlFlowGraph object - interprocedural
				// linking/inlining registers a call-connected component's
				// graph under every member method's key (see
				// BasicBlockInstrumenter/AcademicCfgBuilder). Drawing by
				// object identity, not by key, is what keeps a shared graph
				// from being emitted twice into the .dot file - harmless
				// for Graphviz's own node/edge dedup, but real duplicate
				// <text> elements at the same SVG coordinates read as
				// bolded/blurred labels, which matters for a diagram meant
				// for publication.
				Set<ControlFlowGraph> drawnGraphs = Collections.newSetFromMap(new IdentityHashMap<>());
				for (String methodKey : methodKeys) {
					ControlFlowGraph graph = graphs.get(methodKey);
					if (graph == null || !drawnGraphs.add(graph)) {
						continue;
					}
					for (String blockId : graph.successors().keySet()) {
						writeCombinedNode(w, process.processId, blockId, graph, branchPredicates);
					}
				}
				drawnGraphs.clear();
				for (String methodKey : methodKeys) {
					ControlFlowGraph graph = graphs.get(methodKey);
					if (graph == null || !drawnGraphs.add(graph)) {
						continue;
					}
					for (Map.Entry<String, List<String>> entry : graph.successors().entrySet()) {
						String from = prefixedId(process.processId, entry.getKey());
						boolean twoWay = branchPredicates.containsKey(entry.getKey()) && entry.getValue().size() == 2;
						List<String> succ = entry.getValue();
						for (int i = 0; i < succ.size(); i++) {
							String to = prefixedId(process.processId, succ.get(i));
							String label = twoWay ? (i == 0 ? "T" : "F") : "";
							w.println("    \"" + from + "\" -> \"" + to + "\""
									+ (label.isEmpty() ? "" : " [label=\"" + label + "\"]") + ";");
						}
					}
				}
				w.println("  }");
			}

			// The actual synchronization edges - dashed, and NOT allowed to
			// influence layout (constraint=false) so a benchmark with lots of
			// declared-but-uncovered possibilities doesn't distort the clean
			// vertical swim lanes above. Message edges cross clusters;
			// identity edges (same senderProcessId/receiverProcessId) curve
			// within one.
			for (RequiredEdge edge : required) {
				String fromBlock = syncEdgeBlocks.get(edge.senderEdgeId);
				String toBlock = syncEdgeBlocks.get(edge.receiverEdgeId);
				if (fromBlock == null || toBlock == null) {
					continue;
				}
				String from = prefixedId(edge.senderProcessId, fromBlock);
				String to = prefixedId(edge.receiverProcessId, toBlock);
				boolean covered = coveredEdgeKeys.contains(edge.toString());
				String color = covered ? "forestgreen" : "gray70";
				String penwidth = covered ? "2.2" : "1";
				w.println("  \"" + from + "\" -> \"" + to + "\" [style=dashed, constraint=false, color=\"" + color
						+ "\", penwidth=" + penwidth + ", tooltip=\"" + escape(edge.toString()) + "\"];");
			}

			w.println("}");
		}
	}

	private static void writeCombinedNode(PrintWriter w, int processId, String blockId, ControlFlowGraph graph,
			Map<String, Integer> branchPredicates) {
		String nodeId = prefixedId(processId, blockId);
		boolean isEntry = blockId.equals(graph.entryBlockId());
		Integer predicateOpcode = branchPredicates.get(blockId);
		String shape = predicateOpcode != null ? "diamond" : "circle";
		String label = shortBlockName(blockId);
		String penwidth = isEntry ? "2.5" : "1";

		StringBuilder tooltip = new StringBuilder(blockId);
		if (predicateOpcode != null) {
			tooltip.append(" - ").append(opcodeName(predicateOpcode));
		}

		w.println("    \"" + nodeId + "\" [label=\"" + label + "\", shape=" + shape + ", penwidth=" + penwidth
				+ ", tooltip=\"" + escape(tooltip.toString()) + "\"];");
	}

	private static String prefixedId(int processId, String blockId) {
		return "p" + processId + "_" + blockId;
	}

	private static String shortBlockName(String blockId) {
		int idx = blockId.lastIndexOf(':');
		return idx == -1 ? blockId : blockId.substring(idx + 1);
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static String opcodeName(int opcode) {
		return switch (opcode) {
			case Opcodes.IF_ICMPEQ -> "IF_ICMPEQ";
			case Opcodes.IFEQ -> "IFEQ";
			case Opcodes.IF_ICMPNE -> "IF_ICMPNE";
			case Opcodes.IFNE -> "IFNE";
			case Opcodes.IF_ICMPLT -> "IF_ICMPLT";
			case Opcodes.IFLT -> "IFLT";
			case Opcodes.IF_ICMPGE -> "IF_ICMPGE";
			case Opcodes.IFGE -> "IFGE";
			case Opcodes.IF_ICMPGT -> "IF_ICMPGT";
			case Opcodes.IFGT -> "IFGT";
			case Opcodes.IF_ICMPLE -> "IF_ICMPLE";
			case Opcodes.IFLE -> "IFLE";
			default -> "opcode " + opcode;
		};
	}

	private static boolean isDotAvailable() {
		try {
			Process p = new ProcessBuilder("dot", "-V").redirectErrorStream(true).start();
			return p.waitFor() == 0;
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}

	private static void renderSvg(File dotFile) {
		try {
			String svgPath = dotFile.getAbsolutePath().replaceAll("\\.dot$", ".svg");
			Process p = new ProcessBuilder("dot", "-Tsvg", dotFile.getAbsolutePath(), "-o", svgPath)
					.redirectErrorStream(true).start();
			p.waitFor();
		} catch (IOException | InterruptedException e) {
			System.err.println("PcfgVisualizer: failed to render " + dotFile + " to SVG: " + e);
		}
	}
}
