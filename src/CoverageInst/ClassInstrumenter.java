package CoverageInst;

import java.util.List;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.LocalVariablesSorter;

/**
 * ASM ClassVisitor that finds calls to the small set of synchronization
 * primitives CoverageInst recognizes (DatagramSocket send/receive/<init>,
 * Semaphore acquire/release, Thread#start) and wraps each call site with a
 * hook into CoverageTracer - without changing the original call's
 * arguments, return value, or control flow, matching the same
 * "instrumentation must not alter program semantics" rule ValiPar itself
 * follows.
 *
 * One edge id is assigned per call site (not per method), sequentially,
 * as they're discovered scanning the class: "<ClassName>#<method>:<n>".
 *
 * `synchronized` blocks are also recognized, via a separate visitInsn
 * override (MONITORENTER/MONITOREXIT are bare zero-operand instructions,
 * never a method-instruction call site) - see SyncPointMatcher.matchInsnKind
 * for the exact scope and the known double-MONITOREXIT-per-block wrinkle.
 * `synchronized` METHODS are not recognized: javac emits no
 * MONITORENTER/MONITOREXIT for those at all (only the ACC_SYNCHRONIZED
 * access flag, checked implicitly by the JVM), so there is nothing here to
 * hook - no benchmark needs this today.
 *
 * Scope, deliberate for now: matches are exact-owner (java/net/DatagramSocket
 * specifically, not subclasses like MulticastSocket - a statically-typed
 * `MulticastSocket` variable's calls would carry that owner in the
 * bytecode and be missed here) and exact-descriptor (Semaphore's
 * multi-permit acquire(int)/release(int) overloads aren't matched, only
 * the no-arg ones). None of our current benchmarks use either, so this
 * isn't an active gap - extend the owner/descriptor checks below if a
 * future benchmark needs them.
 */
public class ClassInstrumenter extends ClassVisitor {

	private static final String TRACER = "CoverageInst/CoverageTracer";
	private static final String DATAGRAM_SOCKET = "java/net/DatagramSocket";
	private static final String MULTICAST_SOCKET = "java/net/MulticastSocket";
	private static final String DATAGRAM_PACKET_DESC = "Ljava/net/DatagramPacket;";
	private static final String SEMAPHORE = "java/util/concurrent/Semaphore";
	private static final String REENTRANT_LOCK = "java/util/concurrent/locks/ReentrantLock";
	private static final String LOCK = "java/util/concurrent/locks/Lock";
	private static final String CONDITION = "java/util/concurrent/locks/Condition";
	private static final String THREAD = "java/lang/Thread";
	private static final String CYCLIC_BARRIER = "java/util/concurrent/CyclicBarrier";
	private static final String DATAGRAM_CHANNEL = "java/nio/channels/DatagramChannel";
	private static final String SOCKET_ADDRESS_DESC = "Ljava/net/SocketAddress;";

	private final String className;
	private final List<SyncPoint> discovered;
	private int edgeCounter = 0;

	/**
	 * @param discovered every recognized call site found during the visit is
	 *                   appended here, in the same order edge ids are
	 *                   assigned - this is CoverageInst's equivalent of
	 *                   ValiElem's static node discovery, produced as a
	 *                   byproduct of the same pass that instruments the
	 *                   class, instead of a separate scan.
	 */
	public ClassInstrumenter(ClassVisitor classWriter, String className, List<SyncPoint> discovered) {
		super(Opcodes.ASM9, classWriter);
		this.className = className;
		this.discovered = discovered;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
		MethodVisitor original = super.visitMethod(access, name, descriptor, signature, exceptions);
		// Wrapped for every method (a no-op unless SyncPointVisitor actually
		// calls newLocal - see the DatagramChannel#send handling below,
		// the only hook that needs a scratch local to preserve the channel
		// reference across the real send() call).
		LocalVariablesSorter sorter = new LocalVariablesSorter(access, descriptor, original);
		return new SyncPointVisitor(sorter, sorter, name);
	}

