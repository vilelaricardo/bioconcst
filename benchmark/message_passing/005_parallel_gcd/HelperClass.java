import java.io.*;
import java.nio.file.*;

public class HelperClass {
    public static String makeAddress(String ip, int port) {
        StringBuilder builder = new StringBuilder();
        builder.append(ip).append(":").append(port);
        String address = builder.toString();
        return address;
    }

    public static void makeAddressFile(int processId, String address) throws IOException {
        FileWriter ownFile = new FileWriter("parallel_gcd" + processId + ".txt");
        PrintWriter writeFile = new PrintWriter(ownFile);
        writeFile.printf(address);
        ownFile.close();
    }

    public static String makeMessage(int value1, int value2) {
        StringBuilder builder = new StringBuilder();
        builder.append(value1).append(",").append(value2);
        String address = builder.toString();
        return address;
    }
	
    public static String readRemoteIP(int processId) throws IOException {
        FileReader file = new FileReader("parallel_gcd" + processId + ".txt");
        BufferedReader br = new BufferedReader(file);
        String fileContent;
        while ((fileContent = br.readLine()) == null) {}
        String[] remoteAddress = new String(fileContent).split(":");
        String hostIP = remoteAddress[0];
        file.close();
        return hostIP;
    }

    public static int readRemotePort(int processId) throws IOException {
        FileReader file = new FileReader("parallel_gcd" + processId + ".txt");
        BufferedReader br = new BufferedReader(file);
        String fileContent;
        while ((fileContent = br.readLine()) == null) {}
        String[] remoteAddress = new String(fileContent).split(":");
        int remotePort = Integer.parseInt(remoteAddress[1]);
        file.close();
        return remotePort;
    }

    public static void waitMaster() {
        while (!Files.exists(Paths.get("parallel_gcd0.txt")));
    }

    public static void waitSlaves() {
        while (!Files.exists(Paths.get("parallel_gcd1.txt")));
        while (!Files.exists(Paths.get("parallel_gcd2.txt")));
        while (!Files.exists(Paths.get("parallel_gcd3.txt")));
    }
}