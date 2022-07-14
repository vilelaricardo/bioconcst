import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.file.*;

public class TokenRingSlave {
    public static void main(String args[]) throws Exception {
        int processId = 0;
        int operation = 0; // 0 or 1 => 0 increment, 1 multiplies
        int amountOfProcess = 0;
        int bothDirections = 2;
        int token;
        FileReader file = null;


            processId = Integer.parseInt(args[0]);
            operation = Integer.parseInt(args[1]);
            amountOfProcess = Integer.parseInt(args[2]);

if((operation==0 || operation==1)){	        

        // create block socket
        DatagramSocket socket = new DatagramSocket();
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLocalHost();
        String ip = addressIP.getHostAddress();

        // create non-blocking socket
        DatagramChannel channel = DatagramChannel.open();

        // make address file
        String address = HelperClass.makeAddress(ip, port);
        HelperClass.makeAddressFile(processId, address);

        Path fp = Paths.get("slave" + processId);
        Files.createFile(fp);

        // identification of the clockwise and counterclockwise neighbors
        int clockwiseAddress = HelperClass.getClockwiseNeighbor(processId, amountOfProcess);
        int counterclockwiseAddress = HelperClass.getCounterclockwiseNeighbor(processId, amountOfProcess);

        // wait neighbors
        HelperClass.waitNeighbors(clockwiseAddress, counterclockwiseAddress);

        byte[] receiveBuffer = new byte[255];

        while (bothDirections != 0) {
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);
            String[] result = new String(receivePacket.getData()).trim().split(",");
            String origin = new String(result[0]);
            token = (Integer.parseInt(result[1]));

            if (operation == 0) {
                token++;
            }
            if (operation == 1) {
                token = token * 2;
            }

            int destination = -1;
            int compare = (Integer.parseInt(origin));

            if (compare == clockwiseAddress) {
                destination = counterclockwiseAddress;
                file = new FileReader("token_ring_" + counterclockwiseAddress + ".txt");
            } else if ((Integer.parseInt(origin)) == counterclockwiseAddress) {
                destination = clockwiseAddress;
                file = new FileReader("token_ring_" + clockwiseAddress + ".txt");
            }

            // get remote IP and PORT of the destination
            String hostIP = HelperClass.readNeighborIP(file);
            InetAddress remoteIP = InetAddress.getByName(hostIP);
            int remotePort = HelperClass.readNeighborPort(file, destination);

            // make message <processId, token>
            String values = HelperClass.makeMessage(processId, token);

            // set non-blocking address
            InetSocketAddress addr = new InetSocketAddress(remoteIP, remotePort);
            channel.configureBlocking(false);

            int sendResult = 0;
            byte[] sendMessage = values.getBytes();

            ByteBuffer buffer = HelperClass.getBuffer(sendMessage);

            do {
                sendResult = channel.send(buffer, addr);
            } while (sendResult < 0);

            bothDirections--;
        }
        Files.deleteIfExists(fp);
    }}
}
