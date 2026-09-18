package CoverageInst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import CoverageInst.support.FixtureCompiler;

/**
 * Pins down CrossProcessFlagResolver against the exact FlagChainStress
 * fixture (benchmark/synthetic/007_flag_chain_stress) that this analysis was
 * built and reviewed against - see sync-claude-codex.md (2026-09-15/16
 * entries) for the full design discussion, including the ambiguity trap
 * Codex's review caught (Source has TWO structurally-compatible sends -
 * TRIGGER and SKIP - to Relay's one receive; picking the wrong one would
 * silently redirect fitness gradient towards the wrong branch).
 */
class CrossProcessFlagResolverTest {

	private static final File BENCH_DIR = new File("benchmark/synthetic/007_flag_chain_stress");

	@Test
	void resolvesRelayMainOneAsGuardedBySourceMainZeroThroughReceive() throws Exception {
		File relayClass = new File(BENCH_DIR, "Relay.class");
		File sourceClass = new File(BENCH_DIR, "Source.class");

		List<CrossProcessFlagResolver.Suggestion> suggestions = CrossProcessFlagResolver.resolve(relayClass, "Relay",
				Map.of("Source", sourceClass));

		Optional<CrossProcessFlagResolver.Suggestion> resolved = suggestions.stream()
				.filter(s -> s.status() == CrossProcessFlagResolver.Status.RESOLVED).findFirst();
		assertTrue(resolved.isPresent(), "expected exactly one resolved cross-process association, got: " + suggestions);

		CrossProcessFlagResolver.Suggestion s = resolved.get();
		assertEquals("Relay#main:1", s.targetEdgeId());
		assertEquals("Relay#main:0", s.viaReceiverEdge());
		assertEquals("Source#main:0", s.targetSenderEdge());
		// The composed local step: FlagResolver's own resolution of
		// Source's inWindow flag should come along for free, unmodified.
		assertEquals(2, s.senderLocalSources().size(),
				"expected the sender's own local flag sources (B0/B1) to be composed in");
	}

	@Test
	void doesNotPickAnArbitrarySenderWhenNoLiteralDistinguishesCandidates() throws Exception {
		// Ambiguity fixture: two sends with IDENTICAL literal payload, so
		// literal-based disambiguation cannot tell them apart - must be
		// reported ambiguous, never an arbitrary pick.
		String source = """
				import java.net.*;
				import java.io.*;
				public class AmbiguousSource {
				    public static void main(String[] args) throws IOException {
				        DatagramSocket socket = new DatagramSocket();
				        InetAddress addr = InetAddress.getLoopbackAddress();
				        int value = Integer.parseInt(args[0]);
				        if (value > 0) {
				            byte[] buf = "SAME".getBytes();
				            DatagramPacket p = new DatagramPacket(buf, buf.length, addr, 1);
				            socket.send(p);
				        } else {
				            byte[] buf = "SAME".getBytes();
				            DatagramPacket p = new DatagramPacket(buf, buf.length, addr, 1);
				            socket.send(p);
				        }
				        socket.close();
				    }
				}
				""";
		File senderFile = FixtureCompiler.compileOne("AmbiguousSource", source);

		String receiverSource = """
				import java.net.*;
				import java.io.*;
				public class AmbiguousRelay {
				    public static void main(String[] args) throws IOException {
				        DatagramSocket socket = new DatagramSocket();
				        InetAddress addr = InetAddress.getLoopbackAddress();
				        byte[] receiveBuffer = new byte[255];
				        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
				        socket.receive(receivePacket);
				        String received = new String(receivePacket.getData()).trim();
				        if (received.equals("SAME")) {
				            byte[] fwd = "FORWARD".getBytes();
				            DatagramPacket p = new DatagramPacket(fwd, fwd.length, addr, 1);
				            socket.send(p);
				        }
				        socket.close();
				    }
				}
				""";
		File receiverFile = FixtureCompiler.compileOne("AmbiguousRelay", receiverSource);

		List<CrossProcessFlagResolver.Suggestion> suggestions = CrossProcessFlagResolver.resolve(receiverFile,
				"AmbiguousRelay", Map.of("AmbiguousSource", senderFile));

		assertTrue(suggestions.stream().anyMatch(s -> s.status() == CrossProcessFlagResolver.Status.AMBIGUOUS),
				"expected an AMBIGUOUS suggestion when two candidate sends share the same literal, got: " + suggestions);
		assertFalse(suggestions.stream().anyMatch(s -> s.status() == CrossProcessFlagResolver.Status.RESOLVED),
				"must never resolve arbitrarily when candidates are indistinguishable");
	}

	@Test
	void reportsNothingWhenThePredicateDoesNotDeriveFromAReceive() throws Exception {
		// Negative fixture: a predicate on a purely local value - must not
		// be mistaken for a message-derived p-use.
		String source = """
				import java.net.*;
				import java.io.*;
				public class LocalOnlyRelay {
				    public static void main(String[] args) throws IOException {
				        DatagramSocket socket = new DatagramSocket();
				        InetAddress addr = InetAddress.getLoopbackAddress();
				        String local = "SOMETHING";
				        if (local.equals("SAME")) {
				            byte[] fwd = "FORWARD".getBytes();
				            DatagramPacket p = new DatagramPacket(fwd, fwd.length, addr, 1);
				            socket.send(p);
				        }
				        socket.close();
				    }
				}
				""";
		File classFile = FixtureCompiler.compileOne("LocalOnlyRelay", source);

		List<CrossProcessFlagResolver.Suggestion> suggestions = CrossProcessFlagResolver.resolve(classFile,
				"LocalOnlyRelay", Map.of());

		assertTrue(suggestions.isEmpty(),
				"a predicate over a purely local value must not produce any suggestion (resolved or otherwise): "
						+ suggestions);
	}

