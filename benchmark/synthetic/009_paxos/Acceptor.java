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
 * Two sockets per acceptor (same discipline as bully-election's Node and
 * ricart-agrawala's Node): a phase-1 socket for PREPARE/PROMISE/REJECT and
 * a SEPARATE, dedicated phase-2 socket for ACCEPT/ACCEPTED/REJECTED -
 * without the split, RequiredElementsGenerator has no concept of "round"
 * any more than it has a concept of "socket", and pairs a phase-1 send
 * against the Proposer's phase-2 receives too (and vice versa), producing
 * a large, permanently-unreachable spurious-pairing ceiling (confirmed
 * empirically: see git history for the single-socket version's measured
 * numbers before this fix).
 *
 * java Acceptor <processId> <highestPromisedGene>
 */

import java.net.*;
import java.io.*;

public class Acceptor {
    public static void main(String[] args) throws IOException {
        int processId = Integer.parseInt(args[0]);
        int highestPromised = Integer.parseInt(args[1]);

        DatagramSocket phase1Socket = new DatagramSocket();
        InetAddress addressIP = InetAddress.getLoopbackAddress();
        String ip = addressIP.getHostAddress();
        HelperClass.makeAddressFile(processId, 1, HelperClass.makeAddress(ip, phase1Socket.getLocalPort()));

        DatagramSocket phase2Socket = new DatagramSocket();
        HelperClass.makeAddressFile(processId, 2, HelperClass.makeAddress(ip, phase2Socket.getLocalPort()));

        HelperClass.markReady(processId);

        HelperClass.waitForReady(0);

        InetAddress proposerPhase1IP = InetAddress.getByName(HelperClass.readRemoteIP(0, 1));
        int proposerPhase1Port = HelperClass.readRemotePort(0, 1);
        InetAddress proposerPhase2IP = InetAddress.getByName(HelperClass.readRemoteIP(0, 2));
        int proposerPhase2Port = HelperClass.readRemotePort(0, 2);

        // Not ambiguous - the Proposer is the only process that ever sends
        // here, so no fixedMessageSources pin is needed for either receive.
        byte[] prepareBuffer = new byte[255];
        DatagramPacket preparePacket = new DatagramPacket(prepareBuffer, prepareBuffer.length);
        phase1Socket.receive(preparePacket);
        int proposalNumber = Integer.parseInt(new String(preparePacket.getData()).trim().split(":")[1]);

        boolean promise = proposalNumber > highestPromised;

        // Branch-duplicated sends (own static call site per branch) so
        // PROMISE vs REJECT stays independently visible to coverage - same
        // discipline as raft-election's Peer (GRANT vs DENY).
        if (promise) {
            byte[] sendBuffer = "PROMISE".getBytes();
            DatagramPacket promisePacket = new DatagramPacket(sendBuffer, sendBuffer.length, proposerPhase1IP,
                    proposerPhase1Port);
            phase1Socket.send(promisePacket);
        } else {
            byte[] sendBuffer = "REJECT".getBytes();
            DatagramPacket rejectPacket = new DatagramPacket(sendBuffer, sendBuffer.length, proposerPhase1IP,
                    proposerPhase1Port);
            phase1Socket.send(rejectPacket);
        }

        // Phase 2 - not ambiguous, same reasoning as the phase-1 receive.
        byte[] acceptBuffer = new byte[255];
        DatagramPacket acceptPacket = new DatagramPacket(acceptBuffer, acceptBuffer.length);
        phase2Socket.receive(acceptPacket);

        // No new comparison here - with a single proposer and no competing
        // proposal in between, phase 2's answer is exactly phase 1's own
        // promise decision (see class javadoc). Branch-duplicated sends,
        // same discipline as phase 1.
        if (promise) {
            byte[] sendBuffer = "ACCEPTED".getBytes();
            DatagramPacket acceptedPacket = new DatagramPacket(sendBuffer, sendBuffer.length, proposerPhase2IP,
                    proposerPhase2Port);
            phase2Socket.send(acceptedPacket);
        } else {
            byte[] sendBuffer = "REJECTED".getBytes();
            DatagramPacket rejectedPacket = new DatagramPacket(sendBuffer, sendBuffer.length, proposerPhase2IP,
                    proposerPhase2Port);
            phase2Socket.send(rejectedPacket);
        }

        phase1Socket.close();
        phase2Socket.close();
    }
}
