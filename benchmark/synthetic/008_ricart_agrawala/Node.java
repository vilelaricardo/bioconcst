/**
 * Synthetic Benchmark - Ricart-Agrawala Mutual Exclusion (Node)
 *
 * Implements the request/defer/reply cascade from Ricart & Agrawala,
 * "An Optimal Algorithm for Mutual Exclusion in Computer Networks",
 * CACM Vol. 24, No. 1, January 1981: every node broadcasts a REQUEST
 * carrying its own logical timestamp, and on receiving another node's
 * REQUEST either replies immediately (the sender has priority) or defers
 * the reply until after its own critical-section exit (this node has
 * priority) - priority is decided by comparing (timestamp, processId)
 * lexicographically, the paper's own tie-break rule so no two nodes ever
 * defer to each other. A node enters its critical section only once it
 * has collected a REPLY from every other node.
 *
 * Deliberate simplification, in the same spirit as this suite's other
 * benchmarks: there is no running Lamport clock incrementing across real
 * concurrent events - each node's "timestamp" is a single fixed value
 * (the evolved gene) standing in for whatever logical time it would have
 * queued its request at, which keeps the GA's search target a fixed,
 * well-defined set of pairwise comparisons instead of a value that also
 * depends on scheduling. The critical section itself is a no-op (a single
 * printed line) - this benchmark exercises the admission protocol, not
 * mutual exclusion of a real resource.
 *
 * Three symmetric nodes (N=3), one class for every role, all-to-all -
 * unlike bully-election there is no crashed/alive concept, every node
 * always fully participates, so the request/reply race is the only
 * non-determinism. Two sockets per node (same discipline as
 * bully-election's Node): a request socket used to broadcast/receive
 * REQUEST, and a SEPARATE dedicated reply socket used only for REPLY -
 * without the split, a REPLY arriving before this node's second unrolled
 * REQUEST receive would be wrongly consumed by it (UDP delivery order
 * across two independent sockets isn't guaranteed to match this node's own
 * receive-call order).
 *
 * The two decisions ("defer or reply now" to each of the other two nodes)
 * are unrolled (not a loop) exactly like raft-election/two-phase-commit's
 * hubs, so each of the two pairwise comparisons a node makes stays its own
 * distinguishable branch for coverage - and the two deferred-reply sends at
 * the end are unrolled per specific target id for the same reason.
 *
 * Real coverage ceiling, NOT 100% - same over-approximate-then-accept-a-
 * ceiling pattern quorum-handshake's 66.67% and bully-election's 22.2%
 * already document, here for a different reason: this class uses TWO
 * sockets (request vs. reply) so the same numeric edge id space is reused
 * across both channels, but RequiredElementsGenerator has no concept of
 * "socket" - it pairs a send edge with every syntactically role-reachable
 * receive edge on the resolved target process, request-receives (:1, :3)
 * included, even though a REPLY packet can physically only ever be picked
 * up by that process's dedicated reply socket (:5, :6). Confirmed by hand
 * against the actual protocol logic above (a real fixedMessageTargets pin
 * on edges :7/:8 was needed and added - each targets a totalNodes-1-sized
 * array slot resolved at runtime, so it varies per physical instance -
 * but no equivalent mechanism exists to also narrow WHICH of the target's
 * several receive edges a send may pair with). Not a bug to route around,
 * and not evidence of a weak GA/search if measured coverage plateaus
 * below 100%.
 *
 * java Node <processId> <totalNodes> <timestampGene>
 */

import java.net.*;
import java.io.*;

public class Node {
    static final int SOCKET_TIMEOUT_MS = 10000;

