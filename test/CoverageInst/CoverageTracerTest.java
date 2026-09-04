package CoverageInst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
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
	void resolveProcessIdReportsUnknownForAnAddressNoOneRegistered() throws Exception {
		File coverageDir = Files.createTempDirectory("coverageinst-tracer-test").toFile();
		Class<?> tracer = freshTracerClass(coverageDir, 0);
		Method resolveProcessId = tracer.getDeclaredMethod("resolveProcessId", String.class);
		resolveProcessId.setAccessible(true);

		String resolved = (String) resolveProcessId.invoke(null, "10.0.0.99:9999");

		assertTrue(resolved.startsWith("UNKNOWN("), "an address nobody registered must resolve to UNKNOWN(...), not throw: " + resolved);
	}
}
