package CoverageInst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

/**
 * SyncPointMatcher is the single source of truth ClassInstrumenter (writes
 * hooks) and ControlFlowGraph (reads the same call sites back) both depend
 * on to agree on edge ids - a wrong predicate here doesn't just miss a sync
 * point, it desyncs the two passes' numbering. Pure (opcode, owner, name,
 * descriptor) checks, so no bytecode compilation needed to exercise them
 * directly.
 */
class SyncPointMatcherTest {

	private static final String DATAGRAM_SOCKET = "java/net/DatagramSocket";
	private static final String MULTICAST_SOCKET = "java/net/MulticastSocket";
	private static final String DATAGRAM_PACKET = "(Ljava/net/DatagramPacket;)V";
	private static final String SEMAPHORE = "java/util/concurrent/Semaphore";
	private static final String REENTRANT_LOCK = "java/util/concurrent/locks/ReentrantLock";
	private static final String LOCK = "java/util/concurrent/locks/Lock";
	private static final String CONDITION = "java/util/concurrent/locks/Condition";
	private static final String THREAD = "java/lang/Thread";
	private static final String CYCLIC_BARRIER = "java/util/concurrent/CyclicBarrier";

	@Test
	void datagramInitMatchesBothPlainAndMulticastSocketConstructionButNoOtherOpcode() {
		// Not a numbered sync point (no edge id, no discovered SyncPoint) -
		// still its own predicate, checked directly since matchKind never
		// references it at all.
		assertTrue(SyncPointMatcher.isDatagramInit(Opcodes.INVOKESPECIAL, DATAGRAM_SOCKET, "<init>", "()V"));
		assertTrue(SyncPointMatcher.isDatagramInit(Opcodes.INVOKESPECIAL, MULTICAST_SOCKET, "<init>", "(I)V"));
		assertFalse(SyncPointMatcher.isDatagramInit(Opcodes.INVOKEVIRTUAL, DATAGRAM_SOCKET, "<init>", "()V"),
				"construction is always INVOKESPECIAL, never INVOKEVIRTUAL");
	}

	@Test
	void datagramSendMatchesBothPlainAndMulticastSockets() {
		assertTrue(SyncPointMatcher.isDatagramSend(Opcodes.INVOKEVIRTUAL, DATAGRAM_SOCKET, "send", DATAGRAM_PACKET));
		assertTrue(
				SyncPointMatcher.isDatagramSend(Opcodes.INVOKEVIRTUAL, MULTICAST_SOCKET, "send", DATAGRAM_PACKET));
	}

	@Test
	void datagramSendRejectsWrongOpcodeOwnerNameOrDescriptor() {
		assertFalse(SyncPointMatcher.isDatagramSend(Opcodes.INVOKESPECIAL, DATAGRAM_SOCKET, "send", DATAGRAM_PACKET),
				"wrong opcode");
		assertFalse(SyncPointMatcher.isDatagramSend(Opcodes.INVOKEVIRTUAL, "java/net/Socket", "send",
				DATAGRAM_PACKET), "wrong owner");
		assertFalse(
				SyncPointMatcher.isDatagramSend(Opcodes.INVOKEVIRTUAL, DATAGRAM_SOCKET, "receive", DATAGRAM_PACKET),
				"wrong name");
		assertFalse(SyncPointMatcher.isDatagramSend(Opcodes.INVOKEVIRTUAL, DATAGRAM_SOCKET, "send", "()V"),
				"wrong descriptor");
	}

	@Test
	void lockLockAcceptsReentrantLockViaInvokevirtualAndTheLockInterfaceViaInvokeinterface() {
		// This distinction is a real, previously-hit bug: a field declared as
		// the Lock INTERFACE type emits INVOKEINTERFACE with owner=Lock, while
		// a field declared as the concrete ReentrantLock type emits
		// INVOKEVIRTUAL with owner=ReentrantLock - the exact-owner, exact-
		// opcode scope is deliberate, not an oversight.
		assertTrue(SyncPointMatcher.isLockLock(Opcodes.INVOKEVIRTUAL, REENTRANT_LOCK, "lock", "()V"));
		assertTrue(SyncPointMatcher.isLockLock(Opcodes.INVOKEINTERFACE, LOCK, "lock", "()V"));
	}

	@Test
	void lockLockRejectsTheOppositeOpcodeOwnerPairing() {
		// ReentrantLock never gets called via INVOKEINTERFACE, and the Lock
		// interface never gets called via INVOKEVIRTUAL - real javac never
		// emits either combination, but the predicate must still reject them
		// rather than silently degrade to "any opcode goes".
		assertFalse(SyncPointMatcher.isLockLock(Opcodes.INVOKEINTERFACE, REENTRANT_LOCK, "lock", "()V"));
		assertFalse(SyncPointMatcher.isLockLock(Opcodes.INVOKEVIRTUAL, LOCK, "lock", "()V"));
	}

	@Test
	void semaphoreAcquireMatchesBothAcquireMethodNames() {
		assertTrue(SyncPointMatcher.isSemaphoreAcquire(Opcodes.INVOKEVIRTUAL, SEMAPHORE, "acquire", "()V"));
		assertTrue(
				SyncPointMatcher.isSemaphoreAcquire(Opcodes.INVOKEVIRTUAL, SEMAPHORE, "acquireUninterruptibly", "()V"));
		assertFalse(SyncPointMatcher.isSemaphoreAcquire(Opcodes.INVOKEVIRTUAL, SEMAPHORE, "tryAcquire", "()Z"));
	}