	private String nextEdgeId(String methodName, SyncPoint.Kind kind) {
		String edgeId = className + "#" + methodName + ":" + (edgeCounter++);
		discovered.add(new SyncPoint(edgeId, kind, className));
		return edgeId;
	}

	private class SyncPointVisitor extends MethodVisitor {

		private final String methodName;
		private final LocalVariablesSorter sorter;

		SyncPointVisitor(MethodVisitor delegate, LocalVariablesSorter sorter, String methodName) {
			super(Opcodes.ASM9, delegate);
			this.sorter = sorter;
			this.methodName = methodName;
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
				boolean isInterface) {

			if (opcode == Opcodes.INVOKESPECIAL && (owner.equals(DATAGRAM_SOCKET) || owner.equals(MULTICAST_SOCKET)) && name.equals("<init>")) {
				super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
				// Stack after <init> still holds the DUP'd socket reference
				// pushed by the original "new DatagramSocket()" sequence.
				super.visitInsn(Opcodes.DUP);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "registerSocket",
						"(Ljava/net/DatagramSocket;)V", false);
				return;
			}

			if (opcode == Opcodes.INVOKEVIRTUAL && (owner.equals(DATAGRAM_SOCKET) || owner.equals(MULTICAST_SOCKET)) && name.equals("send")
					&& descriptor.equals("(" + DATAGRAM_PACKET_DESC + ")V")) {
				String edgeId = nextEdgeId(methodName, SyncPoint.Kind.SEND);
				super.visitInsn(Opcodes.DUP2);
				super.visitLdcInsn(edgeId);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "beforeSend",
						"(Ljava/net/DatagramSocket;Ljava/net/DatagramPacket;Ljava/lang/String;)V", false);
				super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "afterSend", "()V", false);
				return;
			}

			if (opcode == Opcodes.INVOKEVIRTUAL && (owner.equals(DATAGRAM_SOCKET) || owner.equals(MULTICAST_SOCKET)) && name.equals("receive")
					&& descriptor.equals("(" + DATAGRAM_PACKET_DESC + ")V")) {
				String edgeId = nextEdgeId(methodName, SyncPoint.Kind.RECEIVE);
				super.visitInsn(Opcodes.DUP2);
				super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
				super.visitLdcInsn(edgeId);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "afterReceive",
						"(Ljava/net/DatagramSocket;Ljava/net/DatagramPacket;Ljava/lang/String;)V", false);
				return;
			}

			if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals(SEMAPHORE) && name.equals("release")
					&& descriptor.equals("()V")) {
				String edgeId = nextEdgeId(methodName, SyncPoint.Kind.SEM_RELEASE);
				super.visitInsn(Opcodes.DUP);
				super.visitLdcInsn(edgeId);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "beforeSemaphoreRelease",
						"(Ljava/lang/Object;Ljava/lang/String;)V", false);
				super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
				return;
			}

			if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals(SEMAPHORE)
					&& (name.equals("acquire") || name.equals("acquireUninterruptibly"))
					&& descriptor.equals("()V")) {
				String edgeId = nextEdgeId(methodName, SyncPoint.Kind.SEM_ACQUIRE);
				super.visitInsn(Opcodes.DUP);
				super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
				super.visitLdcInsn(edgeId);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "afterSemaphoreAcquire",
						"(Ljava/lang/Object;Ljava/lang/String;)V", false);
				return;
			}

			// ReentrantLock's lock()/unlock() are the shared-memory-only,
			// binary-permit sibling of Semaphore's acquire/release - same
			// same-object identity pairing, so the exact same tracer hooks
			// apply: lock() is the "acquire" side, unlock() the "release"
			// side.
			if (((opcode == Opcodes.INVOKEVIRTUAL && owner.equals(REENTRANT_LOCK))
					|| (opcode == Opcodes.INVOKEINTERFACE && owner.equals(LOCK))) && name.equals("unlock")
					&& descriptor.equals("()V")) {
				String edgeId = nextEdgeId(methodName, SyncPoint.Kind.SEM_RELEASE);
				super.visitInsn(Opcodes.DUP);
				super.visitLdcInsn(edgeId);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "beforeSemaphoreRelease",
						"(Ljava/lang/Object;Ljava/lang/String;)V", false);
				super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
				return;
			}

			if (((opcode == Opcodes.INVOKEVIRTUAL && owner.equals(REENTRANT_LOCK))
					|| (opcode == Opcodes.INVOKEINTERFACE && owner.equals(LOCK))) && name.equals("lock")
					&& descriptor.equals("()V")) {
				String edgeId = nextEdgeId(methodName, SyncPoint.Kind.SEM_ACQUIRE);
				super.visitInsn(Opcodes.DUP);
				super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
				super.visitLdcInsn(edgeId);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "afterSemaphoreAcquire",
						"(Ljava/lang/Object;Ljava/lang/String;)V", false);
				return;
			}

			// Condition (an interface - calls go through INVOKEINTERFACE, not
			// INVOKEVIRTUAL, unlike everything else recognized here) is the
			// same identity-pairing model again: await() is the "acquire"
			// side, signal()/signalAll() the "release" side.
			if (opcode == Opcodes.INVOKEINTERFACE && owner.equals(CONDITION)
					&& (name.equals("signal") || name.equals("signalAll")) && descriptor.equals("()V")) {
				String edgeId = nextEdgeId(methodName, SyncPoint.Kind.SEM_RELEASE);
				super.visitInsn(Opcodes.DUP);
				super.visitLdcInsn(edgeId);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "beforeSemaphoreRelease",
						"(Ljava/lang/Object;Ljava/lang/String;)V", false);
				super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
				return;
			}

			if (opcode == Opcodes.INVOKEINTERFACE && owner.equals(CONDITION) && name.equals("await")
					&& descriptor.equals("()V")) {
				String edgeId = nextEdgeId(methodName, SyncPoint.Kind.SEM_ACQUIRE);
				super.visitInsn(Opcodes.DUP);
				super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
				super.visitLdcInsn(edgeId);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "afterSemaphoreAcquire",
						"(Ljava/lang/Object;Ljava/lang/String;)V", false);
				return;
			}

			if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals(DATAGRAM_CHANNEL) && name.equals("send")
					&& descriptor.equals("(Ljava/nio/ByteBuffer;" + SOCKET_ADDRESS_DESC + ")I")) {
				String edgeId = nextEdgeId(methodName, SyncPoint.Kind.SEND);
				// Stack here: channel, buffer, addr. The channel reference
				// (the receiver) needs to survive all the way past the real
				// send() call too - afterChannelSend registers the
				// channel's own local address (see CoverageTracer.
				// registerChannel's javadoc for why that has to happen
				// AFTER the send, not alongside beforeChannelSend the way
				// registerSocket runs alongside beforeSend/afterReceive) -
				// so unlike every other hook here, this one needs scratch
				// locals: stash buffer/addr, grab the now-exposed channel,
				// then rebuild the original 3-value stack for the real call.
				int addrSlot = sorter.newLocal(Type.getType(SOCKET_ADDRESS_DESC));
				int bufferSlot = sorter.newLocal(Type.getType("Ljava/nio/ByteBuffer;"));
				int channelSlot = sorter.newLocal(Type.getObjectType(DATAGRAM_CHANNEL));
				super.visitVarInsn(Opcodes.ASTORE, addrSlot);
				super.visitVarInsn(Opcodes.ASTORE, bufferSlot);
				super.visitVarInsn(Opcodes.ASTORE, channelSlot);
				super.visitVarInsn(Opcodes.ALOAD, channelSlot);
				super.visitVarInsn(Opcodes.ALOAD, bufferSlot);
				super.visitVarInsn(Opcodes.ALOAD, addrSlot);
				super.visitInsn(Opcodes.DUP);
				super.visitLdcInsn(edgeId);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "beforeChannelSend",
						"(" + SOCKET_ADDRESS_DESC + "Ljava/lang/String;)V", false);
				super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
				// Real call left an int (sendResult) on top - afterChannelSend
				// takes the channel as its only argument, pushed AFTER that
				// int, so consuming it leaves the int untouched underneath.
				super.visitVarInsn(Opcodes.ALOAD, channelSlot);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "afterChannelSend",
						"(" + Type.getObjectType(DATAGRAM_CHANNEL).getDescriptor() + ")V", false);
				return;
			}

			if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals(DATAGRAM_CHANNEL) && name.equals("receive")
					&& descriptor.equals("(Ljava/nio/ByteBuffer;)" + SOCKET_ADDRESS_DESC)) {
				String edgeId = nextEdgeId(methodName, SyncPoint.Kind.RECEIVE);
				super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
				super.visitInsn(Opcodes.DUP);
				super.visitLdcInsn(edgeId);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "afterChannelReceive",
						"(" + SOCKET_ADDRESS_DESC + "Ljava/lang/String;)V", false);
				return;
			}

			if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals(CYCLIC_BARRIER) && name.equals("await")
					&& descriptor.equals("()I")) {
				String edgeId = nextEdgeId(methodName, SyncPoint.Kind.BARRIER);
				super.visitInsn(Opcodes.DUP);
				// original await()I consumes the top barrier ref, pushes the
				// arrival-index int; SWAP brings the saved barrier ref back
				// on top (above the int) so it - not the int - is what gets
				// passed to atBarrier, while the int is left in the right
				// position underneath for whatever follows.
				super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
				super.visitInsn(Opcodes.SWAP);
				super.visitLdcInsn(edgeId);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "atBarrier",
						"(Ljava/lang/Object;Ljava/lang/String;)V", false);
				return;
			}

			if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals(THREAD) && name.equals("start")
					&& descriptor.equals("()V")) {
				String edgeId = nextEdgeId(methodName, SyncPoint.Kind.THREAD_START);
				super.visitInsn(Opcodes.DUP);
				super.visitLdcInsn(edgeId);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "beforeThreadStart",
						"(Ljava/lang/Object;Ljava/lang/String;)V", false);
				super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
				return;
			}

			super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
		}

		// synchronized(obj){...} - MONITORENTER/MONITOREXIT are bare
		// zero-operand instructions (no owner/name/descriptor), so they
		// arrive here, never in visitMethodInsn above. Both instructions
		// pop exactly one operand (the monitor objectref) and push
		// nothing, so the same DUP-based idioms Lock's lock()/unlock() use
		// apply unchanged - no scratch local needed.
		@Override
		public void visitInsn(int opcode) {
			if (opcode == Opcodes.MONITORENTER) {
				String edgeId = nextEdgeId(methodName, SyncPoint.Kind.SEM_ACQUIRE);
				super.visitInsn(Opcodes.DUP);
				super.visitInsn(opcode);
				super.visitLdcInsn(edgeId);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "afterSemaphoreAcquire",
						"(Ljava/lang/Object;Ljava/lang/String;)V", false);
				return;
			}

			if (opcode == Opcodes.MONITOREXIT) {
				String edgeId = nextEdgeId(methodName, SyncPoint.Kind.SEM_RELEASE);
				super.visitInsn(Opcodes.DUP);
				super.visitLdcInsn(edgeId);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "beforeSemaphoreRelease",
						"(Ljava/lang/Object;Ljava/lang/String;)V", false);
				super.visitInsn(opcode);
				return;
			}

			super.visitInsn(opcode);
		}
	}
}
