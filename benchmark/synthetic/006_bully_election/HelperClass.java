import java.io.*;
import java.nio.file.*;

/**
 * Generalized address-exchange helper for the synthetic hub-and-spoke
 * benchmarks (any number of peers, not just 2) - each process writes its own
 * "addr_<id>.txt" and "ready_<id>" marker right after binding its socket;
 * anyone needing another process's address blocks on waitForReady(ids...)
 * first, guaranteeing the address file write already happened-before the
 * marker file became visible (same ordering discipline quorum-handshake's
 * own HelperClass relies on with its peer1/peer2/coordinator marker files).
 */
public class HelperClass {
    public static String makeAddress(String ip, int port) {
        return ip + ":" + port;
    }

    /**
     * socketId lets one process publish MORE THAN ONE socket's address (e.g.
     * bully-election's Node uses a dedicated second socket purely for the
     * final COORDINATOR announcement, so a "crashed" node's still-open main
     * socket can never have a stray ELECTION/OK packet mistakenly consumed
     * by the announcement receive - same multi-socket-per-process shape
     * roller-coaster's Car/Passenger already use, generalized to any id).
     */
    public static void makeAddressFile(int processId, int socketId, String address) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter("addr_" + processId + "_" + socketId + ".txt"))) {
            writer.print(address);
        }
    }

    public static void makeAddressFile(int processId, String address) throws IOException {
        makeAddressFile(processId, 1, address);
    }

    public static void markReady(int processId) throws IOException {
        Files.createFile(Paths.get("ready_" + processId));
    }

    public static void waitForReady(int... processIds) {
        for (int id : processIds) {
            while (!Files.exists(Paths.get("ready_" + id)));
        }
    }

    public static String readRemoteIP(int processId, int socketId) throws IOException {
        return readAddressParts(processId, socketId)[0];
    }

    public static int readRemotePort(int processId, int socketId) throws IOException {
        return Integer.parseInt(readAddressParts(processId, socketId)[1]);
    }

    public static String readRemoteIP(int processId) throws IOException {
        return readRemoteIP(processId, 1);
    }

    public static int readRemotePort(int processId) throws IOException {
        return readRemotePort(processId, 1);
    }

    private static String[] readAddressParts(int processId, int socketId) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new FileReader("addr_" + processId + "_" + socketId + ".txt"))) {
            String line;
            while ((line = br.readLine()) == null) {
                // address file exists (waitForReady already returned) but the
                // write may not be flushed yet - keep polling the same handle.
            }
            return line.split(":");
        }
    }

    public static void cleanup(int... processIds) {
        for (int id : processIds) {
            new File("addr_" + id + "_1.txt").delete();
            new File("addr_" + id + "_2.txt").delete();
            new File("ready_" + id).delete();
        }
    }
}
