/**
 * Synthetic Benchmark - Two-Phase Commit (Participant)
 *
 * Votes YES only when its own value lands inside a narrow secret window out
 * of a much wider range - mirrors quorum-handshake's Peer, but the window
 * here (400-599 out of [0,999], p=0.2) is calibrated so that
 * P(all 4 vote YES) = 0.2^4 = 0.16%, matching quorum-handshake's own
 * compound-target rarity (0.04^2 = 0.16%) spread over 4 independent
 * dimensions instead of 2.
 *
 * java Participant <processId> <value>
 */

import java.net.*;
import java.io.*;

public class Participant {
    static final int WINDOW_LOW = 400;
    static final int WINDOW_HIGH = 599;

    public static void main(String[] args) throws IOException {
        int processId = Integer.parseInt(args[0]);
        int value = Integer.parseInt(args[1]);

        DatagramSocket socket = new DatagramSocket();
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLoopbackAddress();
        String ip = addressIP.getHostAddress();

        String address = HelperClass.makeAddress(ip, port);
        HelperClass.makeAddressFile(processId, address);
        HelperClass.markReady(processId);

        HelperClass.waitForReady(0);

        String coordinatorIP = HelperClass.readRemoteIP(0);
        InetAddress remoteIP = InetAddress.getByName(coordinatorIP);
        int remotePort = HelperClass.readRemotePort(0);

        // Not ambiguous - Coordinator is the only process that ever sends
        // here, so no fixedMessageSources pin is needed for this receive.
        byte[] receiveBuffer = new byte[255];
        DatagramPacket preparePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(preparePacket);

        boolean inWindow = (value >= WINDOW_LOW) && (value <= WINDOW_HIGH);

        // Branch-duplicated sends (own static call site per branch) so YES
        // vs NO stays independently visible to coverage.
        if (inWindow) {
            byte[] sendBuffer = "YES".getBytes();
            DatagramPacket votePacket = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
            socket.send(votePacket);
        } else {
            byte[] sendBuffer = "NO".getBytes();
            DatagramPacket votePacket = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
            socket.send(votePacket);
        }

        byte[] resultBuffer = new byte[255];
        DatagramPacket resultPacket = new DatagramPacket(resultBuffer, resultBuffer.length);
        socket.receive(resultPacket);
        String result = new String(resultPacket.getData()).trim();

        if (result.equals("COMMIT")) {
            System.out.println("Participant " + processId + " commits.");
        } else {
            System.out.println("Participant " + processId + " aborts.");
        }

        socket.close();
    }
}
