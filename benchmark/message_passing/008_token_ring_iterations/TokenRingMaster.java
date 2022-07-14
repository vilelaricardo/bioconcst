/**
 * Concurrent Benchmarks
 * 
 * Title:  Token Ring Iterations     
 * 
 * Description:  This program simulates the token ring topology 
 *               where a token is passed by message from one process to another. 
 *               This token is a integer value that can be incremented or multiplied
 *               by each process and also sent in the clockwise or counterclockwise
 *               direction several times.
 *
 * Paradigm:     Message Passing
 *               
 * Year:         2014
 * Company:      ICMC/USP - São Carlos
 *               University of São Paulo (USP)
 *               Institute of Mathematics and Computer Science (ICMC)
 *               
 * @author       George Gabriel Mendes Dourado
 * @version      1.0
 */

/* TEST WITH 4 PROCESSES
 * java TokenRingSlave 1 0 0 2 4
 * java TokenRingSlave 2 0 0 2 4
 * java TokenRingSlave 3 0 0 2 4
 * java TokenRingMaster 0 0 0 2 4
 * 
 * OUTPUT:
 * Result = 10
 */

/* TEST WITH 5 PROCESSES
 * java TokenRingSlave 1 0 1 2 5
 * java TokenRingSlave 2 0 1 2 5
 * java TokenRingSlave 3 0 1 2 5
 * java TokenRingSlave 3 0 1 2 5
 * java TokenRingMaster 0 0 1 2 5
 * 
 * OUTPUT:
 * Result = 12
 */

/* TEST WITH 4 PROCESSES
 * java TokenRingSlave 1 1 1 2 4
 * java TokenRingSlave 2 1 1 2 4
 * java TokenRingSlave 3 1 1 2 4
 * java TokenRingMaster 0 1 1 2 4
 * 
 * OUTPUT:
 * Result = 512
 */

import java.net.*;
import java.io.*;

public class TokenRingMaster {
    public static void main(String args[]) throws Exception {
        int amountOfProcesses = 0;
        int processId = 0; // use the number zero
        int operation = 0; // 0 or 1 => 0 increment, 1 multiplies
        int direction = 0; // 0 or 1 => 0 clockwise, 1 counterclockwise
        int numberOfIterations = 1;
        int token = 2;
        FileReader file = null;
        String data;

       
            processId = Integer.parseInt(args[0]); // fixo 0
            operation = Integer.parseInt(args[1]); // 0 ou 1
            direction = Integer.parseInt(args[2]); // 0 ou 1
            numberOfIterations = Integer.parseInt(args[3]); //1 ou +
            amountOfProcesses = Integer.parseInt(args[4]);// 3 +
       
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
        int remotePort = HelperClass.readNeighborPort(file, direction, clockwiseAddress, counterclockwiseAddress);;

        byte[] sendBuffer = new byte[255];
        byte[] receiveBuffer = new byte[255];
        String dataString = Integer.toString(token);
        sendBuffer = dataString.getBytes();

        while (numberOfIterations != 0) {
            DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
            // sends the token
            socket.send(datagram);
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
            numberOfIterations--;
        }

        socket.close();
        System.out.println("Result = " + token);

        // delete address file
        new File("token_ring_0.txt").delete();
    }}}}
}
