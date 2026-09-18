/**
 * Synthetic Benchmark - Bully Algorithm (Node)
 *
 * Implements the coordinator-election cascade from Garcia-Molina,
 * "Elections in a Distributed System", IEEE Transactions on Computers,
 * Vol. C-31, No. 1, January 1982: a node that suspects the coordinator is
 * down sends ELECTION to every node with a HIGHER id; any alive higher node
 * replies OK and starts its own election further up; the node that gets no
 * OK from anyone above it declares itself coordinator and broadcasts that
 * to everyone else.
 *
 * A single structurally-identical class plays every role - lowerIds/
 * higherIds naturally come out empty at the two ends of the id range, so no
 * id-specific branching is needed. Node 1 is the FIXED initiator (always
 * alive, no gene) - if the sole designated initiator could itself evolve to
 * "crashed", no election would ever start and every node would just block
 * on its final receive until execTimeLimitMs kills the test case, a wasted
 * outcome that doesn't exercise any new coverage.
 *
 * Honest, deliberate simplification: this framework can only vary a
 * launched process's ARGS, never whether it launches at all - so a
 * "crashed" node still runs address exchange and its own final receive (so
 * it doesn't itself hang forever and doesn't stall the harness's own
 * readiness barrier), it just skips all election logic. This is not the
 * same as a process that was never started.
 *
 * Uses TWO sockets per node (same multi-socket-per-process shape
 * roller-coaster's Car/Passenger already use): a main socket for
 * ELECTION/OK exchange, and a SEPARATE, dedicated announceSocket purely for
 * the final COORDINATOR broadcast/receive. Without this split, a "crashed"
 * node's still-open single socket would still receive whatever ELECTION/OK
 * packets other nodes send it (they don't know it's crashed) - since a
 * crashed node skips the code that would normally consume and reply to
 * those, a stray packet is left sitting in the socket's receive buffer, and
 * gets wrongly picked up by the final one-shot receive instead of the real
 * COORDINATOR message (confirmed empirically in an early pilot run - the
 * fix is a genuinely separate socket, not a content check in a loop, since
 * a loop here would let stray-packet correlations wrongly satisfy required
 * MESSAGE edges that never really represent a coordinator announcement).
 *
 * ELECTION/OK collection uses a timeout-bounded loop, not unrolled
 * receives: unlike raft-election/two-phase-commit's hub, a node here may
 * receive 0 to (its own id - 1) ELECTION messages depending on which lower
 * ids happen to be alive - a count only known at runtime, not statically.
 * The one genuinely hard, well-modeled MESSAGE race is the FINAL
 * COORDINATOR announcement each non-winner waits for - always exactly one
 * message, from whichever id ends up winning - which is why that receive
 * is a single, one-shot statement (not a loop), with fixedMessageSources
 * declared over every other id in the config.
 *
 * Real coverage ceiling for N=3 is 12/54 = 22.2%, NOT 100% - traced by hand
 * against the actual protocol logic above. RequiredElementsGenerator's
 * fixedMessageSources only narrows required edges by WHICH PROCESS may be
 * the sender, never by WHICH of that sender's several distinct SEND edges
 * (:1 OK-reply, :2 ELECTION, :4 COORDINATOR) actually reaches a given
 * receive edge - so e.g. "SEND:1@p2 -> RECEIVE:5@p1" (an OK-reply wrongly
 * paired with the final announcement receive) is generated as a required
 * edge even though it can never really happen. This is the same
 * over-approximate-then-accept-a-ceiling pattern quorum-handshake's own
 * 66.67% ceiling already documents, just more severe here because Bully
 * uses ONE class for every role (several distinct message types funnel
 * through the same small set of edge ids) where raft-election/
 * two-phase-commit use one class per role (one message type per edge id) -
 * not a bug to route around, and not evidence of a weak GA/search if the
 * measured coverage plateaus well below 100%.
 *
 * java Node <processId> <totalNodes> [aliveGene]  (aliveGene omitted for
 * the fixed initiator, node 1)
 */

import java.net.*;
import java.io.*;
import java.util.*;

public class Node {
    static final int INITIATOR_ID = 1;
    static final int SOCKET_TIMEOUT_MS = 5000;

