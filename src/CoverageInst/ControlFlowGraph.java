package CoverageInst;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One method's real control-flow graph at basic-block granularity - the
 * CoverageInst equivalent of ConcurrentTesting.Graph (which loads a
 * ValiPar-generated PCFG JSON file), built directly from bytecode instead
 * since CoverageInst has no external PCFG source. See BasicBlockInstrumenter
 * for how this is constructed and why the graph is scoped per-method.
 */
public final class ControlFlowGraph {

	private final String entryBlockId;
	private final Map<String, List<String>> successors;

	ControlFlowGraph(String entryBlockId, Map<String, List<String>> successors) {
		this.entryBlockId = entryBlockId;
		this.successors = successors;
	}

	public String entryBlockId() {
		return entryBlockId;
	}

	public Map<String, List<String>> successors() {
		return successors;
	}

	/**
	 * For a block whose last instruction is a recognized two-way numeric
	 * predicate (see BasicBlockInstrumenter/BranchDistance) - the successor
	 * reached when the jump IS taken. Meaningless (and not guaranteed
	 * present) for any other kind of block; callers must already know a
	 * block is such a predicate (e.g. via BranchDistance's block-predicate
	 * map) before calling this.
	 */
	public String takenSuccessor(String blockId) {
		List<String> succ = successors.get(blockId);
		return succ != null && succ.size() > 0 ? succ.get(0) : null;
	}

	/** Same as {@link #takenSuccessor}, for the fallthrough (jump NOT taken) direction. */
	public String fallthroughSuccessor(String blockId) {
		List<String> succ = successors.get(blockId);
		return succ != null && succ.size() > 1 ? succ.get(1) : null;
	}

	/**
	 * BFS shortest path from the method's entry block to targetBlockId,
	 * inclusive of both ends - mirrors ConcurrentTesting.BreadthFirstSearch,
	 * but returns an empty list when unreachable instead of assuming a path
	 * always exists (a real possibility here, unlike ValiPar's own
	 * required-elements set, which by construction is only ever built from
	 * paths already known to occur in some PCFG).
	 */
	public List<String> shortestPathTo(String targetBlockId) {
		if (entryBlockId.equals(targetBlockId)) {
			return List.of(entryBlockId);
		}
		Map<String, String> predecessor = new HashMap<>();
		Set<String> visited = new HashSet<>();
		Deque<String> queue = new ArrayDeque<>();
		queue.add(entryBlockId);
		visited.add(entryBlockId);
		boolean found = false;
		outer: while (!queue.isEmpty()) {
			String current = queue.poll();
			for (String next : successors.getOrDefault(current, List.of())) {
				if (!visited.add(next)) {
					continue;
				}
				predecessor.put(next, current);
				if (next.equals(targetBlockId)) {
					found = true;
					break outer;
				}
				queue.add(next);
			}
		}
		if (!found) {
			return List.of();
		}
		List<String> path = new ArrayList<>();
		String step = targetBlockId;
		while (step != null) {
			path.add(step);
			step = predecessor.get(step);
		}
		Collections.reverse(path);
		return path;
	}
}
