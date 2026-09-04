/**
 * Synthetic Benchmark - Combined Handshake (Peer)
 *
 * Combines both paradigms in one chain: this Peer delegates its own secret-
 * window check to an internal WindowChecker thread (shared-memory handoff
 * via semaphore), then votes QUORUM/NORMAL to the Coordinator over a socket
 * (message-passing handoff) based on what the checker found. The Coordinator
 * only runs the "celebration" round when both peers voted QUORUM in the same
 * test case - same compound-target difficulty as quorum-handshake and
 * threshold-handshake, but the path to it now crosses both mechanisms.
 *
 * java Peer <processId> <value>
 */
import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Semaphore;

// Run as two instances of this same class (peer 1 and peer 2) - part of why
// this benchmark's real coverage ceiling is 66.67%, not 100%; see
// Coordinator.java for the measured numbers and full explanation.
public class Peer {
    public static void main(String[] args) throws IOException, InterruptedException {
        int processId = Integer.parseInt(args[0]);
        int value = Integer.parseInt(args[1]);

        // Shared-memory tier: delegate the window check to an internal thread
        // instead of checking it directly here.
        PeerState state = new PeerState();
        Semaphore doneGate = new Semaphore(0);
        WindowChecker checker = new WindowChecker();
        checker.setValue(value);
        checker.setPeerState(state);
        checker.setDoneGate(doneGate);
        checker.start();
        doneGate.acquireUninterruptibly();

        // Message-passing tier: same socket handshake as quorum-handshake's Peer.
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

        // Duplicated per branch - same lesson as the other two synthetic
        // benchmarks: a single shared call site can't distinguish outcomes.
        if (state.inWindow) {
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
        checker.join();
    }
}
