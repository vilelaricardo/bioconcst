import java.io.*;
import java.nio.file.*;

/**
 * Generic address rendezvous by process id - "flagchain<id>.txt" holds
 * "ip:port" for whichever process wrote it. Deliberately not tailored to
 * any one role (unlike quorum-handshake's Coordinator/Peer split) since
 * this benchmark is a pure diagnostic fixture, not a benchmark meant to
 * model a real protocol - see FlagChainStress' own note.
 */
public class HelperClass {
    public static String makeAddress(String ip, int port) {
        return ip + ":" + port;
    }

    public static void makeAddressFile(int processId, String address) throws IOException {
        FileWriter ownFile = new FileWriter("flagchain" + processId + ".txt");
        PrintWriter writeFile = new PrintWriter(ownFile);
        writeFile.printf(address);
        ownFile.close();
    }

    public static void waitFor(int processId) {
        Path p = Paths.get("flagchain" + processId + ".txt");
        while (!Files.exists(p)) {
            // Codex's suggestion: a bare busy-wait spins a full CPU core
            // for however long the other process takes to start - exactly
            // the kind of self-inflicted contention noise this session
            // already chased down once (see sync-claude-codex.md,
            // 2026-09-14 14:45 entry, the threadExecutors/load-average
            // finding).
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static String readRemoteIP(int processId) throws IOException {
        FileReader file = new FileReader("flagchain" + processId + ".txt");
        BufferedReader br = new BufferedReader(file);
        String fileContent;
        while ((fileContent = br.readLine()) == null);
        file.close();
        return fileContent.split(":")[0];
    }

    public static int readRemotePort(int processId) throws IOException {
        FileReader file = new FileReader("flagchain" + processId + ".txt");
        BufferedReader br = new BufferedReader(file);
        String fileContent;
        while ((fileContent = br.readLine()) == null);
        file.close();
        return Integer.parseInt(fileContent.split(":")[1]);
    }

    public static void closeFiles() {
        for (int i = 0; i <= 2; i++) {
            new File("flagchain" + i + ".txt").delete();
        }
    }
}
