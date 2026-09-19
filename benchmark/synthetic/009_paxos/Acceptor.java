/**
 * Synthetic Benchmark - Basic (Single-Decree) Paxos (Acceptor)
 *
 * Implements an Acceptor's role from Lamport, "Paxos Made Simple", ACM
 * SIGACT News 32(4), 2001, Section 2.3: on PREPARE(n), promise iff n is
 * strictly greater than any proposal number already promised, otherwise
 * reject; on ACCEPT(n, v), accept iff n is still the highest promised (no
 * higher-numbered prepare arrived meanwhile), otherwise reject.
 *
 * Deliberate simplification, in the same spirit as this suite's other
 * benchmarks: only ONE proposer ever runs (no dueling/competing proposals,
 * no liveness-breaking duel the real protocol has to tolerate), so an
 * acceptor's phase-2 answer is fully determined by its own phase-1 answer -
 * nothing else can raise its promised number in between. This models the
 * per-acceptor promise/accept arithmetic and the proposer's two-phase
 * majority quorum, not multi-proposer contention, leader election, or log
 * replication (Multi-Paxos) - out of scope for what this suite tests.
 *
 * java Acceptor <processId> <highestPromisedGene>
 */

import java.net.*;
import java.io.*;

public class Acceptor {
    public static void main(String[] args) throws IOException {
        int processId = Integer.parseInt(args[0]);
        int highestPromised = Integer.parseInt(args[1]);

        DatagramSocket socket = new DatagramSocket();
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLoopbackAddress();
        String ip = addressIP.getHostAddress();

        String address = HelperClass.makeAddress(ip, port);
        HelperClass.makeAddressFile(processId, address);
        HelperClass.markReady(processId);

        HelperClass.waitForReady(0);

        String proposerIP = HelperClass.readRemoteIP(0);
        InetAddress remoteIP = InetAddress.getByName(proposerIP);
        int remotePort = HelperClass.readRemotePort(0);

        // Not ambiguous - the Proposer is the only process that ever sends
        // here, so no fixedMessageSources pin is needed for either receive.
        byte[] prepareBuffer = new byte[255];
        DatagramPacket preparePacket = new DatagramPacket(prepareBuffer, prepareBuffer.length);
        socket.receive(preparePacket);
        int proposalNumber = Integer.parseInt(new String(preparePacket.getData()).trim().split(":")[1]);

        boolean promise = proposalNumber > highestPromised;

        // Branch-duplicated sends (own static call site per branch) so
        // PROMISE vs REJECT stays independently visible to coverage - same
        // discipline as raft-election's Peer (GRANT vs DENY).
        if (promise) {
            byte[] sendBuffer = "PROMISE".getBytes();
            DatagramPacket promisePacket = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
            socket.send(promisePacket);
        } else {
            byte[] sendBuffer = "REJECT".getBytes();
            DatagramPacket rejectPacket = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
            socket.send(rejectPacket);
        }

        // Phase 2 - not ambiguous, same reasoning as the phase-1 receive.
        byte[] acceptBuffer = new byte[255];
        DatagramPacket acceptPacket = new DatagramPacket(acceptBuffer, acceptBuffer.length);
        socket.receive(acceptPacket);

        // No new comparison here - with a single proposer and no competing
        // proposal in between, phase 2's answer is exactly phase 1's own
        // promise decision (see class javadoc). Branch-duplicated sends,
        // same discipline as phase 1.
        if (promise) {
            byte[] sendBuffer = "ACCEPTED".getBytes();
            DatagramPacket acceptedPacket = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
            socket.send(acceptedPacket);
        } else {
            byte[] sendBuffer = "REJECTED".getBytes();
            DatagramPacket rejectedPacket = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
            socket.send(rejectedPacket);
        }

        socket.close();
    }
}
