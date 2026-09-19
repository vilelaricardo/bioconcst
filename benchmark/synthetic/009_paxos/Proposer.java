/**
 * Synthetic Benchmark - Basic (Single-Decree) Paxos (Proposer)
 *
 * Models the Proposer's two-phase protocol from Lamport, "Paxos Made
 * Simple", ACM SIGACT News 32(4), 2001, Section 2.3: broadcast
 * PREPARE(n), and if a majority of acceptors PROMISE, broadcast ACCEPT(n)
 * and declare consensus reached only if a majority also ACCEPTED. This
 * benchmark models only one proposer's single round (no dueling
 * proposers, no proposal-number escalation on failure) - see Acceptor.java
 * for the full scope note.
 *
 * Which of the 3 acceptors' response lands in slot 1..3 is a genuine UDP
 * race, same class of thing as raft-election's Candidate and
 * two-phase-commit's Coordinator.
 *
 * Two sockets (phase 1 vs phase 2), same discipline as bully-election's
 * Node and ricart-agrawala's Node: without the split, a phase-2 packet
 * arriving early could be wrongly consumed by a still-pending phase-1
 * receive (a real runtime hazard, distinct from the point below).
 *
 * Open question, NOT yet resolved either way: an earlier single-socket
 * version showed large, execution-to-execution VARIABLE uncovered counts
 * (unlike bully-election's exactly-reproducible 22.2% ceiling), including
 * baseline reaching full coverage in at least one execution - that
 * variability argues against a hard structural ceiling and for "just
 * needs a bigger search budget," but this has not been confirmed with a
 * properly-powered pilot yet. Note also that this two-socket split is a
 * RUNTIME fix (prevents actual cross-round packet mixups); it is NOT
 * known to narrow RequiredElementsGenerator's STATIC pairing, which
 * reasons over bytecode call sites, not live socket identity - do not
 * assume it also fixes any over-approximation without checking the actual
 * post-fix numbers.
 *
 * java Proposer
 */

import java.net.*;
import java.io.*;

public class Proposer {
    static final int PROPOSAL_NUMBER = 500;

