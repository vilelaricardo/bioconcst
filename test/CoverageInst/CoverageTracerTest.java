package CoverageInst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * CoverageTracer's registry (registerSocket/appendToRegistry/
 * resolveProcessId) is what lets a receiver figure out WHICH process a
 * packet actually came from, purely by matching source addresses against
 * files on disk - real runtime behavior, not something that fits the
 * synthetic-trace-file style of CoverageEvaluatorTest/ReplayScheduleTest.
 *
 * CoverageTracer keys its behavior off "coverage.dir"/"coverage.processId"
 * system properties read into static final fields ONCE when the class
 * first loads - shared, mutable JVM-wide state that's exactly wrong to
 * fight against in-process. Each test here instead loads a completely
 * FRESH copy of the real CoverageTracer.class (from target/classes) through
 * an isolated URLClassLoader, setting the system properties immediately
 * before forcing that specific copy's class initialization - so every test
 * gets its own independently-initialized instance, regardless of what any
 * other test (or class) already did to the "main" copy on the normal
 * classpath.
 */
class CoverageTracerTest {

	private static Class<?> freshTracerClass(File coverageDir, int processId) throws Exception {
		String previousDir = System.getProperty("coverage.dir");
		String previousPid = System.getProperty("coverage.processId");
		System.setProperty("coverage.dir", coverageDir.getAbsolutePath());
		System.setProperty("coverage.processId", String.valueOf(processId));
		try {
			File classesDir = new File("target/classes").getAbsoluteFile();
			URLClassLoader loader = new URLClassLoader(new URL[] { classesDir.toURI().toURL() },
					ClassLoader.getPlatformClassLoader());
			return Class.forName("CoverageInst.CoverageTracer", true, loader);
		} finally {
			restore("coverage.dir", previousDir);
			restore("coverage.processId", previousPid);
		}
	}

	private static void restore(String key, String previousValue) {
		if (previousValue != null) {
			System.setProperty(key, previousValue);
		} else {
			System.clearProperty(key);
		}
	}

	private static File registryFileFor(File coverageDir, int processId) {
		return new File(new File(coverageDir, "registry"), processId + ".addr");
	}

	@Test
	void registeringTwoDistinctSocketsForOneProcessRecordsBothAddresses() throws Exception {
		// Regression test for a real bug found migrating
		// 010_token_ring_both_directions_different_primitives: a single
		// "registered" boolean let only the FIRST socket register, silently
		// leaving anything sent/received on the second unresolvable forever.
		File coverageDir = Files.createTempDirectory("coverageinst-tracer-test").toFile();
		Class<?> tracer = freshTracerClass(coverageDir, 0);
		Method registerSocket = tracer.getMethod("registerSocket", DatagramSocket.class);

		try (DatagramSocket first = new DatagramSocket(); DatagramSocket second = new DatagramSocket()) {
			registerSocket.invoke(null, first);
			registerSocket.invoke(null, second);
		}

		List<String> lines = Files.readAllLines(registryFileFor(coverageDir, 0).toPath());
		assertEquals(2, lines.size(), "both sockets must be registered, not just the first: " + lines);
		assertTrue(!lines.get(0).equals(lines.get(1)), "two different sockets have two different ports: " + lines);
	}

	@Test
	void registeringTheSameSocketTwiceRecordsItOnlyOnce() throws Exception {
		File coverageDir = Files.createTempDirectory("coverageinst-tracer-test").toFile();
		Class<?> tracer = freshTracerClass(coverageDir, 0);
		Method registerSocket = tracer.getMethod("registerSocket", DatagramSocket.class);

		try (DatagramSocket socket = new DatagramSocket()) {
			registerSocket.invoke(null, socket);
			registerSocket.invoke(null, socket);
		}

		List<String> lines = Files.readAllLines(registryFileFor(coverageDir, 0).toPath());
		assertEquals(1, lines.size(), "the same socket registered twice must be de-duplicated: " + lines);
	}

