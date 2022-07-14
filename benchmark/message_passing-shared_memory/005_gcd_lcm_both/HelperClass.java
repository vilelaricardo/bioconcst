import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class HelperClass {
    public static String makeAddress(String ip, int port) {
        StringBuilder builder = new StringBuilder();
        builder.append(ip).append(":").append(port);
        String address = builder.toString();
        return address;
    }

    public static void makeAddressFile(int processId, String address) throws IOException {
        FileWriter ownFile = new FileWriter("parallel_gcd_lcm" + processId + ".txt");
        PrintWriter writeFile = new PrintWriter(ownFile);
        writeFile.printf(address);
        ownFile.close();
    }

    public static void waitMaster() {
        while (!Files.exists(Paths.get("parallel_gcd_lcm0.txt")));
    }

    public static void waitSlaves1() {
        while (!Files.exists(Paths.get("parallel_gcd_lcm1.txt")));
        while (!Files.exists(Paths.get("parallel_gcd_lcm2.txt")));
        while (!Files.exists(Paths.get("parallel_gcd_lcm3.txt")));
    }
    
    public static void waitSlaves2() {
        while (!Files.exists(Paths.get("parallel_gcd_lcm1.txt")));
        while (!Files.exists(Paths.get("parallel_gcd_lcm2.txt")));
    }

    public static String readRemoteIP(int slaveId) throws IOException {
        FileReader file = new FileReader("parallel_gcd_lcm" + slaveId + ".txt");
        BufferedReader br = new BufferedReader(file);
        String fileContent;
        while ((fileContent = br.readLine()) == null) {}
        String[] remoteAddress = new String(fileContent).split(":");
        String hostIP = remoteAddress[0];
        file.close();
        return hostIP;
    }

    public static int readRemotePort(int slaveId) throws IOException {
        FileReader file = new FileReader("parallel_gcd_lcm" + slaveId + ".txt");
        BufferedReader br = new BufferedReader(file);
        String fileContent;
        while ((fileContent = br.readLine()) == null) {}
        String[] remoteAddress = new String(fileContent).split(":");
        int remotePort = Integer.parseInt(remoteAddress[1]);
        file.close();
        return remotePort;
    }

    public static String makeMessage1(int method, int value1, int value2) {
        StringBuilder builder = new StringBuilder();
        builder.append(method).append(",").append(value1).append(",").append(value2);
        String address = builder.toString();
        return address;
    }
    
    public static String makeMessage2(int method, int value1, int value2, int value3) {
        StringBuilder builder = new StringBuilder();
        builder.append(method).append(",").append(value1).append(",").append(value2).append(",").append(value3);
        String address = builder.toString();
        return address;
    }

    public static void closeFiles1() {
        new File("parallel_gcd_lcm0.txt").delete();
        for (int i = 1; i <= 3; i++) {
            new File("parallel_gcd_lcm" + i + ".txt").delete();
            new File("slave" + i).delete();
        }
    }
    
    public static void closeFiles2() {
        new File("parallel_gcd_lcm0.txt").delete();
        for (int i = 1; i <= 2; i++) {
            new File("parallel_gcd_lcm" + i + ".txt").delete();
            new File("slave" + i).delete();
        }
    }
}