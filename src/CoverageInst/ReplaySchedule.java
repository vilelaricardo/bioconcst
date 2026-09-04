package CoverageInst;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlled execution / replay for CoverageInst, scoped to the one real
 * source of non-determinism these benchmarks have: when a receiving process
 * calls receive() more than once and more than one other process could be
 * the sender, WHICH physical sender's packet arrives at which call is a
 * genuine OS/network race (semaphore and barrier handoffs have no such
 * ambiguity - they're always 1:1 or symmetric, so nothing here touches
 * them).
 *
 * A schedule is simply, per destination processId, the order senderProcessId
 * values were observed arriving during a free (unconstrained) run - read
 * directly off that destination's own trace log, since RECEIVE events are
 * logged in the exact order the receive() calls actually happened. Replay
 * doesn't touch the receiver at all: it only makes each SENDER wait its
 * turn before actually sending (see CoverageTracer.beforeSend), so the OS
 * delivers packets to the (unmodified) sequential receive() calls in the
 * same order as the original run - without needing to know, from the
 * sender's side, which specific receive call site a message will land on.
 *
 * Serialized as a trivial line-based format (no JSON dependency needed here):
 * one line per destination, "destProcessId sender1,sender2,sender3".
 */
public final class ReplaySchedule {

	private final Map<Integer, List<Integer>> senderOrderByDestination;

	private ReplaySchedule(Map<Integer, List<Integer>> senderOrderByDestination) {
		this.senderOrderByDestination = senderOrderByDestination;
	}

	public List<Integer> senderOrderFor(int destinationProcessId) {
		return senderOrderByDestination.getOrDefault(destinationProcessId, List.of());
	}

	/** Builds a schedule from a completed free run's trace logs. */
	public static ReplaySchedule buildFromTrace(File coverageDir, List<Integer> processIds) throws IOException {
		Map<Integer, List<Integer>> order = new LinkedHashMap<>();
		for (int processId : processIds) {
			File log = new File(coverageDir, "trace-" + processId + ".log");
			if (!log.exists()) {
				continue;
			}
			List<Integer> senders = new ArrayList<>();
			for (String line : Files.readAllLines(log.toPath())) {
				if (line.isBlank()) {
					continue;
				}
				String[] parts = line.split(" ");
				if (!parts[0].equals("RECEIVE")) {
					continue;
				}
				try {
					senders.add(Integer.parseInt(parts[2]));
				} catch (NumberFormatException e) {
					// UNKNOWN(...) correlation - can't schedule around an
					// unresolved sender; leave it out rather than guess.
				}
			}
			if (!senders.isEmpty()) {
				order.put(processId, senders);
			}
		}
		return new ReplaySchedule(order);
	}

	public void save(File file) throws IOException {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<Integer, List<Integer>> entry : senderOrderByDestination.entrySet()) {
			sb.append(entry.getKey()).append(' ');
			List<String> senders = entry.getValue().stream().map(String::valueOf).toList();
			sb.append(String.join(",", senders)).append('\n');
		}
		Files.writeString(file.toPath(), sb.toString());
	}

	public static ReplaySchedule load(File file) throws IOException {
		return fromInlineString(Files.readString(file.toPath()).replace('\n', ';'));
	}

	/** Same "destProcessId sender1,sender2" grammar as save()/load(), joined with ";" so it fits in one text field. */
	public String toInlineString() {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Map.Entry<Integer, List<Integer>> entry : senderOrderByDestination.entrySet()) {
			if (!first) {
				sb.append(';');
			}
			first = false;
			sb.append(entry.getKey()).append(' ');
			List<String> senders = entry.getValue().stream().map(String::valueOf).toList();
			sb.append(String.join(",", senders));
		}
		return sb.toString();
	}

	public static ReplaySchedule fromInlineString(String inline) {
		Map<Integer, List<Integer>> order = new LinkedHashMap<>();
		for (String line : inline.split(";")) {
			if (line.isBlank()) {
				continue;
			}
			String[] parts = line.trim().split(" ");
			int destProcessId = Integer.parseInt(parts[0]);
			List<Integer> senders = new ArrayList<>();
			if (parts.length > 1 && !parts[1].isBlank()) {
				for (String s : parts[1].split(",")) {
					senders.add(Integer.parseInt(s));
				}
			}
			order.put(destProcessId, senders);
		}
		return new ReplaySchedule(order);
	}
}
