/**
 * Synthetic Benchmark - RIP Distance-Vector Wave (Destination)
 *
 * Models one bounded wave of the RIP distance-vector update rule (RFC 1058,
 * Section 3.2): each hop adds its own outgoing-interface metric to a
 * received route advertisement, and 16 stands for infinity/unreachable.
 * Honest scope: this is a single wave through a fixed line of routers, not
 * a full RIP daemon - no periodic timers, no split horizon/poisoned
 * reverse, no topology changes, no multiple destinations. It models only
 * the additive-metric arithmetic and the finite-vs-infinity branch that
 * arithmetic feeds into.
 *
 * Destination -> R1 -> R2 -> R3 -> R4 -> Monitor, metric 0 at the source.
 *
 * java Destination
 */

import java.net.*;
import java.io.*;

public class Destination {
    public static void main(String[] args) throws IOException {
        int processId = 0;
        int downstreamId = 1;

        DatagramSocket socket = new DatagramSocket();
        InetAddress addressIP = InetAddress.getLoopbackAddress();
        String ip = addressIP.getHostAddress();
        HelperClass.makeAddressFile(processId, HelperClass.makeAddress(ip, socket.getLocalPort()));
        HelperClass.markReady(processId);

        HelperClass.waitForReady(downstreamId);
        InetAddress downstreamIP = InetAddress.getByName(HelperClass.readRemoteIP(downstreamId));
        int downstreamPort = HelperClass.readRemotePort(downstreamId);

        // Single send - not ambiguous, R1 is the only process that ever
        // receives here.
        byte[] metric = "0".getBytes();
        socket.send(new DatagramPacket(metric, metric.length, downstreamIP, downstreamPort));

        socket.close();
    }
}
