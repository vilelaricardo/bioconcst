/**
 * Synthetic Benchmark - Quorum Handshake (Peer)
 *
 * A peer only votes QUORUM when its own value lands inside a narrow secret
 * window out of a much wider range. The coordinator only runs the extra
 * "celebration" round when every peer voted QUORUM in the same test case -
 * a compound condition across independently evolved arguments, with no
 * partial credit available from a single peer's own value being "closer".
 *
 * java Peer <processId> <value>
 */

import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Peer {
    static final int WINDOW_LOW = 480;
    static final int WINDOW_HIGH = 519;

    public static void main(String[] args) throws IOException {
        int processId = Integer.parseInt(args[0]);
        int value = Integer.parseInt(args[1]);

        DatagramSocket socket = new DatagramSocket();
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLocalHost();
        String ip = addressIP.getHostAddress();

        String address = HelperClass.makeAddress(ip, port);
        HelperClass.makeAddressFile(processId, address);

        Path fp = Paths.get("peer" + processId);
        Files.createFile(fp);

        HelperClass.waitCoordinator();

        String hostIP = HelperClass.readRemoteIP(0);
        InetAddress remoteIP = InetAddress.getByName(hostIP);
        int remotePort = HelperClass.readRemotePort(0);

        boolean inWindow = (value >= WINDOW_LOW) && (value <= WINDOW_HIGH);

        // The send is duplicated in each branch (instead of building the
        // message once and sending it after the if/else) so ValiPar's
        // static analysis sees two distinct send statements - otherwise
        // both outcomes collapse onto the same sync edge and voting QUORUM
        // vs NORMAL is invisible to the coverage/distance metric entirely.
        if (inWindow) {
            byte[] sendBuffer = "QUORUM".getBytes();
            DatagramPacket votePacket = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
            socket.send(votePacket);
        } else {
            byte[] sendBuffer = "NORMAL".getBytes();
            DatagramPacket votePacket = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
            socket.send(votePacket);
        }

        byte[] receiveBuffer = new byte[255];
        DatagramPacket resultPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(resultPacket);
        String result = new String(resultPacket.getData()).trim();

        if (result.equals("CELEBRATE")) {
            System.out.println("Peer " + processId + " celebrates the quorum!");
        } else {
            System.out.println("Peer " + processId + " continues normally.");
        }

        socket.close();
        new File("peer" + processId).delete();
    }
}