    public static void main(String[] args) throws IOException {
        int processId = 0;

        DatagramSocket phase1Socket = new DatagramSocket();
        InetAddress addressIP = InetAddress.getLoopbackAddress();
        String ip = addressIP.getHostAddress();
        HelperClass.makeAddressFile(processId, 1, HelperClass.makeAddress(ip, phase1Socket.getLocalPort()));

        DatagramSocket phase2Socket = new DatagramSocket();
        HelperClass.makeAddressFile(processId, 2, HelperClass.makeAddress(ip, phase2Socket.getLocalPort()));

        HelperClass.markReady(processId);

        HelperClass.waitForReady(1, 2, 3);

        InetAddress[] acceptorPhase1IP = new InetAddress[4];
        int[] acceptorPhase1Port = new int[4];
        InetAddress[] acceptorPhase2IP = new InetAddress[4];
        int[] acceptorPhase2Port = new int[4];
        for (int acceptorId = 1; acceptorId <= 3; acceptorId++) {
            acceptorPhase1IP[acceptorId] = InetAddress.getByName(HelperClass.readRemoteIP(acceptorId, 1));
            acceptorPhase1Port[acceptorId] = HelperClass.readRemotePort(acceptorId, 1);
            acceptorPhase2IP[acceptorId] = InetAddress.getByName(HelperClass.readRemoteIP(acceptorId, 2));
            acceptorPhase2Port[acceptorId] = HelperClass.readRemotePort(acceptorId, 2);
        }

        // Broadcast PREPARE(n) to every acceptor - one static call site
        // (edge :0), executed 3 times at runtime; destination resolved from
        // each acceptor's real address, so this direction needs no
        // fixedMessageSources pin.
        byte[] prepare = ("PREPARE:" + PROPOSAL_NUMBER).getBytes();
        for (int acceptorId = 1; acceptorId <= 3; acceptorId++) {
            DatagramPacket preparePacket = new DatagramPacket(prepare, prepare.length, acceptorPhase1IP[acceptorId],
                    acceptorPhase1Port[acceptorId]);
            phase1Socket.send(preparePacket);
        }

        // Three UNROLLED receive statements (not a loop) - same discipline
        // as raft-election's Candidate: this is what makes which acceptor's
        // response lands in which slot a real, per-execution race.
        byte[] receiveBuffer1 = new byte[255];
        DatagramPacket promisePacket1 = new DatagramPacket(receiveBuffer1, receiveBuffer1.length);
        phase1Socket.receive(promisePacket1);
        String promise1 = new String(promisePacket1.getData()).trim();

        byte[] receiveBuffer2 = new byte[255];
        DatagramPacket promisePacket2 = new DatagramPacket(receiveBuffer2, receiveBuffer2.length);
        phase1Socket.receive(promisePacket2);
        String promise2 = new String(promisePacket2.getData()).trim();

        byte[] receiveBuffer3 = new byte[255];
        DatagramPacket promisePacket3 = new DatagramPacket(receiveBuffer3, receiveBuffer3.length);
        phase1Socket.receive(promisePacket3);
        String promise3 = new String(promisePacket3.getData()).trim();

        int explicitPromises = 0;
        if (promise1.equals("PROMISE")) explicitPromises++;
        if (promise2.equals("PROMISE")) explicitPromises++;
        if (promise3.equals("PROMISE")) explicitPromises++;

        // Majority of 3 is 2.
        boolean phase1Majority = explicitPromises >= 2;

        // Branch-duplicated, unrolled sends (one static call site per
        // acceptor, per branch) so each outcome's 3 sends are independently
        // visible to coverage - same discipline as raft-election's
        // Candidate (LEADER vs SPLIT_VOTE).
        if (phase1Majority) {
            System.out.println("Phase 1 majority (" + explicitPromises + "/3 promises). Sending ACCEPT.");

            byte[] accept = "ACCEPT".getBytes();
            phase2Socket.send(new DatagramPacket(accept, accept.length, acceptorPhase2IP[1], acceptorPhase2Port[1]));
            byte[] accept2 = "ACCEPT".getBytes();
            phase2Socket
                    .send(new DatagramPacket(accept2, accept2.length, acceptorPhase2IP[2], acceptorPhase2Port[2]));
            byte[] accept3 = "ACCEPT".getBytes();
            phase2Socket
                    .send(new DatagramPacket(accept3, accept3.length, acceptorPhase2IP[3], acceptorPhase2Port[3]));
        } else {
            System.out.println("Phase 1 failed (" + explicitPromises + "/3 promises). No consensus this round.");

            byte[] abort = "ABORT".getBytes();
            phase2Socket.send(new DatagramPacket(abort, abort.length, acceptorPhase2IP[1], acceptorPhase2Port[1]));
            byte[] abort2 = "ABORT".getBytes();
            phase2Socket.send(new DatagramPacket(abort2, abort2.length, acceptorPhase2IP[2], acceptorPhase2Port[2]));
            byte[] abort3 = "ABORT".getBytes();
            phase2Socket.send(new DatagramPacket(abort3, abort3.length, acceptorPhase2IP[3], acceptorPhase2Port[3]));
        }

        // Three more UNROLLED receives for the phase-2 responses - always
        // sent by every acceptor regardless of phase 1's outcome (ACCEPT or
        // ABORT both still expect a reply, see Acceptor.java), so this
        // round always runs; only the CONTENT (ACCEPTED/REJECTED vs. the
        // ABORT-branch's own reply) depends on phase 1.
        byte[] receiveBuffer4 = new byte[255];
        DatagramPacket resultPacket1 = new DatagramPacket(receiveBuffer4, receiveBuffer4.length);
        phase2Socket.receive(resultPacket1);
        String result1 = new String(resultPacket1.getData()).trim();

        byte[] receiveBuffer5 = new byte[255];
        DatagramPacket resultPacket2 = new DatagramPacket(receiveBuffer5, receiveBuffer5.length);
        phase2Socket.receive(resultPacket2);
        String result2 = new String(resultPacket2.getData()).trim();

        byte[] receiveBuffer6 = new byte[255];
        DatagramPacket resultPacket3 = new DatagramPacket(receiveBuffer6, receiveBuffer6.length);
        phase2Socket.receive(resultPacket3);
        String result3 = new String(resultPacket3.getData()).trim();

        int explicitAccepted = 0;
        if (result1.equals("ACCEPTED")) explicitAccepted++;
        if (result2.equals("ACCEPTED")) explicitAccepted++;
        if (result3.equals("ACCEPTED")) explicitAccepted++;

        boolean consensusReached = phase1Majority && explicitAccepted >= 2;

        if (consensusReached) {
            System.out.println("Consensus reached (" + explicitAccepted + "/3 accepted).");
        } else {
            System.out.println("No consensus (" + explicitAccepted + "/3 accepted).");
        }

        phase1Socket.close();
        phase2Socket.close();
        HelperClass.cleanup(0, 1, 2, 3);
    }
}
