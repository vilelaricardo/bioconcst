package CoverageInst;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime half of CoverageInst. Called from bytecode injected by
 * ClassInstrumenter at each recognized synchronization primitive.
 *
 * Unlike ValiPar's static analysis - which has to GUESS which send can
 * reach which receive, because it never sees the program run - this runs
 * inside the real process and reads the real destination/sender address
 * off the real DatagramPacket. Sync-edge identity is resolved from a
 * tiny file-based registry (one file per processId, written the moment
 * that process's socket is constructed) instead of inferred: a send's
 * destination address is looked up against the registry to find WHICH
 * processId it actually reaches; a receive's sender address (which UDP
 * hands you for free via DatagramPacket.getAddress()/getPort() after
 * receive() returns) is looked up the same way. Two processes that are
 * the same class (like Peer x2) never get confused, because the lookup
 * is against real bound socket addresses, not source-level identity -
 * and a send whose destination is chosen at runtime via a file-exchanged
 * address (exactly what every synthetic benchmark's HelperClass does)
 * resolves correctly because we're reading the actual address that made
 * it into the packet, not trying to trace the value statically.
 *
 * Configured via two system properties, set per-process by the test
 * harness: "coverage.dir" (a directory shared by every process in one
 * test case - the registry and trace logs live under it) and
 * "coverage.processId" (this process's id, matching the same numbering
 * used by testSetupProcesses).
 */
public final class CoverageTracer {

	private static final File COVERAGE_DIR = new File(System.getProperty("coverage.dir", "./coverage"));
	private static final String PROCESS_ID = System.getProperty("coverage.processId", "?");
	private static final File REGISTRY_DIR = new File(COVERAGE_DIR, "registry");
	private static final File TRACE_LOG = new File(COVERAGE_DIR, "trace-" + PROCESS_ID + ".log");
	// Registration happens within milliseconds of socket construction in
	// practice (it's the first thing beforeSend/afterReceive/registerSocket
	// do) - 1.5s is already generous slack, not a tight budget. Kept short
	// on purpose: a real stuck/never-registering process should fail fast
	// and loud (see the warning in resolveProcessId), not silently stall
	// every unresolved lookup for 5s each, which is exactly what let stale
	// background processes from earlier, unrelated runs pile up and
	// interleave trace output during CoverageInst's own development.
	private static final int REGISTRY_LOOKUP_TIMEOUT_MS = 1500;
	private static final int REGISTRY_LOOKUP_POLL_MS = 10;

	// Replay mode (controlled execution, see ReplaySchedule's own javadoc for
	// the full rationale): opt-in via "coverage.replaySchedule" pointing at a
	// schedule file saved from a prior free run. When set, beforeSend/
	// beforeChannelSend block the sender until the shared per-destination
	// turn file says it's this process's recorded turn, then advance that
	// turn file only after the real send() has actually happened (afterSend/
	// afterChannelSend) - never before, so a later-scheduled sender can never
	// race ahead of a send that's merely been "decided" but not yet executed.
	private static final String REPLAY_SCHEDULE_PATH = System.getProperty("coverage.replaySchedule");
	private static volatile ReplaySchedule replaySchedule;
	private static final Map<Integer, Integer> occurrencesSentSoFar = new ConcurrentHashMap<>();
	private static final int MY_PROCESS_ID_INT = parseProcessIdOrMinusOne();
	// [destProcessIdInt, turnIndex] of the wait this thread most recently
	// performed in beforeSend/beforeChannelSend, consumed by the matching
	// afterSend/afterChannelSend once the real send has executed. A
	// ThreadLocal (not a field) because nothing else here assumes a single
	// sending thread per process - CoverageInst's other primitives don't
	// either.
	private static final ThreadLocal<int[]> pendingTurnAdvance = new ThreadLocal<>();

	// Identity-keyed, not a single flag: a process can construct MORE than
	// one DatagramSocket of its own - e.g. a regular point-to-point socket
	// AND a separate MulticastSocket for a group send/receive (both are
	// DatagramSocket subtypes, so both go through this same hook) - and
	// each needs its OWN address registered, or whichever socket registers
	// second would previously be silently skipped, leaving anything sent
	// or received on it unresolvable forever (found while migrating
	// 010_token_ring_both_directions_different_primitives's multicast
	// broadcast step).
	private static final java.util.Set<DatagramSocket> registeredSockets = Collections
			.newSetFromMap(new java.util.IdentityHashMap<>());
	private static volatile boolean channelRegistered = false;

	// Basic-block entry events (see BasicBlockInstrumenter) fire vastly more
	// often than sync-point events - opening/closing a FileWriter per event
	// (the original approach) would seriously erode CoverageInst's own
	// performance advantage over ValiPar. Buffered in memory instead, flushed
	// once at JVM shutdown. A process killed by TLE (destroyForcibly, not a
	// normal exit) never runs its shutdown hook and loses its buffered tail -
	// same effect as today's "missing/incomplete trace" case, already handled
	// by CoverageInstFitnessFunction's allProcessesCompleted fallback.
	private static final List<String> traceBuffer = Collections.synchronizedList(new ArrayList<>());

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(CoverageTracer::flush));
	}

	private CoverageTracer() {
	}

	private static int parseProcessIdOrMinusOne() {
		try {
			return Integer.parseInt(PROCESS_ID);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static ReplaySchedule loadedSchedule() {
		if (REPLAY_SCHEDULE_PATH == null) {
			return null;
		}
		ReplaySchedule loaded = replaySchedule;
		if (loaded == null) {
			synchronized (CoverageTracer.class) {
				loaded = replaySchedule;
				if (loaded == null) {
					try {
						loaded = ReplaySchedule.load(new File(REPLAY_SCHEDULE_PATH));
					} catch (IOException e) {
						throw new RuntimeException(
								"CoverageTracer: failed to load replay schedule " + REPLAY_SCHEDULE_PATH, e);
					}
					replaySchedule = loaded;
				}
			}
		}
		return loaded;
	}

	public static synchronized void registerSocket(DatagramSocket socket) {
		if (!registeredSockets.add(socket)) {
			return;
		}
		try {
			// An unbound DatagramSocket()'s local address is the wildcard
			// (0.0.0.0 / ::), valid for receiving on any interface but
			// useless as an address other processes can send *to* -
			// substitute the machine's real address, same fix as
			// ValiPar's own UddiRequest.register() needed (see
			// project_valipar_compat memory).
			java.net.InetAddress localAddress = socket.getLocalAddress();
			if (localAddress.isAnyLocalAddress()) {
				localAddress = java.net.InetAddress.getLocalHost();
			}
			String address = localAddress.getHostAddress() + ":" + socket.getLocalPort();
			appendToRegistry(address);
		} catch (IOException e) {
			throw new RuntimeException("CoverageTracer: failed to register socket for process " + PROCESS_ID, e);
		}
	}

	// A process can be reachable under MORE than one address: its own
	// DatagramSocket (used for blocking receive()) has a different local
	// port than a DatagramChannel it separately opens for non-blocking
	// send() - a channel isn't bound to a real port until its first send()
	// actually completes, which is exactly why this runs from
	// afterChannelSend (never before) rather than alongside beforeChannelSend
	// the way registerSocket runs alongside beforeSend/afterReceive. Both
	// addresses have to resolve back to this SAME processId, so the registry
	// file holds one address per line instead of a single value - see
	// resolveProcessId's matching side.
	public static synchronized void registerChannel(java.nio.channels.DatagramChannel channel) {
		if (channelRegistered) {
			return;
		}
		try {
			java.net.SocketAddress local = channel.getLocalAddress();
			if (!(local instanceof InetSocketAddress inet)) {
				channelRegistered = true;
				return;
			}
			java.net.InetAddress localAddress = inet.getAddress();
			if (localAddress == null || localAddress.isAnyLocalAddress()) {
				localAddress = java.net.InetAddress.getLocalHost();
			}
			String address = localAddress.getHostAddress() + ":" + inet.getPort();
			appendToRegistry(address);
			channelRegistered = true;
		} catch (IOException e) {
			throw new RuntimeException("CoverageTracer: failed to register channel for process " + PROCESS_ID, e);
		}
	}

	private static void appendToRegistry(String address) throws IOException {
		REGISTRY_DIR.mkdirs();
		File myFile = new File(REGISTRY_DIR, PROCESS_ID + ".addr");
		try (PrintWriter w = new PrintWriter(new FileWriter(myFile, true))) {
			w.println(address);
		}
	}

	public static void beforeSend(DatagramSocket socket, DatagramPacket packet, String edgeId) {
		registerSocket(socket);
		// A multicast/broadcast group address (e.g. MulticastSocket's own
		// send()) never belongs to any single process's own registry entry -
		// resolveProcessId would burn the full REGISTRY_LOOKUP_TIMEOUT_MS on
		// every single call before giving up, for nothing. It genuinely does
		// reach every group member at once, so it's recorded as such
		// directly - see CoverageEvaluator's matching side for how a
		// "BROADCAST" correlation satisfies every compatible receiver
		// instead of one specific processId. Not scheduled under replay
		// mode either: there's no "whose turn" to coordinate for a send
		// that isn't racing against any other sender for one recipient.
		if (packet.getAddress().isMulticastAddress()) {
			writeTrace("SEND", edgeId, "BROADCAST");
			return;
		}
		String destAddress = packet.getAddress().getHostAddress() + ":" + packet.getPort();
		String destProcessId = resolveProcessId(destAddress);
		waitForTurnIfReplaying(destProcessId);
		writeTrace("SEND", edgeId, destProcessId);
	}

	/** Runs immediately after the real send() - see the replay-mode comment above. */
	public static void afterSend() {
		advanceTurnIfPending();
	}

	public static void afterReceive(DatagramSocket socket, DatagramPacket packet, String edgeId) {
		registerSocket(socket);
		String senderAddress = packet.getAddress().getHostAddress() + ":" + packet.getPort();
		String senderProcessId = resolveProcessId(senderAddress);
		writeTrace("RECEIVE", edgeId, senderProcessId);
	}

	public static void beforeSemaphoreRelease(Object semaphore, String edgeId) {
		writeTrace("SEM_RELEASE", edgeId, String.valueOf(System.identityHashCode(semaphore)));
	}

	public static void afterSemaphoreAcquire(Object semaphore, String edgeId) {
		writeTrace("SEM_ACQUIRE", edgeId, String.valueOf(System.identityHashCode(semaphore)));
	}

	// DatagramChannel's send/receive carry the destination/source address as
	// a plain SocketAddress (a real InetSocketAddress in practice) instead
	// of a DatagramPacket - same resolution idea as beforeSend/afterReceive,
	// just a different shape. receive() returns null when a non-blocking
	// poll finds nothing yet (its own "testOperation" semantics, per
	// ValiPar's own java.primitives.json) - that's not an event, so it's
	// not logged at all.
	public static void beforeChannelSend(SocketAddress destination, String edgeId) {
		String destProcessId = resolveSocketAddress(destination);
		waitForTurnIfReplaying(destProcessId);
		writeTrace("SEND", edgeId, destProcessId);
	}

	/**
	 * Runs immediately after the real channel send() - mirrors afterSend().
	 * Also where the channel's own local address gets registered (see
	 * registerChannel's javadoc for why it can't happen any earlier).
	 */
	public static void afterChannelSend(java.nio.channels.DatagramChannel channel) {
		registerChannel(channel);
		advanceTurnIfPending();
	}

	public static void afterChannelReceive(SocketAddress sender, String edgeId) {
		if (sender == null) {
			return;
		}
		String senderProcessId = resolveSocketAddress(sender);
		writeTrace("RECEIVE", edgeId, senderProcessId);
	}

	private static String resolveSocketAddress(SocketAddress address) {
		if (!(address instanceof InetSocketAddress inetAddress)) {
			return "UNKNOWN(non-inet:" + address + ")";
		}
		return resolveProcessId(inetAddress.getAddress().getHostAddress() + ":" + inetAddress.getPort());
	}

	// CyclicBarrier#await() is symmetric - every party both "arrives" and
	// "is released" at the same call, unlike Semaphore's directional
	// release/acquire - so there's just one hook, logged once the party
	// actually reaches the barrier (matching everything else here: log the
	// real event, not an inferred one).
	public static void atBarrier(Object barrier, String edgeId) {
		writeTrace("BARRIER", edgeId, String.valueOf(System.identityHashCode(barrier)));
	}

	public static void beforeThreadStart(Object thread, String edgeId) {
		writeTrace("THREAD_START", edgeId, String.valueOf(System.identityHashCode(thread)));
	}

	private static String resolveProcessId(String address) {
		long deadline = System.currentTimeMillis() + REGISTRY_LOOKUP_TIMEOUT_MS;
		while (System.currentTimeMillis() < deadline) {
			String[] files = REGISTRY_DIR.list();
			if (files != null) {
				for (String fileName : files) {
					if (!fileName.endsWith(".addr")) {
						continue;
					}
					try {
						// One address per line - a process can be registered
						// under more than one (its DatagramSocket's port AND
						// a separately-opened DatagramChannel's own port -
						// see registerChannel).
						for (String line : Files.readAllLines(new File(REGISTRY_DIR, fileName).toPath())) {
							if (line.trim().equals(address)) {
								return fileName.substring(0, fileName.length() - ".addr".length());
							}
						}
					} catch (IOException e) {
						// Registry file may be mid-write from another process; retry.
					}
				}
			}
			try {
				Thread.sleep(REGISTRY_LOOKUP_POLL_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		System.err.println("CoverageTracer WARNING: process " + PROCESS_ID + " could not resolve " + address
				+ " against the registry within " + REGISTRY_LOOKUP_TIMEOUT_MS
				+ "ms - either a topology/registration bug or a genuinely slow process");
		return "UNKNOWN(" + address + ")";
	}

	// Only ever called when destProcessId resolved to a real numeric id - an
	// UNKNOWN(...) destination can't be scheduled around, so it falls back to
	// running free (same as a schedule that simply never recorded this send).
	private static void waitForTurnIfReplaying(String destProcessId) {
		ReplaySchedule schedule = loadedSchedule();
		if (schedule == null) {
			return;
		}
		int destProcessIdInt;
		try {
			destProcessIdInt = Integer.parseInt(destProcessId);
		} catch (NumberFormatException e) {
			return;
		}
		List<Integer> order = schedule.senderOrderFor(destProcessIdInt);
		// Which occurrence (0-based) this is, among all of MY sends to this
		// same destination - needed to disambiguate repeated sends the same
		// pair of processes makes over the course of one run.
		int occurrence = occurrencesSentSoFar.merge(destProcessIdInt, 1, Integer::sum) - 1;
		int myTurnIndex = -1;
		int seen = 0;
		for (int i = 0; i < order.size(); i++) {
			if (order.get(i) == MY_PROCESS_ID_INT) {
				if (seen == occurrence) {
					myTurnIndex = i;
					break;
				}
				seen++;
			}
		}
		if (myTurnIndex == -1) {
			// The free run's schedule doesn't cover this send (e.g. the
			// mutated test data now sends one extra time) - nothing to
			// replay against, so let it through unconstrained.
			pendingTurnAdvance.remove();
			return;
		}
		File turnFile = new File(COVERAGE_DIR, "replay-turn-" + destProcessIdInt + ".txt");
		// Waiting here means waiting on ANOTHER process's send, not just a
		// local file registration - give it a much longer budget than
		// REGISTRY_LOOKUP_TIMEOUT_MS before giving up and running free.
		long deadline = System.currentTimeMillis() + (long) REGISTRY_LOOKUP_TIMEOUT_MS * 20;
		while (System.currentTimeMillis() < deadline) {
			if (readTurn(turnFile) >= myTurnIndex) {
				pendingTurnAdvance.set(new int[] { destProcessIdInt, myTurnIndex });
				return;
			}
			try {
				Thread.sleep(REGISTRY_LOOKUP_POLL_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				pendingTurnAdvance.remove();
				return;
			}
		}
		System.err.println("CoverageTracer WARNING: process " + PROCESS_ID + " timed out waiting for replay turn "
				+ myTurnIndex + " sending to process " + destProcessIdInt
				+ " - a scheduled sender may be stuck, or this replay run is genuinely diverging from the recorded one");
		pendingTurnAdvance.remove();
	}

	private static void advanceTurnIfPending() {
		int[] pending = pendingTurnAdvance.get();
		if (pending == null) {
			return;
		}
		pendingTurnAdvance.remove();
		writeTurn(new File(COVERAGE_DIR, "replay-turn-" + pending[0] + ".txt"), pending[1] + 1);
	}

	private static synchronized int readTurn(File turnFile) {
		if (!turnFile.exists()) {
			return 0;
		}
		try {
			String value = Files.readString(turnFile.toPath()).trim();
			return value.isEmpty() ? 0 : Integer.parseInt(value);
		} catch (IOException e) {
			return 0;
		}
	}

	private static synchronized void writeTurn(File turnFile, int value) {
		try (PrintWriter w = new PrintWriter(new FileWriter(turnFile))) {
			w.print(value);
		} catch (IOException e) {
			throw new RuntimeException("CoverageTracer: failed to write replay turn file " + turnFile, e);
		}
	}

	private static void writeTrace(String event, String edgeId, String correlation) {
		traceBuffer.add(event + " " + edgeId + " " + correlation + " " + System.nanoTime());
	}

	/**
	 * Fired at the entry of every basic block (see BasicBlockInstrumenter) -
	 * the PCFG-distance equivalent of ValiPar's own "NodeEvent" trace line
	 * (Souza et al. 2008): unlike the sync-point events above, this isn't
	 * about coverage identity at all, it's what lets GraphDistance compare a
	 * shortest-path-to-target against what the process actually executed.
	 */
	public static void atNode(String nodeId) {
		writeTrace("NODE", nodeId, "-");
	}

	/** Captures the real operands of a recognized two-operand numeric predicate (IF_ICMPxx) - see BranchDistance. */
	public static void atBranch2(int a, int b, String blockId) {
		writeTrace("BRANCH2", blockId, a + "," + b);
	}

	/** Same as atBranch2, for a single-operand predicate compared against zero (IFxx). */
	public static void atBranch1(int a, String blockId) {
		writeTrace("BRANCH1", blockId, String.valueOf(a));
	}

	private static void flush() {
		if (traceBuffer.isEmpty()) {
			return;
		}
		COVERAGE_DIR.mkdirs();
		try (PrintWriter w = new PrintWriter(new FileWriter(TRACE_LOG, true))) {
			synchronized (traceBuffer) {
				for (String line : traceBuffer) {
					w.println(line);
				}
			}
		} catch (IOException e) {
			System.err.println("CoverageTracer: failed to flush trace buffer for process " + PROCESS_ID + ": " + e);
		}
	}
}
