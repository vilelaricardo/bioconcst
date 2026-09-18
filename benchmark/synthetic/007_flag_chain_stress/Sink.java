/**
 * Synthetic diagnostic fixture - FlagChainStress (Sink)
 *
 * Receives with a short timeout instead of blocking forever, since Relay
 * might never send anything at all. Receiving FORWARD is the one hard
 * required element in this fixture.
 *
 * java Sink
 */

import java.net.*;
import java.io.*;

public class Sink {
    // 1000ms, not the original 300ms - Codex's suggestion, since 300ms
    // was an aggressive margin for a loopback UDP send that could still
    // occasionally lose the race under load.
    static final int RECEIVE_TIMEOUT_MS = 1000;

    public static void main(String[] args) throws IOException {
        int processId = 2;

        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(RECEIVE_TIMEOUT_MS);
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLoopbackAddress();
        String address = HelperClass.makeAddress(addressIP.getHostAddress(), port);
        HelperClass.makeAddressFile(processId, address);

        try {
            byte[] receiveBuffer = new byte[255];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);
        } catch (SocketTimeoutException e) {
            // Nothing arrived this run - not a failure.
        }

        socket.close();
    }
}
