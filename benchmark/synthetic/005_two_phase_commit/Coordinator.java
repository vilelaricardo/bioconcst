/**
 * Synthetic Benchmark - Two-Phase Commit (Coordinator)
 *
 * Models the VOTE and DECISION phases of the classic two-phase commit
 * protocol (Gray, "Notes on Data Base Operating Systems", 1978): the
 * coordinator broadcasts PREPARE, collects each participant's YES/NO vote,
 * and commits only if EVERY participant voted YES (unanimous agreement) -
 * otherwise it aborts. This benchmark models only the VOTE+DECISION
 * arithmetic; it does NOT model the coordinator's write-ahead log,
 * presumed-abort recovery, participant crash-recovery, or the classic 2PC
 * "blocking problem" (a participant left hanging if the coordinator itself
 * fails after PREPARE) - those are out of scope for what this suite tests.
 *
 * Which of the 4 participants' vote lands in votePacket1..4 is the same
 * genuine UDP race quorum-handshake's Coordinator already documents, scaled
 * to 4 candidate senders per receive slot. Unlike raft-election (majority,
 * ~50% threshold per peer), this benchmark's COMMIT branch requires ALL 4
 * independently-evolved participant inputs to simultaneously land in a
 * narrow window - a much rarer compound target, deliberately calibrated to
 * match quorum-handshake's own 0.16% compound probability
 * (0.04^2 there vs. 0.2^4 = 0.16% here), spread over 4 dimensions instead
 * of 2 - isolating dimensionality as the harder variable, not raw rarity.
 *
 * java Coordinator
 */

import java.net.*;
import java.io.*;

public class Coordinator {
    public static void main(String[] args) throws IOException {
        int processId = 0;

        DatagramSocket socket = new DatagramSocket();
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLoopbackAddress();
        String ip = addressIP.getHostAddress();

        String address = HelperClass.makeAddress(ip, port);
        HelperClass.makeAddressFile(processId, address);
        HelperClass.markReady(processId);

        HelperClass.waitForReady(1, 2, 3, 4);

        String[] participantIP = new String[5];
        int[] participantPort = new int[5];
        for (int participantId = 1; participantId <= 4; participantId++) {
            participantIP[participantId] = HelperClass.readRemoteIP(participantId);
            participantPort[participantId] = HelperClass.readRemotePort(participantId);
        }

        // Broadcast PREPARE to every participant - one static call site
        // (edge :0), executed 4 times at runtime; destination resolved from
        // each participant's real address, so this direction needs no
        // fixedMessageSources pin.
        byte[] prepare = "PREPARE".getBytes();
        for (int participantId = 1; participantId <= 4; participantId++) {
            InetAddress remoteIP = InetAddress.getByName(participantIP[participantId]);
            DatagramPacket preparePacket = new DatagramPacket(prepare, prepare.length, remoteIP,
                    participantPort[participantId]);
            socket.send(preparePacket);
        }

        // Four UNROLLED receive statements (not a loop) - same discipline
        // as raft-election's Candidate: this is what makes which
        // participant's vote lands in which slot a real, per-execution
        // fixed outcome that only varies ACROSS executions.
        byte[] receiveBuffer1 = new byte[255];
        DatagramPacket votePacket1 = new DatagramPacket(receiveBuffer1, receiveBuffer1.length);
        socket.receive(votePacket1);
        String vote1 = new String(votePacket1.getData()).trim();

        byte[] receiveBuffer2 = new byte[255];
        DatagramPacket votePacket2 = new DatagramPacket(receiveBuffer2, receiveBuffer2.length);
        socket.receive(votePacket2);
        String vote2 = new String(votePacket2.getData()).trim();

        byte[] receiveBuffer3 = new byte[255];
        DatagramPacket votePacket3 = new DatagramPacket(receiveBuffer3, receiveBuffer3.length);
        socket.receive(votePacket3);
        String vote3 = new String(votePacket3.getData()).trim();

        byte[] receiveBuffer4 = new byte[255];
        DatagramPacket votePacket4 = new DatagramPacket(receiveBuffer4, receiveBuffer4.length);
        socket.receive(votePacket4);
        String vote4 = new String(votePacket4.getData()).trim();

        boolean allYes = vote1.equals("YES") && vote2.equals("YES") && vote3.equals("YES") && vote4.equals("YES");

        // Branch-duplicated, unrolled sends (one static call site per
        // participant, per branch) so each outcome's 4 sends are
        // independently visible to coverage.
        if (allYes) {
            System.out.println("All participants voted YES. Committing.");

            byte[] commit = "COMMIT".getBytes();
            socket.send(new DatagramPacket(commit, commit.length, InetAddress.getByName(participantIP[1]),
                    participantPort[1]));
            byte[] commit2 = "COMMIT".getBytes();
            socket.send(new DatagramPacket(commit2, commit2.length, InetAddress.getByName(participantIP[2]),
                    participantPort[2]));
            byte[] commit3 = "COMMIT".getBytes();
            socket.send(new DatagramPacket(commit3, commit3.length, InetAddress.getByName(participantIP[3]),
                    participantPort[3]));
            byte[] commit4 = "COMMIT".getBytes();
            socket.send(new DatagramPacket(commit4, commit4.length, InetAddress.getByName(participantIP[4]),
                    participantPort[4]));
        } else {
            System.out.println("At least one participant voted NO. Aborting.");

            byte[] abort = "ABORT".getBytes();
            socket.send(new DatagramPacket(abort, abort.length, InetAddress.getByName(participantIP[1]),
                    participantPort[1]));
            byte[] abort2 = "ABORT".getBytes();
            socket.send(new DatagramPacket(abort2, abort2.length, InetAddress.getByName(participantIP[2]),
                    participantPort[2]));
            byte[] abort3 = "ABORT".getBytes();
            socket.send(new DatagramPacket(abort3, abort3.length, InetAddress.getByName(participantIP[3]),
                    participantPort[3]));
            byte[] abort4 = "ABORT".getBytes();
            socket.send(new DatagramPacket(abort4, abort4.length, InetAddress.getByName(participantIP[4]),
                    participantPort[4]));
        }

        socket.close();
        HelperClass.cleanup(0, 1, 2, 3, 4);
    }
}
