/**
 * Concurrent Benchmarks
 * 
 * Title:  Token Ring Both Directions - Different Primitives       
 * 
 * Description:  This program simulates the token ring topology where a 
 *               token is passed by message from one process to another 
 *               in both directions using different primitives send. 
 *               Finally, the result is sent to the slaves by multicast.
 *               This token is a integer value that can be incremented 
 *               or multiplied by each process. 
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
 * java TokenRingSlave 1 0 1 4
 * java TokenRingSlave 2 0 1 4
 * java TokenRingSlave 3 0 1 4
 * java TokenRingMaster 0 0 1 4
 * 
 * OUTPUT:
 * Result = 10
 */


import java.net.*;
import java.io.*;

public class TokenRingMaster {
    public static void main(String args[]) throws Exception {
        int amountOfProcess = 0;
        int processId = 0;
        int operation = 0; // 0 or 1 => 0 increment, 1 multiplies
        int direction = 0; // 0 or 1 => 0 clockwise, 1 counterclockwise
        int bothDirections = 2;
        int token = 2;
        FileReader file = null;
        String data = "";


            processId = Integer.parseInt(args[0]); //FIXO 0 MESTRE
            operation = Integer.parseInt(args[1]); // 0 ou 1
            direction = Integer.parseInt(args[2]); // 0 ou 1
            amountOfProcess = Integer.parseInt(args[3]); // 3+

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

        byte[] sendBuffer;
        byte[] receiveBuffer;

        while (bothDirections != 0) {
            sendBuffer = new byte[255];
            String dataString = Integer.toString(token);
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

            bothDirections--;
        }

        String msg = Integer.toString(token);
        DatagramPacket multicastMsg = new DatagramPacket(msg.getBytes(), msg.length(), group, 6789);
        multicastSocket.send(multicastMsg);

        HelperClass.closeFiles(amountOfProcess, file);
        System.out.println("Result = " + token);
   
    }}}
}
