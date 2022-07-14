import java.net.*;
import java.io.*;

public class TokenRingSlave {
    public static void main(String args[]) throws Exception {
        int amountOfProcesses = 0;
        int processId = 1; // number 1 ... N, just no the number zero
        int operation = 0; // 0 or 1 => 0 increment, 1 multiplies
        int direction = 0; // 0 or 1 => 0 clockwise, 1 counterclockwise
        int numberOfIterations = 1;
        int token;
        FileReader file = null;
        String data;


            processId = Integer.parseInt(args[0]);
            operation = Integer.parseInt(args[1]);
            direction = Integer.parseInt(args[2]);
            numberOfIterations = Integer.parseInt(args[3]);
            amountOfProcesses = Integer.parseInt(args[4]);

if((operation==1 || operation==0)){
		if ((direction==1) || (direction==0)){
			if(numberOfIterations>=1 && numberOfIterations<=10){

        // create socket
        DatagramSocket socket = new DatagramSocket();
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLocalHost();
        String ip = addressIP.getHostAddress();

        // make address file with IP:PORT
        String address = HelperClass.makeAddress(ip, port);
        HelperClass.makeAddressFile(processId, address);

        // identification of the clockwise and counterclockwise neighbors
        int clockwiseAddress = HelperClass.getClockwiseNeighbor(processId, amountOfProcesses);
        int counterclockwiseAddress = HelperClass.getCounterclockwiseNeighbor(processId, amountOfProcesses);

        // wait neighbors
        HelperClass.waitNeighbors(clockwiseAddress, counterclockwiseAddress);

        if (direction == 0) {
            file = new FileReader("token_ring_" + clockwiseAddress + ".txt");
        } else if (direction == 1) {
            file = new FileReader("token_ring_" + counterclockwiseAddress + ".txt");
        }

        // get remote IP and PORT of the neighbor
        String hostIP = HelperClass.readNeighborIP(file);
        InetAddress remoteIP = InetAddress.getByName(hostIP);
        int remotePort = HelperClass.readNeighborPort(file, direction, clockwiseAddress, counterclockwiseAddress);
        
        String dataString;
        byte[] sendBuffer = new byte[255];
        byte[] receiveBuffer = new byte[255];

        while (numberOfIterations != 0) {
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            // receives the token
            socket.receive(receivePacket);
            data = new String(receivePacket.getData());
            token = (Integer.parseInt(data.trim()));

            if (operation == 0) {
                token++;
            } else if (operation == 1) {
                token = token * 2;
            }

            dataString = Integer.toString(token);
            sendBuffer = dataString.getBytes();
            DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
            // sends the token
            socket.send(datagram);
            numberOfIterations--;
        }
        socket.close();
        // delete address file
        new File("token_ring_" + processId + ".txt").delete();
    }}}}
}