    public static void main(String[] args) throws IOException {
        int processId = Integer.parseInt(args[0]);
        int totalNodes = Integer.parseInt(args[1]);
        boolean alive = (processId == INITIATOR_ID) || (Integer.parseInt(args[2]) >= 500);

        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(SOCKET_TIMEOUT_MS);
        InetAddress addressIP = InetAddress.getLoopbackAddress();
        String ip = addressIP.getHostAddress();
        HelperClass.makeAddressFile(processId, 1, HelperClass.makeAddress(ip, socket.getLocalPort()));

        DatagramSocket announceSocket = new DatagramSocket();
        announceSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
        HelperClass.makeAddressFile(processId, 2, HelperClass.makeAddress(ip, announceSocket.getLocalPort()));

        HelperClass.markReady(processId);

        int[] otherIds = new int[totalNodes - 1];
        int idx = 0;
        for (int id = 1; id <= totalNodes; id++) {
            if (id != processId) {
                otherIds[idx++] = id;
            }
        }
        HelperClass.waitForReady(otherIds);

        Map<Integer, InetAddress> remoteIP = new HashMap<>();
        Map<Integer, Integer> remotePort = new HashMap<>();
        Map<Integer, InetAddress> announceIP = new HashMap<>();
        Map<Integer, Integer> announcePort = new HashMap<>();
        for (int id : otherIds) {
            remoteIP.put(id, InetAddress.getByName(HelperClass.readRemoteIP(id, 1)));
            remotePort.put(id, HelperClass.readRemotePort(id, 1));
            announceIP.put(id, InetAddress.getByName(HelperClass.readRemoteIP(id, 2)));
            announcePort.put(id, HelperClass.readRemotePort(id, 2));
        }

        boolean gotAnyOk = false;

        if (alive) {
            // Reply OK to every ELECTION received from a lower id -
            // order-independent (any single OK is enough for the sender to
            // back off), count varies 0..(processId-1) depending on which
            // lower ids are alive - a timeout-bounded loop, not unrolled
            // receives.
            if (processId > 1) {
                try {
                    while (true) {
                        byte[] receiveBuffer = new byte[255];
                        DatagramPacket electionPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                        socket.receive(electionPacket);
                        int fromId = Integer.parseInt(new String(electionPacket.getData()).trim().split(":")[1]);

                        byte[] okBuffer = ("OK:" + processId).getBytes();
                        DatagramPacket okPacket = new DatagramPacket(okBuffer, okBuffer.length, remoteIP.get(fromId),
                                remotePort.get(fromId));
                        socket.send(okPacket);
                    }
                } catch (SocketTimeoutException expectedEndOfElections) {
                    // No more ELECTION messages expected from lower ids.
                }
            }

            // Escalate: send ELECTION to every higher id and wait to see if
            // ANY of them is alive and replies OK - any single OK is
            // sufficient, so this is also a timeout-bounded loop, not
            // unrolled receives (a higher id's OK is order-independent too).
            if (processId < totalNodes) {
                byte[] electionBuffer = ("ELECTION:" + processId).getBytes();
                for (int higherId = processId + 1; higherId <= totalNodes; higherId++) {
                    DatagramPacket electionPacket = new DatagramPacket(electionBuffer, electionBuffer.length,
                            remoteIP.get(higherId), remotePort.get(higherId));
                    socket.send(electionPacket);
                }

                try {
                    while (true) {
                        byte[] receiveBuffer = new byte[255];
                        DatagramPacket okPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                        socket.receive(okPacket);
                        gotAnyOk = true;
                    }
                } catch (SocketTimeoutException expectedEndOfOks) {
                    // Done waiting for OKs from higher ids.
                }
            }
        }

        if (alive && !gotAnyOk) {
            System.out.println("Node " + processId + " declares itself coordinator.");

            byte[] coordinatorBuffer = ("COORDINATOR:" + processId).getBytes();
            for (int otherId : otherIds) {
                DatagramPacket coordinatorPacket = new DatagramPacket(coordinatorBuffer, coordinatorBuffer.length,
                        announceIP.get(otherId), announcePort.get(otherId));
                announceSocket.send(coordinatorPacket);
            }
        } else {
            // Ambiguous over every other id - the actual sender depends on
            // which combination of the other nodes' alive genes ends up
            // winning. Single, one-shot receive (not a loop) so this
            // specific call site keeps its own distinct edge id - safe from
            // stray ELECTION/OK packets because announceSocket is a
            // dedicated socket those never touch.
            byte[] receiveBuffer = new byte[255];
            DatagramPacket coordinatorPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            try {
                announceSocket.receive(coordinatorPacket);
                String message = new String(coordinatorPacket.getData()).trim();
                System.out.println("Node " + processId + " acknowledges " + message);
            } catch (SocketTimeoutException noAnnouncementArrived) {
                System.out.println("Node " + processId + " timed out waiting for a coordinator announcement.");
            }
        }

        socket.close();
        announceSocket.close();
        HelperClass.cleanup(otherIds);
    }
}
