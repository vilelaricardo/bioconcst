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
 * Real coverage ceiling, NOT 100% - same over-approximate-then-accept-a-
 * ceiling pattern quorum-handshake's 66.67% and bully-election's 22.2%
 * already document, here for yet another reason: phase 1 (PROMISE/REJECT)
 * and phase 2 (ACCEPTED/REJECTED) reuse the SAME socket and the SAME
 * role-level roleLink, so RequiredElementsGenerator has no concept of
 * "round" any more than ricart-agrawala's two sockets had a concept of
 * "channel" - an Acceptor's phase-1 send gets paired against BOTH the
 * Proposer's phase-1 receives (:1/:2/:3) AND its phase-2 receives
 * (:10/:11/:12), even though a phase-1 reply can never physically be
 * mistaken for a phase-2 one at the receiving end. Confirmed by hand
 * against the protocol logic above. Not a bug to route around, and not
 * evidence of a weak GA/search if measured coverage plateaus well below
 * 100% - raw baseline-vs-full comparisons on this benchmark should be
 * read with that ceiling in mind, not as a clean measure of search quality
 * alone.
 *
 * java Proposer
 */

import java.net.*;
import java.io.*;

public class Proposer {
    static final int PROPOSAL_NUMBER = 500;

    public static void main(String[] args) throws IOException {
        int processId = 0;

        DatagramSocket socket = new DatagramSocket();
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLoopbackAddress();
        String ip = addressIP.getHostAddress();

        String address = HelperClass.makeAddress(ip, port);
        HelperClass.makeAddressFile(processId, address);
        HelperClass.markReady(processId);

        HelperClass.waitForReady(1, 2, 3);

        String[] acceptorIP = new String[4];
        int[] acceptorPort = new int[4];
        for (int acceptorId = 1; acceptorId <= 3; acceptorId++) {
            acceptorIP[acceptorId] = HelperClass.readRemoteIP(acceptorId);
            acceptorPort[acceptorId] = HelperClass.readRemotePort(acceptorId);
        }

        // Broadcast PREPARE(n) to every acceptor - one static call site
        // (edge :0), executed 3 times at runtime; destination resolved from
        // each acceptor's real address, so this direction needs no
        // fixedMessageSources pin.
        byte[] prepare = ("PREPARE:" + PROPOSAL_NUMBER).getBytes();
        for (int acceptorId = 1; acceptorId <= 3; acceptorId++) {
            InetAddress remoteIP = InetAddress.getByName(acceptorIP[acceptorId]);
            DatagramPacket preparePacket = new DatagramPacket(prepare, prepare.length, remoteIP,
                    acceptorPort[acceptorId]);
            socket.send(preparePacket);
        }

        // Three UNROLLED receive statements (not a loop) - same discipline
        // as raft-election's Candidate: this is what makes which acceptor's
        // response lands in which slot a real, per-execution race.
        byte[] receiveBuffer1 = new byte[255];
        DatagramPacket promisePacket1 = new DatagramPacket(receiveBuffer1, receiveBuffer1.length);
        socket.receive(promisePacket1);
        String promise1 = new String(promisePacket1.getData()).trim();

        byte[] receiveBuffer2 = new byte[255];
        DatagramPacket promisePacket2 = new DatagramPacket(receiveBuffer2, receiveBuffer2.length);
        socket.receive(promisePacket2);
        String promise2 = new String(promisePacket2.getData()).trim();

        byte[] receiveBuffer3 = new byte[255];
        DatagramPacket promisePacket3 = new DatagramPacket(receiveBuffer3, receiveBuffer3.length);
        socket.receive(promisePacket3);
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
            socket.send(new DatagramPacket(accept, accept.length, InetAddress.getByName(acceptorIP[1]),
                    acceptorPort[1]));
            byte[] accept2 = "ACCEPT".getBytes();
            socket.send(new DatagramPacket(accept2, accept2.length, InetAddress.getByName(acceptorIP[2]),
                    acceptorPort[2]));
            byte[] accept3 = "ACCEPT".getBytes();
            socket.send(new DatagramPacket(accept3, accept3.length, InetAddress.getByName(acceptorIP[3]),
                    acceptorPort[3]));
        } else {
            System.out.println("Phase 1 failed (" + explicitPromises + "/3 promises). No consensus this round.");

            byte[] abort = "ABORT".getBytes();
            socket.send(new DatagramPacket(abort, abort.length, InetAddress.getByName(acceptorIP[1]),
                    acceptorPort[1]));
            byte[] abort2 = "ABORT".getBytes();
            socket.send(new DatagramPacket(abort2, abort2.length, InetAddress.getByName(acceptorIP[2]),
                    acceptorPort[2]));
            byte[] abort3 = "ABORT".getBytes();
            socket.send(new DatagramPacket(abort3, abort3.length, InetAddress.getByName(acceptorIP[3]),
                    acceptorPort[3]));
        }

        // Three more UNROLLED receives for the phase-2 responses - always
        // sent by every acceptor regardless of phase 1's outcome (ACCEPT or
        // ABORT both still expect a reply, see Acceptor.java), so this
        // round always runs; only the CONTENT (ACCEPTED/REJECTED vs. the
        // ABORT-branch's own reply) depends on phase 1.
        byte[] receiveBuffer4 = new byte[255];
        DatagramPacket resultPacket1 = new DatagramPacket(receiveBuffer4, receiveBuffer4.length);
        socket.receive(resultPacket1);
        String result1 = new String(resultPacket1.getData()).trim();

        byte[] receiveBuffer5 = new byte[255];
        DatagramPacket resultPacket2 = new DatagramPacket(receiveBuffer5, receiveBuffer5.length);
        socket.receive(resultPacket2);
        String result2 = new String(resultPacket2.getData()).trim();

        byte[] receiveBuffer6 = new byte[255];
        DatagramPacket resultPacket3 = new DatagramPacket(receiveBuffer6, receiveBuffer6.length);
        socket.receive(resultPacket3);
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

        socket.close();
        HelperClass.cleanup(0, 1, 2, 3);
    }
}
