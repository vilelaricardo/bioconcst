/**
 * Synthetic Benchmark - Raft RequestVote / Leader Election (Peer)
 *
 * Implements the follower side of the RequestVote RPC (Ongaro & Ousterhout
 * 2014, Section 5.1): grant the vote iff the candidate's declared term is
 * strictly greater than this peer's own currentTerm, otherwise deny.
 *
 * java Peer <processId> <currentTerm>
 */

import java.net.*;
import java.io.*;

public class Peer {
    public static void main(String[] args) throws IOException {
        int processId = Integer.parseInt(args[0]);
        int currentTerm = Integer.parseInt(args[1]);

        DatagramSocket socket = new DatagramSocket();
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLoopbackAddress();
        String ip = addressIP.getHostAddress();

        String address = HelperClass.makeAddress(ip, port);
        HelperClass.makeAddressFile(processId, address);
        HelperClass.markReady(processId);

        HelperClass.waitForReady(0);

        String candidateIP = HelperClass.readRemoteIP(0);
        InetAddress remoteIP = InetAddress.getByName(candidateIP);
        int remotePort = HelperClass.readRemotePort(0);

        // Not ambiguous - Candidate is the only process that ever sends
        // here, so no fixedMessageSources pin is needed for this receive.
        byte[] receiveBuffer = new byte[255];
        DatagramPacket requestVotePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(requestVotePacket);
        String requestVote = new String(requestVotePacket.getData()).trim();
        int candidateTerm = Integer.parseInt(requestVote.split(":")[1]);

        boolean grant = currentTerm < candidateTerm;

        // Branch-duplicated sends (own static call site per branch) so
        // GRANT vs DENY stays independently visible to coverage - same
        // discipline as quorum-handshake's Peer (QUORUM vs NORMAL).
        if (grant) {
            byte[] sendBuffer = "GRANT".getBytes();
            DatagramPacket votePacket = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
            socket.send(votePacket);
        } else {
            byte[] sendBuffer = "DENY".getBytes();
            DatagramPacket votePacket = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
            socket.send(votePacket);
        }

        byte[] resultBuffer = new byte[255];
        DatagramPacket resultPacket = new DatagramPacket(resultBuffer, resultBuffer.length);
        socket.receive(resultPacket);
        String result = new String(resultPacket.getData()).trim();

        if (result.equals("LEADER")) {
            System.out.println("Peer " + processId + " recognizes the new leader.");
        } else {
            System.out.println("Peer " + processId + " sees a split vote, no leader this round.");
        }

        socket.close();
    }
}
