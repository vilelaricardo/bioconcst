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
 * neighbor is fixed and unique, so there is no cross-instance occurrence-
 * identity ambiguity the way there would be in a ring. The four instances
 * DO still share one role-level edge id per call site, though, so the
 * topology config pins each instance's send target and receive source
 * with INSTANCE-SCOPED fixedMessageTargets/fixedMessageSources
 * (`"1@Router#main:1": 2`, etc.) - without that, the default role-level
 * pairing would treat any Router as a candidate sender/receiver for any
 * other, well past what the physical chain allows.
 *
 * Branch-duplicated sends (own static call site per branch) so the
 * finite-metric announcement and the infinity announcement stay
 * independently visible to coverage - same discipline as every other
 * benchmark in this suite's flag-gated sends.
 *
 * Real reachable denominator is 8/9 required edges, NOT 9/9: the metric
 * starts fixed at 0 (Destination always sends exactly 0) and R1's own
 * weight gene is capped at 15 by the oracle's own domain, so R1's
 * candidate (0 + w1) can never reach 16 - R1's OWN infinity announcement
 * (this class's edge :2, specifically the first router instance) is
 * structurally unreachable by construction, not a coverage-tool artifact.
 * Every other router's both branches (finite and infinity) are reachable,
 * confirmed by the smoke cases in config/rip-covinst-smoke.json. Not a
 * bug to route around - the wave starting at metric 0 is what RFC 1058's
 * own rule specifies, and a bounded four-hop wave from a real destination
 * genuinely cannot make its very first hop infinite.
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