    public static void main(String[] args) throws IOException {
        int processId = Integer.parseInt(args[0]);
        int totalNodes = Integer.parseInt(args[1]);
        int myTimestamp = Integer.parseInt(args[2]);

        DatagramSocket requestSocket = new DatagramSocket();
        requestSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
        InetAddress addressIP = InetAddress.getLoopbackAddress();
        String ip = addressIP.getHostAddress();
        HelperClass.makeAddressFile(processId, 1, HelperClass.makeAddress(ip, requestSocket.getLocalPort()));

        DatagramSocket replySocket = new DatagramSocket();
        replySocket.setSoTimeout(SOCKET_TIMEOUT_MS);
        HelperClass.makeAddressFile(processId, 2, HelperClass.makeAddress(ip, replySocket.getLocalPort()));

        HelperClass.markReady(processId);

        int[] otherIds = new int[totalNodes - 1];
        int idx = 0;
        for (int id = 1; id <= totalNodes; id++) {
            if (id != processId) {
                otherIds[idx++] = id;
            }
        }
        HelperClass.waitForReady(otherIds);

        InetAddress[] requestIP = new InetAddress[totalNodes + 1];
        int[] requestPort = new int[totalNodes + 1];
        InetAddress[] replyIP = new InetAddress[totalNodes + 1];
        int[] replyPort = new int[totalNodes + 1];
        for (int id : otherIds) {
            requestIP[id] = InetAddress.getByName(HelperClass.readRemoteIP(id, 1));
            requestPort[id] = HelperClass.readRemotePort(id, 1);
            replyIP[id] = InetAddress.getByName(HelperClass.readRemoteIP(id, 2));
            replyPort[id] = HelperClass.readRemotePort(id, 2);
        }

        // Broadcast REQUEST to every other node - one static call site
        // (edge :0), executed (totalNodes - 1) times; destination resolved
        // from each node's own real address, so this direction needs no
        // fixedMessageSources pin.
        byte[] request = ("REQUEST:" + myTimestamp + ":" + processId).getBytes();
        for (int otherId : otherIds) {
            DatagramPacket requestPacket = new DatagramPacket(request, request.length, requestIP[otherId],
                    requestPort[otherId]);
            requestSocket.send(requestPacket);
        }

        boolean deferredTo0 = false;
        boolean deferredTo1 = false;

        // Two UNROLLED REQUEST receives (not a loop) - ambiguous over the
        // two other ids, same discipline as raft-election/two-phase-commit's
        // hub receives, so which physical node's request lands in slot A
        // vs slot B is a genuine per-execution race, fixedMessageSources
        // declared over both other ids for each slot.
        byte[] receiveBufferA = new byte[255];
        DatagramPacket requestPacketA = new DatagramPacket(receiveBufferA, receiveBufferA.length);
        requestSocket.receive(requestPacketA);
        String[] partsA = new String(requestPacketA.getData()).trim().split(":");
        int theirTimestampA = Integer.parseInt(partsA[1]);
        int theirIdA = Integer.parseInt(partsA[2]);

        boolean deferA = (myTimestamp < theirTimestampA)
                || (myTimestamp == theirTimestampA && processId < theirIdA);
        if (deferA) {
            if (theirIdA == otherIds[0]) {
                deferredTo0 = true;
            } else {
                deferredTo1 = true;
            }
        } else {
            byte[] replyNowA = ("REPLY:" + processId).getBytes();
            DatagramPacket replyPacketA = new DatagramPacket(replyNowA, replyNowA.length, replyIP[theirIdA],
                    replyPort[theirIdA]);
            replySocket.send(replyPacketA);
        }

        byte[] receiveBufferB = new byte[255];
        DatagramPacket requestPacketB = new DatagramPacket(receiveBufferB, receiveBufferB.length);
        requestSocket.receive(requestPacketB);
        String[] partsB = new String(requestPacketB.getData()).trim().split(":");
        int theirTimestampB = Integer.parseInt(partsB[1]);
        int theirIdB = Integer.parseInt(partsB[2]);

        boolean deferB = (myTimestamp < theirTimestampB)
                || (myTimestamp == theirTimestampB && processId < theirIdB);
        if (deferB) {
            if (theirIdB == otherIds[0]) {
                deferredTo0 = true;
            } else {
                deferredTo1 = true;
            }
        } else {
            byte[] replyNowB = ("REPLY:" + processId).getBytes();
            DatagramPacket replyPacketB = new DatagramPacket(replyNowB, replyNowB.length, replyIP[theirIdB],
                    replyPort[theirIdB]);
            replySocket.send(replyPacketB);
        }

        // Two UNROLLED REPLY receives - exactly (totalNodes - 1) replies
        // are always eventually sent (no crash concept in this benchmark),
        // order is the other genuine race, ambiguous over both other ids.
        byte[] replyBufferA = new byte[255];
        DatagramPacket replyPacketInA = new DatagramPacket(replyBufferA, replyBufferA.length);
        replySocket.receive(replyPacketInA);

        byte[] replyBufferB = new byte[255];
        DatagramPacket replyPacketInB = new DatagramPacket(replyBufferB, replyBufferB.length);
        replySocket.receive(replyPacketInB);

        System.out.println("Node " + processId + " enters critical section.");

        // Deferred replies, sent only now (after this node's own critical
        // section) - unrolled per specific target id, branch-duplicated
        // sends so each stays its own distinguishable edge.
        if (deferredTo0) {
            byte[] deferredReply0 = ("REPLY:" + processId).getBytes();
            DatagramPacket deferredPacket0 = new DatagramPacket(deferredReply0, deferredReply0.length,
                    replyIP[otherIds[0]], replyPort[otherIds[0]]);
            replySocket.send(deferredPacket0);
        }
        if (deferredTo1) {
            byte[] deferredReply1 = ("REPLY:" + processId).getBytes();
            DatagramPacket deferredPacket1 = new DatagramPacket(deferredReply1, deferredReply1.length,
                    replyIP[otherIds[1]], replyPort[otherIds[1]]);
            replySocket.send(deferredPacket1);
        }

        requestSocket.close();
        replySocket.close();
        HelperClass.cleanup(otherIds);
    }
}
