package CoverageInst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import CoverageInst.support.FixtureCompiler;

/**
 * ClassScanner runs the exact same ClassInstrumenter pass the real
 * instrumentation pipeline uses, just to collect discovered SyncPoints
 * without writing the result anywhere - so these tests exercise real
 * javac-emitted bytecode (via FixtureCompiler) through the real recognizer,
 * covering both "is this primitive found at all" and "are edge ids assigned
 * in the right order, continuing across methods within one class".
 */
class ClassScannerTest {

	@Test
	void findsADatagramSendFollowedByAReceiveInCallOrder() throws Exception {
		File classFile = FixtureCompiler.compileOne("Fixture", """
				import java.net.DatagramSocket;
				import java.net.DatagramPacket;
				import java.net.InetAddress;

				public class Fixture {
				    public static void main(String[] args) throws Exception {
				        DatagramSocket socket = new DatagramSocket();
				        byte[] buf = new byte[10];
				        DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getLocalHost(), 9999);
				        socket.send(packet);
				        socket.receive(packet);
				    }
				}
				""");

		List<SyncPoint> points = ClassScanner.scan(classFile, "Fixture");

		assertEquals(2, points.size());
		assertEquals(SyncPoint.Kind.SEND, points.get(0).kind);
		assertEquals("Fixture#main:0", points.get(0).edgeId);
		assertEquals(SyncPoint.Kind.RECEIVE, points.get(1).kind);
		assertEquals("Fixture#main:1", points.get(1).edgeId);
	}

	@Test
	void recognizesMulticastSocketTheSameAsPlainDatagramSocket() throws Exception {
		File classFile = FixtureCompiler.compileOne("MulticastFixture", """
				import java.net.MulticastSocket;
				import java.net.DatagramPacket;
				import java.net.InetAddress;

				public class MulticastFixture {
				    public static void main(String[] args) throws Exception {
				        MulticastSocket socket = new MulticastSocket(9999);
				        byte[] buf = new byte[10];
				        DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName("228.5.6.7"), 9999);
				        socket.send(packet);
				    }
				}
				""");

		List<SyncPoint> points = ClassScanner.scan(classFile, "MulticastFixture");

		assertEquals(1, points.size());
		assertEquals(SyncPoint.Kind.SEND, points.get(0).kind);
	}

	@Test
	void findsSemaphoreAcquireAndRelease() throws Exception {
		File classFile = FixtureCompiler.compileOne("SemFixture", """
				import java.util.concurrent.Semaphore;

				public class SemFixture {
				    public static void main(String[] args) throws InterruptedException {
				        Semaphore sem = new Semaphore(1);
				        sem.acquire();
				        sem.release();
				    }
				}
				""");

		List<SyncPoint> points = ClassScanner.scan(classFile, "SemFixture");

		assertEquals(List.of(SyncPoint.Kind.SEM_ACQUIRE, SyncPoint.Kind.SEM_RELEASE),
				points.stream().map(p -> p.kind).toList());
	}

	@Test
	void recognizesBothTheLockInterfaceAndTheConcreteReentrantLockType() throws Exception {
		// Regression test: a field declared as the Lock INTERFACE emits
		// INVOKEINTERFACE with owner=Lock, while one declared as the concrete
		// ReentrantLock type emits INVOKEVIRTUAL with owner=ReentrantLock -
		// both must be recognized, not just one.
		File classFile = FixtureCompiler.compileOne("LockFixture", """
				import java.util.concurrent.locks.Lock;
				import java.util.concurrent.locks.ReentrantLock;

				public class LockFixture {
				    static Lock lockField = new ReentrantLock();
				    static ReentrantLock concreteField = new ReentrantLock();

				    public static void main(String[] args) {
				        lockField.lock();
				        lockField.unlock();
				        concreteField.lock();
				        concreteField.unlock();
				    }
				}
				""");

		List<SyncPoint> points = ClassScanner.scan(classFile, "LockFixture");

		assertEquals(
				List.of(SyncPoint.Kind.SEM_ACQUIRE, SyncPoint.Kind.SEM_RELEASE, SyncPoint.Kind.SEM_ACQUIRE,
						SyncPoint.Kind.SEM_RELEASE),
				points.stream().map(p -> p.kind).toList());
	}

