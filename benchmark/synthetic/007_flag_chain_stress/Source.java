/**
 * Synthetic diagnostic fixture - FlagChainStress (Source)
 *
 * NOT a benchmark meant to model any real protocol - built purely to
 * isolate causalDistance from confounds found in quorum-handshake: an
 * aggregate distance diluted by summing over ~12 required elements most
 * of which are trivially covered, and a redirected distance that varies
 * only within a narrow band the search's selector cannot meaningfully
 * discriminate. Always sends (one call site per branch, like
 * quorum-handshake's Peer) - Codex caught that an earlier revision where
 * Source sent nothing at all when out of window left Relay's receive
 * never actually completing, so causalDistance's cross-process step
 * (observedSenderByReceiveEdge) had nothing to redirect through; that
 * revision only ever exercised the local-flag redirect on its own, not
 * the two-hop composition (cross-process + local) this fixture exists to
 * stress-test - see sync-claude-codex.md, 2026-09-15 entries.
 *
 * java Source <value>
 */

import java.net.*;
import java.io.*;

public class Source {
    static final int WINDOW_LOW = 500;
    static final int WINDOW_HIGH = 501;

    public static void main(String[] args) throws IOException {
        int processId = 0;
        int value = Integer.parseInt(args[0]);

        DatagramSocket socket = new DatagramSocket();
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLoopbackAddress();
        String address = HelperClass.makeAddress(addressIP.getHostAddress(), port);
        HelperClass.makeAddressFile(processId, address);

        HelperClass.waitFor(1);
        String relayIp = HelperClass.readRemoteIP(1);
        InetAddress relayAddr = InetAddress.getByName(relayIp);
        int relayPort = HelperClass.readRemotePort(1);

        boolean inWindow = (value >= WINDOW_LOW) && (value <= WINDOW_HIGH);

        if (inWindow) {
            byte[] sendBuffer = "TRIGGER".getBytes();
            DatagramPacket packet = new DatagramPacket(sendBuffer, sendBuffer.length, relayAddr, relayPort);
            socket.send(packet);
        } else {
            byte[] sendBuffer = "SKIP".getBytes();
            DatagramPacket packet = new DatagramPacket(sendBuffer, sendBuffer.length, relayAddr, relayPort);
            socket.send(packet);
        }

        socket.close();
    }
}
