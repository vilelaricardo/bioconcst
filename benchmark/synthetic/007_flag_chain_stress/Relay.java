/**
 * Synthetic diagnostic fixture - FlagChainStress (Relay)
 *
 * Source now always sends (TRIGGER or SKIP), so this receive always
 * completes for real - no timeout needed here, and
 * observedSenderByReceiveEdge always has a concrete entry for
 * Relay#main:0, letting causalDistance's cross-process step actually
 * find a sender to redirect through (an earlier revision had Source send
 * nothing at all when out of window, which left this receive timing out
 * with no event recorded - Codex caught that the cross-process hop was
 * dead in that version; see sync-claude-codex.md, 2026-09-15 entries).
 *
 * Forwards to Sink only if it actually got "TRIGGER" - a plain
 * String#equals, exactly the idiom that discards all numeric information
 * before it crosses this second process boundary (same pattern as the
 * two-phase-commit Coordinator's vote1.equals("YES"), and this project's
 * own quorum-handshake Coordinator comparing "QUORUM" strings). No local
 * numeric predicate exists in THIS process to redirect to - the only
 * place the real gradient still exists is back in Source's own
 * comparison, one process hop away.
 *
 * Only ONE send call site exists (not one per outcome): forwarding never
 * happens at all when the wrong thing was received, so there is exactly
 * one required element on the Relay->Sink side (Sink's own receive uses
 * a timeout - see Sink.java - since Relay may legitimately never send).
 *
 * java Relay
 */

import java.net.*;
import java.io.*;

public class Relay {
    public static void main(String[] args) throws IOException {
        int processId = 1;

        DatagramSocket socket = new DatagramSocket();
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLoopbackAddress();
        String address = HelperClass.makeAddress(addressIP.getHostAddress(), port);
        HelperClass.makeAddressFile(processId, address);

        HelperClass.waitFor(2);
        String sinkIp = HelperClass.readRemoteIP(2);
        InetAddress sinkAddr = InetAddress.getByName(sinkIp);
        int sinkPort = HelperClass.readRemotePort(2);

        byte[] receiveBuffer = new byte[255];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(receivePacket);
        String received = new String(receivePacket.getData()).trim();

        if (received.equals("TRIGGER")) {
            byte[] sendBuffer = "FORWARD".getBytes();
            DatagramPacket packet = new DatagramPacket(sendBuffer, sendBuffer.length, sinkAddr, sinkPort);
            socket.send(packet);
        }

        socket.close();
    }
}
