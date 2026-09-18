/**
 * Synthetic Benchmark - Raft RequestVote / Leader Election (Candidate)
 *
 * Models the RequestVote RPC and majority-quorum arithmetic from Section
 * 5.1-5.2 of Ongaro & Ousterhout, "In Search of an Understandable Consensus
 * Algorithm (Extended Version)", 2014. This benchmark models ONLY one
 * candidate's single election round (term comparison + majority arithmetic).
 * It does NOT model multiple concurrent candidates (so this is not a
 * literal multi-candidate split vote), leader heartbeats, log replication,
 * or Raft's randomized election-timeout mechanism - those are out of scope
 * for what this suite tests (interleaving coverage of a fixed message
 * topology), not oversights.
 *
 * Which of the 4 peers' vote lands in votePacket1..4 is a genuine UDP race
 * (same class of thing quorum-handshake's Coordinator already documents) -
 * with 4 candidate senders per receive slot, there are up to 4! arrival
 * orderings to explore across the test suite, on top of the
 * majority-vs-split-vote branch itself.
 *
 * java Candidate
 */

import java.net.*;
import java.io.*;

public class Candidate {
    static final int CANDIDATE_TERM = 500;

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

        String[] peerIP = new String[5];
        int[] peerPort = new int[5];
        for (int peerId = 1; peerId <= 4; peerId++) {
            peerIP[peerId] = HelperClass.readRemoteIP(peerId);
            peerPort[peerId] = HelperClass.readRemotePort(peerId);
        }

        // Broadcast RequestVote(term=CANDIDATE_TERM) to every peer - one
        // static call site (edge :0), executed 4 times at runtime; the
        // destination is resolved from each peer's real address, so this
        // direction needs no fixedMessageSources pin (see HelperClass /
        // quorum-handshake precedent: only the convergent many-to-one
        // direction below is ambiguous).
        byte[] requestVote = ("REQUESTVOTE:" + CANDIDATE_TERM).getBytes();
        for (int peerId = 1; peerId <= 4; peerId++) {
            InetAddress remoteIP = InetAddress.getByName(peerIP[peerId]);
            DatagramPacket requestVotePacket = new DatagramPacket(requestVote, requestVote.length, remoteIP,
                    peerPort[peerId]);
            socket.send(requestVotePacket);
        }

        // Four UNROLLED receive statements (not a loop) - this is what
        // makes which peer's vote lands in which slot a real, per-execution
        // fixed outcome that only varies ACROSS executions, exactly like
        // quorum-handshake's votePacket1/votePacket2. Collapsing this into
        // a loop over one receive() call site would make the race
        // invisible to MESSAGE-kind coverage entirely (see roller-coaster's
        // Car, which does exactly that and hits 96.6% coverage in
        // generation 1 as a result).
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

        int explicitGrants = 0;
        if (vote1.equals("GRANT")) explicitGrants++;
        if (vote2.equals("GRANT")) explicitGrants++;
        if (vote3.equals("GRANT")) explicitGrants++;
        if (vote4.equals("GRANT")) explicitGrants++;

        // Majority of 5 (candidate's own implicit self-vote + 4 peers) is
        // 3 - i.e. at least 2 explicit grants among the peers.
        boolean majority = explicitGrants >= 2;

        // Branch-duplicated, unrolled sends (one static call site per
        // peer, per branch) so each outcome's 4 sends are independently
        // visible to coverage - same discipline as quorum-handshake's
        // Coordinator (CELEBRATE vs ACK branches).
        if (majority) {
            System.out.println("Majority reached (" + explicitGrants + "/4 explicit grants). Becoming leader.");

            byte[] leader = "LEADER".getBytes();
            socket.send(new DatagramPacket(leader, leader.length, InetAddress.getByName(peerIP[1]), peerPort[1]));
            byte[] leader2 = "LEADER".getBytes();
            socket.send(new DatagramPacket(leader2, leader2.length, InetAddress.getByName(peerIP[2]), peerPort[2]));
            byte[] leader3 = "LEADER".getBytes();
            socket.send(new DatagramPacket(leader3, leader3.length, InetAddress.getByName(peerIP[3]), peerPort[3]));
            byte[] leader4 = "LEADER".getBytes();
            socket.send(new DatagramPacket(leader4, leader4.length, InetAddress.getByName(peerIP[4]), peerPort[4]));
        } else {
            System.out.println("Split vote (" + explicitGrants + "/4 explicit grants). No leader this round.");

            byte[] split = "SPLIT_VOTE".getBytes();
            socket.send(new DatagramPacket(split, split.length, InetAddress.getByName(peerIP[1]), peerPort[1]));
            byte[] split2 = "SPLIT_VOTE".getBytes();
            socket.send(new DatagramPacket(split2, split2.length, InetAddress.getByName(peerIP[2]), peerPort[2]));
            byte[] split3 = "SPLIT_VOTE".getBytes();
            socket.send(new DatagramPacket(split3, split3.length, InetAddress.getByName(peerIP[3]), peerPort[3]));
            byte[] split4 = "SPLIT_VOTE".getBytes();
            socket.send(new DatagramPacket(split4, split4.length, InetAddress.getByName(peerIP[4]), peerPort[4]));
        }

        socket.close();
        HelperClass.cleanup(0, 1, 2, 3, 4);
    }
}
