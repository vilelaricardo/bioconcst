package CoverageInst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import CoverageInst.support.FixtureCompiler;

/**
 * Verifies ClassInstrumenter's injected bytecode actually behaves correctly
 * when run - not just that SyncPoints are discovered (ClassScannerTest
 * already covers that), but that the hooks fire in the right order with the
 * right edge ids AND that the original program's real behavior survives the
 * instrumentation unchanged. Loads the instrumented fixture through a
 * URLClassLoader whose parent is the PLATFORM loader (not the application
 * classloader) pointed at an isolated temp directory containing both the
 * instrumented fixture and a fake CoverageTracer - the platform loader
 * genuinely has no "CoverageInst.CoverageTracer" of its own (that's an
 * application class), so normal parent-first delegation naturally falls
 * through to the fake instead of ever touching the real one - no custom
 * loadClass override, no risk of the fake leaking onto the real test
 * classpath.
 */
class ClassInstrumenterTest {

	// Records every hook call instead of doing anything real (no sockets, no
	// files, no system properties) - exact same method signatures as the
	// real CoverageTracer's public API, since the instrumented bytecode's
	// INVOKESTATIC call sites are descriptor-exact.
	private static final String FAKE_TRACER_SOURCE = """
			package CoverageInst;

			import java.net.DatagramSocket;
			import java.net.DatagramPacket;
			import java.net.SocketAddress;
			import java.nio.channels.DatagramChannel;
			import java.util.ArrayList;
			import java.util.Collections;
			import java.util.List;

			public class CoverageTracer {
			    public static final List<String> calls = Collections.synchronizedList(new ArrayList<>());

			    public static void registerSocket(DatagramSocket socket) { calls.add("registerSocket"); }
			    public static void beforeSend(DatagramSocket socket, DatagramPacket packet, String edgeId) { calls.add("beforeSend:" + edgeId); }
			    public static void afterSend() { calls.add("afterSend"); }
			    public static void afterReceive(DatagramSocket socket, DatagramPacket packet, String edgeId) { calls.add("afterReceive:" + edgeId); }
			    public static void beforeSemaphoreRelease(Object semaphore, String edgeId) { calls.add("beforeSemaphoreRelease:" + edgeId); }
			    public static void afterSemaphoreAcquire(Object semaphore, String edgeId) { calls.add("afterSemaphoreAcquire:" + edgeId); }
			    public static void beforeChannelSend(SocketAddress destination, String edgeId) { calls.add("beforeChannelSend:" + edgeId); }
			    public static void afterChannelSend(DatagramChannel channel) { calls.add("afterChannelSend"); }
			    public static void afterChannelReceive(SocketAddress sender, String edgeId) { calls.add("afterChannelReceive:" + edgeId); }
			    public static void atBarrier(Object barrier, String edgeId) { calls.add("atBarrier:" + edgeId); }
			    public static void beforeThreadStart(Object thread, String edgeId) { calls.add("beforeThreadStart:" + edgeId); }
			}
			""";

	/** Compiles className+source alongside the fake tracer, instruments className in place, returns a loader over the result. */
	private static URLClassLoader instrument(String className, String source) throws Exception {
		Map<String, File> compiled = FixtureCompiler.compile(Map.of("CoverageTracer", FAKE_TRACER_SOURCE, className, source));
		File classFile = compiled.get(className);

		byte[] original;
		try (FileInputStream in = new FileInputStream(classFile)) {
			original = in.readAllBytes();
		}
		ClassReader reader = new ClassReader(original);
		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
		reader.accept(new ClassInstrumenter(writer, className, new ArrayList<>()), ClassReader.EXPAND_FRAMES);
		try (FileOutputStream out = new FileOutputStream(classFile)) {
			out.write(writer.toByteArray());
		}

		// The fixture class is always package-less (matching every real
		// benchmark class in this project), so its .class file sits directly
		// at the compilation output root - the same root CoverageTracer.class
		// landed under CoverageInst/ within.
		File outDir = classFile.getParentFile();
		return new URLClassLoader(new URL[] { outDir.toURI().toURL() }, ClassLoader.getPlatformClassLoader());
	}

	@SuppressWarnings("unchecked")
	private static List<String> runAndGetCalls(String className, String source) throws Exception {
		URLClassLoader loader = instrument(className, source);
		Class<?> fixtureClass = loader.loadClass(className);
		Method main = fixtureClass.getMethod("main", String[].class);
		main.invoke(null, (Object) new String[0]);

		Class<?> tracerClass = loader.loadClass("CoverageInst.CoverageTracer");
		return (List<String>) tracerClass.getField("calls").get(null);
	}

