import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class HelperClass {
	private static BufferedReader br;
	private static BufferedReader br2;
	
	public static String makeAddress(String ip, int port) {
        StringBuilder builder = new StringBuilder();
        builder.append(ip).append(":").append(port);
        String address = builder.toString();
        return address;
    }
    
	public static void makeAddressFile(int processId, String address) throws IOException {
        FileWriter ownFile = new FileWriter("token_ring_" + processId + ".txt");
        PrintWriter writeFile = new PrintWriter(ownFile);
        writeFile.printf(address);
        ownFile.close();
    }
    
	public static int getClockwiseNeighbor(int processId, int amountOfProcess) {
        int clockwiseAddress = (processId + amountOfProcess + 1) % amountOfProcess;
        return clockwiseAddress;
    }
    
	public static int getCounterclockwiseNeighbor(int processId, int amountOfProcess) {
        int counterclockwiseAddress = (processId + amountOfProcess - 1) % amountOfProcess;
        return counterclockwiseAddress;
    }
 
	public static void waitNeighbors(int clockwiseAddress, int counterclockwiseAddress) {
        while (!Files.exists(Paths.get("token_ring_" + clockwiseAddress + ".txt")));
        while (!Files.exists(Paths.get("token_ring_" + counterclockwiseAddress + ".txt")));
    }
    
    public static String readNeighborIP(FileReader file) throws IOException {
        br = new BufferedReader(file); 
        String fileContent; 
        while((fileContent = br.readLine()) == null) {}
        String[] result = new String(fileContent).split(":");
        String hostIP  = result[0]; 
        return hostIP;        
    }
    
	public static int readNeighborPort(FileReader file, int direction, int clockwiseAddress, int counterclockwiseAddress) throws IOException {
        if (direction == 0) {
            file = new FileReader("token_ring_" + clockwiseAddress + ".txt"); 
        } else if (direction == 1) {
            file = new FileReader("token_ring_" + counterclockwiseAddress + ".txt");
        }  
        br2 = new BufferedReader(file); 
        String fileContent; 
        while((fileContent = br2.readLine()) == null) {}
        String[] result = new String(fileContent).split(":");
        int remotePort = Integer.parseInt(result[1]);
        return remotePort;
    }
    
	public static void closeFiles(int amountOfProcess, FileReader file) throws IOException {
		new File("token_ring_0.txt").delete();
        for (int i = 0; i < amountOfProcess; i++) {
            new File("token_ring_" + i + ".txt").delete();
        } 
    }
}