	@Test
	void conditionSignalMatchesBothSignalMethodNames() {
		assertTrue(SyncPointMatcher.isConditionSignal(Opcodes.INVOKEINTERFACE, CONDITION, "signal", "()V"));
		assertTrue(SyncPointMatcher.isConditionSignal(Opcodes.INVOKEINTERFACE, CONDITION, "signalAll", "()V"));
	}

	@Test
	void barrierAwaitRequiresTheIntReturningOverload() {
		assertTrue(SyncPointMatcher.isBarrierAwait(Opcodes.INVOKEVIRTUAL, CYCLIC_BARRIER, "await", "()I"));
		// The timed overload (await(long, TimeUnit)) is a different descriptor
		// entirely - deliberately out of the recognized scope.
		assertFalse(SyncPointMatcher.isBarrierAwait(Opcodes.INVOKEVIRTUAL, CYCLIC_BARRIER, "await",
				"(JLjava/util/concurrent/TimeUnit;)I"));
	}

	@Test
	void threadStartRequiresExactlyThreadDotStart() {
		assertTrue(SyncPointMatcher.isThreadStart(Opcodes.INVOKEVIRTUAL, THREAD, "start", "()V"));
		assertFalse(SyncPointMatcher.isThreadStart(Opcodes.INVOKEVIRTUAL, THREAD, "run", "()V"));
	}

	@Test
	void matchKindMapsEveryRecognizedPrimitiveToItsExpectedKind() {
		assertEquals(SyncPoint.Kind.SEND,
				SyncPointMatcher.matchKind(Opcodes.INVOKEVIRTUAL, DATAGRAM_SOCKET, "send", DATAGRAM_PACKET));
		assertEquals(SyncPoint.Kind.RECEIVE,
				SyncPointMatcher.matchKind(Opcodes.INVOKEVIRTUAL, DATAGRAM_SOCKET, "receive", DATAGRAM_PACKET));
		assertEquals(SyncPoint.Kind.SEM_RELEASE,
				SyncPointMatcher.matchKind(Opcodes.INVOKEVIRTUAL, SEMAPHORE, "release", "()V"));
		assertEquals(SyncPoint.Kind.SEM_ACQUIRE,
				SyncPointMatcher.matchKind(Opcodes.INVOKEVIRTUAL, SEMAPHORE, "acquire", "()V"));
		assertEquals(SyncPoint.Kind.SEM_RELEASE,
				SyncPointMatcher.matchKind(Opcodes.INVOKEVIRTUAL, REENTRANT_LOCK, "unlock", "()V"));
		assertEquals(SyncPoint.Kind.SEM_ACQUIRE,
				SyncPointMatcher.matchKind(Opcodes.INVOKEINTERFACE, CONDITION, "await", "()V"));
		assertEquals(SyncPoint.Kind.BARRIER,
				SyncPointMatcher.matchKind(Opcodes.INVOKEVIRTUAL, CYCLIC_BARRIER, "await", "()I"));
		assertEquals(SyncPoint.Kind.THREAD_START,
				SyncPointMatcher.matchKind(Opcodes.INVOKEVIRTUAL, THREAD, "start", "()V"));
	}

	@Test
	void matchKindReturnsNullForAnUnrecognizedCallSite() {
		assertNull(SyncPointMatcher.matchKind(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
				"(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
	}

	@Test
	void monitorEnterAndExitAreRecognizedByOpcodeAlone() {
		// Unlike every other primitive here, synchronized(obj){} compiles to
		// a bare zero-operand instruction - no owner/name/descriptor to
		// check at all, so this is opcode-only, exercised through
		// matchInsnKind rather than isLockLock-style four-arg predicates.
		assertTrue(SyncPointMatcher.isMonitorEnter(Opcodes.MONITORENTER));
		assertTrue(SyncPointMatcher.isMonitorExit(Opcodes.MONITOREXIT));
		assertFalse(SyncPointMatcher.isMonitorEnter(Opcodes.MONITOREXIT));
		assertFalse(SyncPointMatcher.isMonitorExit(Opcodes.MONITORENTER));
	}

	@Test
	void matchInsnKindMapsMonitorEnterToAcquireAndMonitorExitToRelease() {
		// synchronized reuses the exact same Kinds Lock/Semaphore/Condition
		// already produce - RequiredElementsGenerator's release<->acquire
		// pairing is primitive-agnostic over Kind, so no new Kind exists for
		// this primitive.
		assertEquals(SyncPoint.Kind.SEM_ACQUIRE, SyncPointMatcher.matchInsnKind(Opcodes.MONITORENTER));
		assertEquals(SyncPoint.Kind.SEM_RELEASE, SyncPointMatcher.matchInsnKind(Opcodes.MONITOREXIT));
	}

	@Test
	void matchInsnKindReturnsNullForAnyOtherOpcodeIncludingPseudoNodesSentinel() {
		// -1 is the sentinel AbstractInsnNode.getOpcode() returns for
		// pseudo instructions (labels/line-numbers/frames) in ASM's tree
		// API - callers that walk raw instructions pass it through here
		// unfiltered, so it must resolve to "not a sync point", not throw.
		assertNull(SyncPointMatcher.matchInsnKind(-1));
		assertNull(SyncPointMatcher.matchInsnKind(Opcodes.NOP));
		assertNull(SyncPointMatcher.matchInsnKind(Opcodes.DUP));
	}
}