	@Test
	void findsMonitorEnterAndBothMonitorExitsForASynchronizedBlock() throws Exception {
		// synchronized(obj){} compiles to exactly one MONITORENTER but TWO
		// MONITOREXIT instructions - javac always adds a second, duplicate
		// one inside a synthetic catch-any handler whose only job is
		// "release the lock, then rethrow". Both are discovered here as
		// separate SEM_RELEASE sync points, even though only the first (the
		// normal-path one) will ever fire for a test case that doesn't
		// throw while holding the lock - see SyncPointMatcher.matchInsnKind's
		// javadoc for why this is accepted rather than filtered out.
		File classFile = FixtureCompiler.compileOne("MonitorFixture", """
				public class MonitorFixture {
				    public static void main(String[] args) {
				        Object lock = new Object();
				        synchronized (lock) {
				            System.out.println("in block");
				        }
				    }
				}
				""");

		List<SyncPoint> points = ClassScanner.scan(classFile, "MonitorFixture");

		assertEquals(List.of(SyncPoint.Kind.SEM_ACQUIRE, SyncPoint.Kind.SEM_RELEASE, SyncPoint.Kind.SEM_RELEASE),
				points.stream().map(p -> p.kind).toList());
		assertEquals(List.of("MonitorFixture#main:0", "MonitorFixture#main:1", "MonitorFixture#main:2"),
				points.stream().map(p -> p.edgeId).toList());
	}

	@Test
	void aClassWithNoRecognizedPrimitivesYieldsNoSyncPoints() throws Exception {
		File classFile = FixtureCompiler.compileOne("Plain", """
				public class Plain {
				    public static void main(String[] args) {
				        System.out.println("hi");
				    }
				}
				""");

		assertTrue(ClassScanner.scan(classFile, "Plain").isEmpty());
	}

	@Test
	void scanProcessCombinesSyncPointsAcrossMultipleClassesInOneProcess() throws Exception {
		// Documented in ProcessInstance's own javadoc: a process isn't
		// always one class - combined-handshake's Peer spawns an internal
		// WindowChecker thread in the same JVM, and both classes' sync
		// points belong to that one process instance. Each class is scanned
		// (and edge-id-numbered) independently - concatenation, not a
		// shared counter across classes.
		Map<String, File> compiled = FixtureCompiler.compile(Map.of("Main", """
				public class Main {
				    public static void main(String[] args) {
				        Helper.doRelease();
				    }
				}
				""", "Helper", """
				import java.util.concurrent.Semaphore;

				public class Helper {
				    static Semaphore sem = new Semaphore(0);

				    static void doRelease() {
				        sem.release();
				    }
				}
				"""));
		File classDir = compiled.get("Main").getParentFile();

		ProcessInstance process = ClassScanner.scanProcess(classDir, 7, "Peer", List.of("Main", "Helper"));

		assertEquals(7, process.processId);
		assertEquals("Peer", process.role);
		assertEquals(1, process.syncPoints.size(), "Main itself has no sync point of its own - only Helper's does");
		assertEquals(SyncPoint.Kind.SEM_RELEASE, process.syncPoints.get(0).kind);
		assertEquals("Helper#doRelease:0", process.syncPoints.get(0).edgeId);
	}

	@Test
	void edgeIdCounterContinuesAcrossMethodsRatherThanResettingPerMethod() throws Exception {
		File classFile = FixtureCompiler.compileOne("TwoMethods", """
				import java.util.concurrent.Semaphore;

				public class TwoMethods {
				    static Semaphore sem = new Semaphore(0);

				    public static void main(String[] args) throws InterruptedException {
				        helper();
				        sem.acquire();
				    }

				    static void helper() {
				        sem.release();
				    }
				}
				""");

		List<SyncPoint> points = ClassScanner.scan(classFile, "TwoMethods");

		assertEquals(2, points.size());
		assertEquals("TwoMethods#main:0", points.get(0).edgeId);
		assertEquals("TwoMethods#helper:1", points.get(1).edgeId);
	}
}