	@Test
	void injectsSocketHooksInTheRightOrderAndPreservesRealDeliveryOverLoopback() throws Exception {
		List<String> calls = runAndGetCalls("EchoFixture", """
				import java.net.DatagramSocket;
				import java.net.DatagramPacket;
				import java.net.InetAddress;

				public class EchoFixture {
				    public static void main(String[] args) throws Exception {
				        DatagramSocket socket = new DatagramSocket();
				        byte[] outBuf = "ping".getBytes();
				        DatagramPacket outPacket = new DatagramPacket(outBuf, outBuf.length,
				                InetAddress.getLoopbackAddress(), socket.getLocalPort());
				        socket.send(outPacket);

				        byte[] inBuf = new byte[16];
				        DatagramPacket inPacket = new DatagramPacket(inBuf, inBuf.length);
				        socket.receive(inPacket);

				        String received = new String(inPacket.getData(), 0, inPacket.getLength());
				        if (!received.equals("ping")) {
				            throw new AssertionError("expected loopback delivery to be unaffected by instrumentation, got: " + received);
				        }
				    }
				}
				""");

		assertEquals(List.of("registerSocket", "beforeSend:EchoFixture#main:0", "afterSend",
				"afterReceive:EchoFixture#main:1"), calls);
	}

	@Test
	void injectsSemaphoreHooksAndPreservesRealAcquireReleaseOrdering() throws Exception {
		List<String> calls = runAndGetCalls("SemFixture", """
				import java.util.concurrent.Semaphore;

				public class SemFixture {
				    public static void main(String[] args) throws InterruptedException {
				        Semaphore sem = new Semaphore(0);
				        sem.release();
				        sem.acquire();
				    }
				}
				""");

		assertEquals(List.of("beforeSemaphoreRelease:SemFixture#main:0", "afterSemaphoreAcquire:SemFixture#main:1"),
				calls);
	}

	@Test
	void injectsDatagramChannelSendHooksWithoutCorruptingTheStack() throws Exception {
		// Regression test for the LocalVariablesSorter bug: the channel
		// reference has to survive the DUP/LDC/INVOKESTATIC sequence AND the
		// real send() call to be re-loaded for afterChannelSend, all while
		// leaving the real send()'s int return value (byte count) correctly
		// in place for the surrounding code to use.
		List<String> calls = runAndGetCalls("ChannelFixture", """
				import java.net.InetSocketAddress;
				import java.net.StandardProtocolFamily;
				import java.nio.ByteBuffer;
				import java.nio.channels.DatagramChannel;

				public class ChannelFixture {
				    public static void main(String[] args) throws Exception {
				        DatagramChannel channel = DatagramChannel.open();
				        channel.bind(null);
				        ByteBuffer buffer = ByteBuffer.wrap("ping".getBytes());
				        int sent = channel.send(buffer, new InetSocketAddress("localhost", channel.socket().getLocalPort()));
				        if (sent != 4) {
				            throw new AssertionError("expected the real send()'s int result (4 bytes) to survive instrumentation, got: " + sent);
				        }
				        channel.close();
				    }
				}
				""");

		assertEquals(List.of("beforeChannelSend:ChannelFixture#main:0", "afterChannelSend"), calls);
	}

	@Test
	void injectsCyclicBarrierHookWithoutCorruptingTheRealArrivalIndex() throws Exception {
		// Regression test for the other stack-manipulation hook (alongside
		// DatagramChannel#send): await()'s original int arrival-index return
		// value sits under the barrier reference on the stack after the
		// real call - a SWAP brings the saved barrier ref back on top so
		// atBarrier gets the reference (not the int), while the int stays
		// correctly positioned underneath for the surrounding code to use.
		List<String> calls = runAndGetCalls("BarrierFixture", """
				import java.util.concurrent.CyclicBarrier;

				public class BarrierFixture {
				    public static void main(String[] args) throws Exception {
				        CyclicBarrier barrier = new CyclicBarrier(1);
				        int arrivalIndex = barrier.await();
				        if (arrivalIndex != 0) {
				            throw new AssertionError("expected the real await()'s int result (arrival index 0 for a "
				                    + "single-party barrier) to survive instrumentation, got: " + arrivalIndex);
				        }
				    }
				}
				""");

		assertEquals(List.of("atBarrier:BarrierFixture#main:0"), calls);
	}

	@Test
	void injectsLockHooksThroughTheInterfaceTypeAndPreservesRealMutualExclusion() throws Exception {
		// Regression test for the exact-owner scope limit: a field declared
		// as the Lock INTERFACE (INVOKEINTERFACE dispatch) must fire the
		// same hooks as a concrete ReentrantLock-typed field would
		// (INVOKEVIRTUAL) - ClassScannerTest already proved both are
		// discovered statically; this proves the injected calls actually
		// execute correctly at runtime for the interface-dispatched case too.
		List<String> calls = runAndGetCalls("LockFixture", """
				import java.util.concurrent.locks.Lock;
				import java.util.concurrent.locks.ReentrantLock;

				public class LockFixture {
				    public static void main(String[] args) {
				        Lock lock = new ReentrantLock();
				        lock.lock();
				        int x = 1 + 1;
				        lock.unlock();
				        if (x != 2) {
				            throw new AssertionError("expected real lock/unlock to leave normal execution untouched, got x=" + x);
				        }
				    }
				}
				""");

		assertEquals(
				List.of("afterSemaphoreAcquire:LockFixture#main:0", "beforeSemaphoreRelease:LockFixture#main:1"),
				calls);
	}

	@Test
	void aProcessWithNoRecognizedPrimitivesInvokesNoHooksAtAll() throws Exception {
		List<String> calls = runAndGetCalls("Plain", """
				public class Plain {
				    public static void main(String[] args) {
				        System.out.println("hi");
				    }
				}
				""");

		assertTrue(calls.isEmpty());
	}
}
