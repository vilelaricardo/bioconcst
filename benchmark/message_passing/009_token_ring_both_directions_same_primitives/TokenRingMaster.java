/**
 * Concurrent Benchmarks
 * 
 * Title:  Token Ring Both Directions - Same Primitives       
 * 
 * Description:  This program simulates the token ring topology where a 
 *               token is passed by message from one process to another 
 *               in both directions using same primitives non-blocking send 
 *               and same primitives blocking receive. 
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
 * java TokenRingSlave 1 0 4
 * java TokenRingSlave 2 0 4
 * java TokenRingSlave 3 0 4
 * java TokenRingMaster 0 0 4
 * 
 * OUTPUT:
 * Result = 11
 */

/* TEST WITH 5 PROCESSES
 * java TokenRingSlave 1 1 5
 * java TokenRingSlave 2 1 5
 * java TokenRingSlave 3 1 5
 * java TokenRingSlave 4 1 5
 * java TokenRingMaster 0 1 5
 * 
 * OUTPUT:
 * Result = 128
 */

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class TokenRingMaster {
    public static void main(String args[]) throws Exception {
        int amountOfProcess = 0;
        int processId = 0;
        int operation = 0; // 0 or 1 => 0 increment, 1 multiplies
        int bothDirections = 2;
        int token = 2;
        int token1 = 0;
        int token2 = 0;
        int remotePort;
        String hostIP = "";


            processId = Integer.parseInt(args[0]); // fixo 0
            operation = Integer.parseInt(args[1]); // 0 ou 1
            amountOfProcess = Integer.parseInt(args[2]); // 3 +

if((operation==0 || operation==1)){	

        // create block socket
        DatagramSocket socket = new DatagramSocket();
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLocalHost();
        String ip = addressIP.getHostAddress();

        // create non-blocking socket
        DatagramChannel channel = DatagramChannel.open();

        // make address file with IP:PORT
        String address = HelperClass.makeAddress(ip, port);
        HelperClass.makeAddressFile(processId, address);

        // identification of the clockwise and counterclockwise neighbors
        int clockwiseAddress = HelperClass.getClockwiseNeighbor(processId, amountOfProcess);
        int counterclockwiseAddress = HelperClass.getCounterclockwiseNeighbor(processId, amountOfProcess);

        // wait neighbors
        HelperClass.waitNeighbors(clockwiseAddress, counterclockwiseAddress);

        // make message <processId, token>
        String values = HelperClass.makeMessage(processId, token);

        FileReader file = null;
        while (bothDirections != 0) {
            int destination = -1;

            if (bothDirections == 2) {
                destination = clockwiseAddress;
                file = new FileReader("token_ring_" + clockwiseAddress + ".txt");
            } else {
                destination = counterclockwiseAddress;
                file = new FileReader("token_ring_" + counterclockwiseAddress + ".txt");
            }

            // get remote IP and PORT of the neighbor
            hostIP = HelperClass.readNeighborIP(file);
            InetAddress remoteIP = InetAddress.getByName(hostIP);
            remotePort = HelperClass.readNeighborPort(file, destination);

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

        bothDirections = 2;
        byte[] receiveBuffer = new byte[255];

        while (bothDirections != 0) {
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);
            String[] result = new String(receivePacket.getData()).trim().split(",");

            if (bothDirections == 2) {
                token1 = (Integer.parseInt(result[1]));
            } else {
                token2 = (Integer.parseInt(result[1]));
            }

            bothDirections--;
        }

        int result = token1 + token2;

        if (operation == 0) {
            result++;
        }
        if (operation == 1) {
            result = result * 2;
        }

        HelperClass.closeFiles(amountOfProcess, file);
        System.out.println("Result = " + result);
    }}
}
