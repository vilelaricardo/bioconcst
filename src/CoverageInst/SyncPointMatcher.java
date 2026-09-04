package CoverageInst;

import org.objectweb.asm.Opcodes;

/**
 * The single source of truth for "is this method-instruction call site a
 * synchronization primitive CoverageInst recognizes, and which kind" -
 * shared between ClassInstrumenter (which injects tracer hooks at these call
 * sites while writing bytecode) and ControlFlowGraph (a separate, read-only
 * ASM tree-API pass that needs to identify the SAME call sites, in the SAME
 * order, so both passes assign identical edge ids - "ClassName#method:N" -
 * to identical call sites). Keeping the matching predicates in exactly one
 * place is what guarantees that correspondence never drifts as new
 * primitives are added.
 *
 * Mirrors ClassInstrumenter's own documented scope limits exactly (exact
 * owner, exact descriptor) since this IS that same logic, just extracted -
 * see ClassInstrumenter's class javadoc for the rationale.
 */
public final class SyncPointMatcher {

	static final String DATAGRAM_SOCKET = "java/net/DatagramSocket";
	static final String MULTICAST_SOCKET = "java/net/MulticastSocket";
	static final String DATAGRAM_PACKET_DESC = "Ljava/net/DatagramPacket;";
	static final String SEMAPHORE = "java/util/concurrent/Semaphore";
	static final String REENTRANT_LOCK = "java/util/concurrent/locks/ReentrantLock";
	static final String LOCK = "java/util/concurrent/locks/Lock";
	static final String CONDITION = "java/util/concurrent/locks/Condition";
	static final String THREAD = "java/lang/Thread";
	static final String CYCLIC_BARRIER = "java/util/concurrent/CyclicBarrier";
	static final String DATAGRAM_CHANNEL = "java/nio/channels/DatagramChannel";
	static final String SOCKET_ADDRESS_DESC = "Ljava/net/SocketAddress;";

	private SyncPointMatcher() {
	}

	// Not a numbered sync point (registerSocket doesn't get an edge id) -
	// still exposed here so ClassInstrumenter's dispatch stays fully driven
	// by this class rather than half-duplicating the owner check locally.
	public static boolean isDatagramInit(int opcode, String owner, String name, String descriptor) {
		return opcode == Opcodes.INVOKESPECIAL && (owner.equals(DATAGRAM_SOCKET) || owner.equals(MULTICAST_SOCKET))
				&& name.equals("<init>");
	}

	public static boolean isDatagramSend(int opcode, String owner, String name, String descriptor) {
		return opcode == Opcodes.INVOKEVIRTUAL && (owner.equals(DATAGRAM_SOCKET) || owner.equals(MULTICAST_SOCKET))
				&& name.equals("send") && descriptor.equals("(" + DATAGRAM_PACKET_DESC + ")V");
	}

	public static boolean isDatagramReceive(int opcode, String owner, String name, String descriptor) {
		return opcode == Opcodes.INVOKEVIRTUAL && (owner.equals(DATAGRAM_SOCKET) || owner.equals(MULTICAST_SOCKET))
				&& name.equals("receive") && descriptor.equals("(" + DATAGRAM_PACKET_DESC + ")V");
	}

	public static boolean isSemaphoreRelease(int opcode, String owner, String name, String descriptor) {
		return opcode == Opcodes.INVOKEVIRTUAL && owner.equals(SEMAPHORE) && name.equals("release")
				&& descriptor.equals("()V");
	}

	public static boolean isSemaphoreAcquire(int opcode, String owner, String name, String descriptor) {
		return opcode == Opcodes.INVOKEVIRTUAL && owner.equals(SEMAPHORE)
				&& (name.equals("acquire") || name.equals("acquireUninterruptibly")) && descriptor.equals("()V");
	}

	public static boolean isLockUnlock(int opcode, String owner, String name, String descriptor) {
		return ((opcode == Opcodes.INVOKEVIRTUAL && owner.equals(REENTRANT_LOCK))
				|| (opcode == Opcodes.INVOKEINTERFACE && owner.equals(LOCK))) && name.equals("unlock")
				&& descriptor.equals("()V");
	}

