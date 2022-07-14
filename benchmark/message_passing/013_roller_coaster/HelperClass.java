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

    public static void makeAddressMainFile(int socketId, String address) throws IOException {
        FileWriter ownFile = new FileWriter("main_socket_" + socketId + ".txt");
        PrintWriter writeFile = new PrintWriter(ownFile);
        writeFile.printf(address);
        ownFile.close();
    }
    
    public static String readCarIP(int socketId) throws IOException {
        FileReader file = new FileReader("car_socket_" + socketId + ".txt");
        BufferedReader br = new BufferedReader(file);
        String fileContent;
        while ((fileContent = br.readLine()) == null) {}
        String[] remoteAddress = new String(fileContent).split(":");
        String hostIP = remoteAddress[0];
        file.close();
        return hostIP;
    }

    public static int readCarPort(int socketId) throws IOException {
        FileReader file = new FileReader("car_socket_" + socketId + ".txt");
        BufferedReader br = new BufferedReader(file);
        String fileContent;
        while ((fileContent = br.readLine()) == null) {}
        String[] remoteAddress = new String(fileContent).split(":");
        int remotePort = Integer.parseInt(remoteAddress[1]);
        file.close();
        return remotePort;
    }
    
    public static String readMainIP(int socketId) throws IOException {
        FileReader file = new FileReader("main_socket_" + socketId + ".txt");
        BufferedReader br = new BufferedReader(file);
        String fileContent;
        while ((fileContent = br.readLine()) == null) {}
        String[] remoteAddress = new String(fileContent).split(":");
        String hostIP = remoteAddress[0];
        file.close();
        return hostIP;
    }

    public static int readMainPort(int socketId) throws IOException {
        FileReader file = new FileReader("main_socket_" + socketId + ".txt");
        BufferedReader br = new BufferedReader(file);
        String fileContent;
        while ((fileContent = br.readLine()) == null) {}
        String[] remoteAddress = new String(fileContent).split(":");
        int remotePort = Integer.parseInt(remoteAddress[1]);
        file.close();
        return remotePort;
    }

    public static void makeAddressPassengerFile(int processId, int socketId, String address) throws IOException {
        FileWriter ownFile = new FileWriter("passenger_"  + processId +       "_socket_"      + socketId + ".txt");
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
    
    public static void makeAddressCarFile(int socketId, String address) throws IOException {
        FileWriter ownFile = new FileWriter("car_socket_" + socketId + ".txt");
        PrintWriter writeFile = new PrintWriter(ownFile);
        writeFile.printf(address);
        ownFile.close();
    }

    public static String readPassengerIP(int passengerId, int socketId) throws IOException {
        FileReader file = new FileReader("passenger_" + passengerId + "_socket_" + socketId + ".txt");
        BufferedReader br = new BufferedReader(file);
        String fileContent;
        while ((fileContent = br.readLine()) == null) {}
        String[] remoteAddress = new String(fileContent).split(":");
        String hostIP = remoteAddress[0];
        file.close();
        return hostIP;
    }

    public static int readPassengerPort(int passengerId, int socketId) throws IOException {
        FileReader file = new FileReader("passenger_" + passengerId + "_socket_" + socketId + ".txt");
        BufferedReader br = new BufferedReader(file);
        String fileContent;
        while ((fileContent = br.readLine()) == null) {}
        String[] remoteAddress = new String(fileContent).split(":");
        int remotePort = Integer.parseInt(remoteAddress[1]);
        file.close();
        return remotePort;
    }
    
    public static void waitFile(int k) {
        for (int i = 1; i < k; i++) {
            while (!Files.exists(Paths.get("wait" + i)));
        }
    }
    
    public static void closeFile(int k) {
        for (int i = 0; i < k; i++) {
            new File("wait" + i).delete();
        }
    }
}
