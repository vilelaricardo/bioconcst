import java.net.*;
import java.io.*;
import java.nio.file.*;

public class TokenRingSlave {
    public static void main(String args[]) throws Exception {
        int amountOfProcess = 0;
        int processId = 0;
        int operation = 0; // 0 or 1 => 0 increment, 1 multiplies
        int direction = 0; // 0 or 1 => 0 clockwise, 1 counterclockwise
        int bothDirections = 2;
        FileReader file = null;
        int token;
        String data;


            processId = Integer.parseInt(args[0]);
            operation = Integer.parseInt(args[1]);
            direction = Integer.parseInt(args[2]);
            amountOfProcess = Integer.parseInt(args[3]);

if((operation==0 || operation==1)){
	if((direction==0 || direction==1)){

       // create socket
        DatagramSocket socket = new DatagramSocket();
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLocalHost();
        String ip = addressIP.getHostAddress();

        // multicast socket
        InetAddress group = InetAddress.getByName("228.5.6.7");
        MulticastSocket multicastSocket = new MulticastSocket(6789);
        multicastSocket.joinGroup(group);

        // make address file with IP:PORT
        String address = HelperClass.makeAddress(ip, port);
        HelperClass.makeAddressFile(processId, address);

        Path fp = Paths.get("slave" + processId);
        Files.createFile(fp);

        // identification of the clockwise and counterclockwise neighbors
        int clockwiseAddress = HelperClass.getClockwiseNeighbor(processId, amountOfProcess);
        int counterclockwiseAddress = HelperClass.getCounterclockwiseNeighbor(processId, amountOfProcess);

        // wait neighbors
        HelperClass.waitNeighbors(clockwiseAddress, counterclockwiseAddress);

        if (direction == 0) {
            file = new FileReader("token_ring_" + clockwiseAddress + ".txt"); 
        } else{
            file = new FileReader("token_ring_" + counterclockwiseAddress + ".txt");
        }    

        // get remote IP and PORT of the neighbor
        String hostIP = HelperClass.readNeighborIP(file);
        InetAddress remoteIP = InetAddress.getByName(hostIP);
        int remotePort = HelperClass.readNeighborPort(file, direction, clockwiseAddress, counterclockwiseAddress);

        byte[] receiveBuffer;
        byte[] sendBuffer;

        while (bothDirections != 0) {
            receiveBuffer = new byte[255];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);            
            data = new String(receivePacket.getData());
            token = (Integer.parseInt(data.trim()));

            if (operation == 0) {
                token++;
            }
            if (operation == 1) {
                token = token * 2;
            }

            String dataString = Integer.toString(token);
            sendBuffer = new byte[255];
            sendBuffer = dataString.getBytes();
            DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);

            if (direction == 0 && bothDirections == 2) {
                socket.send(datagram);
            }
            if (direction == 1 && bothDirections == 2) {
                socket.send(datagram);
            }
            if (direction == 0 && bothDirections == 1) {
                socket.send(datagram);
            }
            if (direction == 1 && bothDirections == 1) {
                socket.send(datagram);
            }

            bothDirections--;
        }

        byte[] buf = new byte[255];
        DatagramPacket recv = new DatagramPacket(buf, buf.length);
        multicastSocket.receive(recv);
        System.out.println("Multicast result = " + new String(recv.getData()));

        Files.deleteIfExists(fp);
    }}}
}
