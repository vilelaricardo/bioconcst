import java.io.*;
import java.nio.file.*;

public class HelperClass {
    public static String makeAddress(String ip, int port) {
        StringBuilder builder = new StringBuilder();
        builder.append(ip).append(":").append(port);
        return builder.toString();
    }

    public static void makeAddressFile(int processId, String address) throws IOException {
        FileWriter ownFile = new FileWriter("quorum" + processId + ".txt");
        PrintWriter writeFile = new PrintWriter(ownFile);
        writeFile.printf(address);
        ownFile.close();
    }

    public static void waitPeers() {
        while (!Files.exists(Paths.get("peer1")));
        while (!Files.exists(Paths.get("peer2")));
    }

    public static void waitCoordinator() {
        while (!Files.exists(Paths.get("coordinator")));
    }

    public static String readRemoteIP(int processId) throws IOException {
        FileReader file = new FileReader("quorum" + processId + ".txt");
        BufferedReader br = new BufferedReader(file);
        String fileContent;
        while ((fileContent = br.readLine()) == null);
        String[] remoteAddress = fileContent.split(":");
        file.close();
        return remoteAddress[0];
    }

    public static int readRemotePort(int processId) throws IOException {
        FileReader file = new FileReader("quorum" + processId + ".txt");
        BufferedReader br = new BufferedReader(file);
        String fileContent;
        while ((fileContent = br.readLine()) == null);
        String[] remoteAddress = fileContent.split(":");
        int remotePort = Integer.parseInt(remoteAddress[1]);
        file.close();
        return remotePort;
    }

    public static void closeFiles() {
        for (int i = 0; i <= 2; i++) {
            new File("quorum" + i + ".txt").delete();
        }
        new File("coordinator").delete();
        new File("peer1").delete();
        new File("peer2").delete();
    }
}
