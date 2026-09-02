/**
 * Synthetic Benchmark - Quorum Handshake (Coordinator)
 *
 * Waits for both peers' votes. Only when BOTH voted QUORUM does it run the
 * extra "celebration" round with each peer - a sync-edge structure that is
 * only reachable through a compound condition over two independently
 * evolved arguments, unlike the rest of the suite where sync edges are
 * reachable almost regardless of the input value.
 *
 * java Coordinator
 */

import java.net.*;
import java.io.*;

public class Coordinator {
    public static void main(String[] args) throws IOException {
        int processId = 0;

        DatagramSocket socket = new DatagramSocket();
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLocalHost();
        String ip = addressIP.getHostAddress();

        String address = HelperClass.makeAddress(ip, port);
        HelperClass.makeAddressFile(processId, address);

        HelperClass.waitPeers();

        String hostIP1 = HelperClass.readRemoteIP(1);
        InetAddress remoteIP1 = InetAddress.getByName(hostIP1);
        int remotePort1 = HelperClass.readRemotePort(1);

        String hostIP2 = HelperClass.readRemoteIP(2);
        InetAddress remoteIP2 = InetAddress.getByName(hostIP2);
        int remotePort2 = HelperClass.readRemotePort(2);

        java.nio.file.Files.createFile(java.nio.file.Paths.get("coordinator"));

        byte[] receiveBuffer1 = new byte[255];
        DatagramPacket votePacket1 = new DatagramPacket(receiveBuffer1, receiveBuffer1.length);
        socket.receive(votePacket1);
        String vote1 = new String(votePacket1.getData()).trim();

        byte[] receiveBuffer2 = new byte[255];
        DatagramPacket votePacket2 = new DatagramPacket(receiveBuffer2, receiveBuffer2.length);
        socket.receive(votePacket2);
        String vote2 = new String(votePacket2.getData()).trim();

        if (vote1.equals("QUORUM") && vote2.equals("QUORUM")) {
            System.out.println("Quorum reached! Celebrating with both peers.");

            byte[] celebrate = "CELEBRATE".getBytes();
            DatagramPacket celebratePacket1 = new DatagramPacket(celebrate, celebrate.length, remoteIP1, remotePort1);
            socket.send(celebratePacket1);

            byte[] celebrate2 = "CELEBRATE".getBytes();
            DatagramPacket celebratePacket2 = new DatagramPacket(celebrate2, celebrate2.length, remoteIP2, remotePort2);
            socket.send(celebratePacket2);
        } else {
            System.out.println("No quorum. Continuing normally.");

            byte[] ack = "ACK".getBytes();
            DatagramPacket ackPacket1 = new DatagramPacket(ack, ack.length, remoteIP1, remotePort1);
            socket.send(ackPacket1);

            byte[] ack2 = "ACK".getBytes();
            DatagramPacket ackPacket2 = new DatagramPacket(ack2, ack2.length, remoteIP2, remotePort2);
            socket.send(ackPacket2);
        }

        socket.close();
        HelperClass.closeFiles();
    }
}
