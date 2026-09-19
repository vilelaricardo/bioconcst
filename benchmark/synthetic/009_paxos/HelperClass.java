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

    public static void makeAddressFile(int processId, String address) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter("addr_" + processId + ".txt"))) {
            writer.print(address);
        }
    }

    public static void markReady(int processId) throws IOException {
        Files.createFile(Paths.get("ready_" + processId));
    }

    public static void waitForReady(int... processIds) {
        for (int id : processIds) {
            while (!Files.exists(Paths.get("ready_" + id)));
        }
    }

    public static String readRemoteIP(int processId) throws IOException {
        return readAddressParts(processId)[0];
    }

    public static int readRemotePort(int processId) throws IOException {
        return Integer.parseInt(readAddressParts(processId)[1]);
    }

    private static String[] readAddressParts(int processId) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader("addr_" + processId + ".txt"))) {
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
            new File("addr_" + id + ".txt").delete();
            new File("ready_" + id).delete();
        }
    }
}
