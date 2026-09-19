/**
 * Synthetic Benchmark - RIP Distance-Vector Wave (Monitor)
 *
 * Terminal observer of the wave: receives R4's final announcement and
 * distinguishes a finite route (metric < 16) from infinity (metric == 16,
 * RFC 1058's unreachable convention). See Destination.java for the full
 * scope note.
 *
 * java Monitor
 */

import java.net.*;
import java.io.*;

public class Monitor {
    static final int INFINITY = 16;

    public static void main(String[] args) throws IOException {
        int processId = 5;

        DatagramSocket socket = new DatagramSocket();
        InetAddress addressIP = InetAddress.getLoopbackAddress();
        String ip = addressIP.getHostAddress();
        HelperClass.makeAddressFile(processId, HelperClass.makeAddress(ip, socket.getLocalPort()));
        HelperClass.markReady(processId);

        // Not ambiguous - R4 is the only process that ever sends here.
        byte[] receiveBuffer = new byte[255];
        DatagramPacket metricPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(metricPacket);
        int finalMetric = Integer.parseInt(new String(metricPacket.getData()).trim());

        boolean reachable = finalMetric < INFINITY;

        if (reachable) {
            System.out.println("Monitor sees a finite route, metric " + finalMetric + ".");
        } else {
            System.out.println("Monitor sees the route as unreachable (infinity).");
        }

        socket.close();
    }
}