	public static boolean isLockLock(int opcode, String owner, String name, String descriptor) {
		return ((opcode == Opcodes.INVOKEVIRTUAL && owner.equals(REENTRANT_LOCK))
				|| (opcode == Opcodes.INVOKEINTERFACE && owner.equals(LOCK))) && name.equals("lock")
				&& descriptor.equals("()V");
	}

	public static boolean isConditionSignal(int opcode, String owner, String name, String descriptor) {
		return opcode == Opcodes.INVOKEINTERFACE && owner.equals(CONDITION)
				&& (name.equals("signal") || name.equals("signalAll")) && descriptor.equals("()V");
	}

	public static boolean isConditionAwait(int opcode, String owner, String name, String descriptor) {
		return opcode == Opcodes.INVOKEINTERFACE && owner.equals(CONDITION) && name.equals("await")
				&& descriptor.equals("()V");
	}

	public static boolean isChannelSend(int opcode, String owner, String name, String descriptor) {
		return opcode == Opcodes.INVOKEVIRTUAL && owner.equals(DATAGRAM_CHANNEL) && name.equals("send")
				&& descriptor.equals("(Ljava/nio/ByteBuffer;" + SOCKET_ADDRESS_DESC + ")I");
	}

	public static boolean isChannelReceive(int opcode, String owner, String name, String descriptor) {
		return opcode == Opcodes.INVOKEVIRTUAL && owner.equals(DATAGRAM_CHANNEL) && name.equals("receive")
				&& descriptor.equals("(Ljava/nio/ByteBuffer;)" + SOCKET_ADDRESS_DESC);
	}

	public static boolean isBarrierAwait(int opcode, String owner, String name, String descriptor) {
		return opcode == Opcodes.INVOKEVIRTUAL && owner.equals(CYCLIC_BARRIER) && name.equals("await")
				&& descriptor.equals("()I");
	}

	public static boolean isThreadStart(int opcode, String owner, String name, String descriptor) {
		return opcode == Opcodes.INVOKEVIRTUAL && owner.equals(THREAD) && name.equals("start")
				&& descriptor.equals("()V");
	}

	/**
	 * Aggregate check for callers that only need "is this a numbered sync
	 * point, and which Kind" (ControlFlowGraph) rather than which specific
	 * injection sequence applies (ClassInstrumenter, via the predicates
	 * above). The predicates are mutually exclusive - each targets a
	 * disjoint (owner, name, descriptor) combination - so check order here
	 * has no effect on correctness.
	 */
	public static SyncPoint.Kind matchKind(int opcode, String owner, String name, String descriptor) {
		if (isDatagramSend(opcode, owner, name, descriptor) || isChannelSend(opcode, owner, name, descriptor)) {
			return SyncPoint.Kind.SEND;
		}
		if (isDatagramReceive(opcode, owner, name, descriptor) || isChannelReceive(opcode, owner, name, descriptor)) {
			return SyncPoint.Kind.RECEIVE;
		}
		if (isSemaphoreRelease(opcode, owner, name, descriptor) || isLockUnlock(opcode, owner, name, descriptor)
				|| isConditionSignal(opcode, owner, name, descriptor)) {
			return SyncPoint.Kind.SEM_RELEASE;
		}
		if (isSemaphoreAcquire(opcode, owner, name, descriptor) || isLockLock(opcode, owner, name, descriptor)
				|| isConditionAwait(opcode, owner, name, descriptor)) {
			return SyncPoint.Kind.SEM_ACQUIRE;
		}
		if (isBarrierAwait(opcode, owner, name, descriptor)) {
			return SyncPoint.Kind.BARRIER;
		}
		if (isThreadStart(opcode, owner, name, descriptor)) {
			return SyncPoint.Kind.THREAD_START;
		}
		return null;
	}
}