	@Test
	void resolveProcessIdFindsTheProcessThatRegisteredAMatchingAddress() throws Exception {
		File coverageDir = Files.createTempDirectory("coverageinst-tracer-test").toFile();
		Class<?> tracer = freshTracerClass(coverageDir, 1);
		Method registerSocket = tracer.getMethod("registerSocket", DatagramSocket.class);
		Method resolveProcessId = tracer.getDeclaredMethod("resolveProcessId", String.class);
		resolveProcessId.setAccessible(true);

		String registeredAddress;
		try (DatagramSocket socket = new DatagramSocket()) {
			registerSocket.invoke(null, socket);
			registeredAddress = Files.readAllLines(registryFileFor(coverageDir, 1).toPath()).get(0);
		}

		String resolved = (String) resolveProcessId.invoke(null, registeredAddress);
		assertEquals("1", resolved);
	}

	@Test
	void beforeSendWritesATraceLineInTheExactFormatCoverageEvaluatorParses() throws Exception {
		// CoverageEvaluatorTest only ever exercises synthetic, hand-written
		// trace files - this is the other half of that contract: the REAL
		// CoverageTracer must actually produce a line that parses back to
		// the same "SEND edgeId resolvedProcessId" shape.
		File coverageDir = Files.createTempDirectory("coverageinst-tracer-test").toFile();
		Files.createDirectories(new File(coverageDir, "registry").toPath());
		Files.writeString(new File(coverageDir, "registry/1.addr").toPath(), "127.0.0.1:9999\n");

		Class<?> tracer = freshTracerClass(coverageDir, 0);
		Method beforeSend = tracer.getMethod("beforeSend", DatagramSocket.class, DatagramPacket.class, String.class);
		Method afterSend = tracer.getMethod("afterSend");
		Method flush = tracer.getDeclaredMethod("flush");
		flush.setAccessible(true);

		try (DatagramSocket socket = new DatagramSocket()) {
			byte[] buf = "hi".getBytes();
			DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName("127.0.0.1"), 9999);
			beforeSend.invoke(null, socket, packet, "Fixture#main:0");
			afterSend.invoke(null);
		}
		flush.invoke(null);

		List<String> lines = Files.readAllLines(new File(coverageDir, "trace-0.log").toPath());
		assertEquals(1, lines.size());
		String[] parts = lines.get(0).split(" ");
		assertEquals("SEND", parts[0]);
		assertEquals("Fixture#main:0", parts[1]);
		assertEquals("1", parts[2], "the destination address registered by process 1 must resolve to processId 1");
	}

	@Test
	void afterReceiveWritesATraceLineInTheExactFormatCoverageEvaluatorParses() throws Exception {
		File coverageDir = Files.createTempDirectory("coverageinst-tracer-test").toFile();
		Files.createDirectories(new File(coverageDir, "registry").toPath());
		Files.writeString(new File(coverageDir, "registry/2.addr").toPath(), "127.0.0.1:8888\n");

		Class<?> tracer = freshTracerClass(coverageDir, 0);
		Method afterReceive = tracer.getMethod("afterReceive", DatagramSocket.class, DatagramPacket.class,
				String.class);
		Method flush = tracer.getDeclaredMethod("flush");
		flush.setAccessible(true);

		try (DatagramSocket socket = new DatagramSocket()) {
			byte[] buf = "hi".getBytes();
			DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName("127.0.0.1"), 8888);
			afterReceive.invoke(null, socket, packet, "Fixture#main:1");
		}
		flush.invoke(null);

		List<String> lines = Files.readAllLines(new File(coverageDir, "trace-0.log").toPath());
		assertEquals(1, lines.size());
		String[] parts = lines.get(0).split(" ");
		assertEquals("RECEIVE", parts[0]);
		assertEquals("Fixture#main:1", parts[1]);
		assertEquals("2", parts[2], "the source address registered by process 2 must resolve to processId 2");
	}

	@Test
	void resolveProcessIdReportsUnknownForAnAddressNoOneRegistered() throws Exception {
		File coverageDir = Files.createTempDirectory("coverageinst-tracer-test").toFile();
		Class<?> tracer = freshTracerClass(coverageDir, 0);
		Method resolveProcessId = tracer.getDeclaredMethod("resolveProcessId", String.class);
		resolveProcessId.setAccessible(true);

		String resolved = (String) resolveProcessId.invoke(null, "10.0.0.99:9999");

		assertTrue(resolved.startsWith("UNKNOWN("), "an address nobody registered must resolve to UNKNOWN(...), not throw: " + resolved);
	}
}