	@Test
	void doesNotResolveNegatedEqualsPredicateBecauseLiteralWouldPointAtWrongSender() throws Exception {
		String senderSource = """
				import java.net.*;
				import java.io.*;
				public class NegatedSource {
				    public static void main(String[] args) throws IOException {
				        DatagramSocket socket = new DatagramSocket();
				        InetAddress addr = InetAddress.getLoopbackAddress();
				        int value = Integer.parseInt(args[0]);
				        if (value > 0) {
				            byte[] buf = "TRIGGER".getBytes();
				            DatagramPacket p = new DatagramPacket(buf, buf.length, addr, 1);
				            socket.send(p);
				        } else {
				            byte[] buf = "SKIP".getBytes();
				            DatagramPacket p = new DatagramPacket(buf, buf.length, addr, 1);
				            socket.send(p);
				        }
				        socket.close();
				    }
				}
				""";
		File senderFile = FixtureCompiler.compileOne("NegatedSource", senderSource);

		String receiverSource = """
				import java.net.*;
				import java.io.*;
				public class NegatedRelay {
				    public static void main(String[] args) throws IOException {
				        DatagramSocket socket = new DatagramSocket();
				        InetAddress addr = InetAddress.getLoopbackAddress();
				        byte[] receiveBuffer = new byte[255];
				        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
				        socket.receive(receivePacket);
				        String received = new String(receivePacket.getData()).trim();
				        if (!received.equals("TRIGGER")) {
				            byte[] fwd = "FORWARD".getBytes();
				            DatagramPacket p = new DatagramPacket(fwd, fwd.length, addr, 1);
				            socket.send(p);
				        }
				        socket.close();
				    }
				}
				""";
		File receiverFile = FixtureCompiler.compileOne("NegatedRelay", receiverSource);

		List<CrossProcessFlagResolver.Suggestion> suggestions = CrossProcessFlagResolver.resolve(receiverFile,
				"NegatedRelay", Map.of("NegatedSource", senderFile));

		assertFalse(suggestions.stream().anyMatch(s -> s.status() == CrossProcessFlagResolver.Status.RESOLVED),
				"a negated equals predicate must not resolve by matching the positive literal sender: " + suggestions);
	}

	@Test
	void usesClassWideSyncEdgeNumberingAcrossMultipleMethods() throws Exception {
		// ClassInstrumenter assigns edge ids with one counter per class, not
		// per method. This fixture puts sync points in helper methods before
		// main, so a method-local counter would produce wrong ids even though
		// the simple FlagChainStress fixture still passes.
		String senderSource = """
				import java.net.*;
				import java.io.*;
				public class MultiMethodSource {
				    static void noise(DatagramSocket socket, InetAddress addr) throws IOException {
				        byte[] buf = "NOISE".getBytes();
				        DatagramPacket p = new DatagramPacket(buf, buf.length, addr, 1);
				        socket.send(p);
				    }
				    public static void main(String[] args) throws IOException {
				        DatagramSocket socket = new DatagramSocket();
				        InetAddress addr = InetAddress.getLoopbackAddress();
				        int value = Integer.parseInt(args[0]);
				        if (value > 0) {
				            byte[] buf = "TRIGGER".getBytes();
				            DatagramPacket p = new DatagramPacket(buf, buf.length, addr, 1);
				            socket.send(p);
				        } else {
				            byte[] buf = "SKIP".getBytes();
				            DatagramPacket p = new DatagramPacket(buf, buf.length, addr, 1);
				            socket.send(p);
				        }
				        socket.close();
				    }
				}
				""";
		File senderFile = FixtureCompiler.compileOne("MultiMethodSource", senderSource);

		String receiverSource = """
				import java.net.*;
				import java.io.*;
				public class MultiMethodRelay {
				    static void warmup(DatagramSocket socket, DatagramPacket packet) throws IOException {
				        socket.receive(packet);
				    }
				    public static void main(String[] args) throws IOException {
				        DatagramSocket socket = new DatagramSocket();
				        InetAddress addr = InetAddress.getLoopbackAddress();
				        byte[] receiveBuffer = new byte[255];
				        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
				        socket.receive(receivePacket);
				        String received = new String(receivePacket.getData()).trim();
				        if (received.equals("TRIGGER")) {
				            byte[] fwd = "FORWARD".getBytes();
				            DatagramPacket p = new DatagramPacket(fwd, fwd.length, addr, 1);
				            socket.send(p);
				        }
				        socket.close();
				    }
				}
				""";
		File receiverFile = FixtureCompiler.compileOne("MultiMethodRelay", receiverSource);

		List<CrossProcessFlagResolver.Suggestion> suggestions = CrossProcessFlagResolver.resolve(receiverFile,
				"MultiMethodRelay", Map.of("MultiMethodSource", senderFile));

		Optional<CrossProcessFlagResolver.Suggestion> resolved = suggestions.stream()
				.filter(s -> s.status() == CrossProcessFlagResolver.Status.RESOLVED).findFirst();
		assertTrue(resolved.isPresent(), "expected one resolved suggestion, got: " + suggestions);
		CrossProcessFlagResolver.Suggestion s = resolved.get();
		assertEquals("MultiMethodRelay#main:2", s.targetEdgeId());
		assertEquals("MultiMethodRelay#main:1", s.viaReceiverEdge());
		assertEquals("MultiMethodSource#main:1", s.targetSenderEdge());
	}
}
