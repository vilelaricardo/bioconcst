/**
 * Synthetic Benchmark - RIP Distance-Vector Wave (Router)
 *
 * One hop of the wave (see Destination.java for the full scope note):
 * receives the upstream metric, adds this router's own outgoing-interface
 * cost (the evolved gene), and announces the new metric if still reachable
 * (< 16) or 16 (infinity) otherwise - RFC 1058's own metric-16-means-
 * unreachable convention.
 *
 * Four instances of this same class play R1..R4 - safe to share (unlike
 * ring/quorum topologies with multiple candidate senders per receive
 * slot), because this is a strictly linear chain: each router's upstream
 * neighbor is fixed and unique, so no fixedMessageSources pin is needed
 * for the receive, and no cross-instance occurrence-identity ambiguity
 * arises the way it would in a ring.
 *
 * Branch-duplicated sends (own static call site per branch) so the
 * finite-metric announcement and the infinity announcement stay
 * independently visible to coverage - same discipline as every other
 * benchmark in this suite's flag-gated sends.
 *
 * java Router <processId> <downstreamId> <weightGene>
 */

import java.net.*;
import java.io.*;

public class Router {
    static final int INFINITY = 16;

    public static void main(String[] args) throws IOException {
        int processId = Integer.parseInt(args[0]);
        int downstreamId = Integer.parseInt(args[1]);
        int weight = Integer.parseInt(args[2]);

        DatagramSocket socket = new DatagramSocket();
        InetAddress addressIP = InetAddress.getLoopbackAddress();
        String ip = addressIP.getHostAddress();
        HelperClass.makeAddressFile(processId, HelperClass.makeAddress(ip, socket.getLocalPort()));
        HelperClass.markReady(processId);

        HelperClass.waitForReady(downstreamId);
        InetAddress downstreamIP = InetAddress.getByName(HelperClass.readRemoteIP(downstreamId));
        int downstreamPort = HelperClass.readRemotePort(downstreamId);

        // Not ambiguous - the upstream neighbor is the only process that
        // ever sends here, so no fixedMessageSources pin is needed.
        byte[] receiveBuffer = new byte[255];
        DatagramPacket metricPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(metricPacket);
        int receivedMetric = Integer.parseInt(new String(metricPacket.getData()).trim());

        int candidate = receivedMetric + weight;
        boolean reachable = candidate < INFINITY;

        if (reachable) {
            byte[] announce = String.valueOf(candidate).getBytes();
            socket.send(new DatagramPacket(announce, announce.length, downstreamIP, downstreamPort));
        } else {
            byte[] announce = String.valueOf(INFINITY).getBytes();
            socket.send(new DatagramPacket(announce, announce.length, downstreamIP, downstreamPort));
        }

        socket.close();
    }
}
