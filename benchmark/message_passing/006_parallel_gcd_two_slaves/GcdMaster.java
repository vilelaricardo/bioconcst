/**
 * 
 * Concurrent Benchmarks
 * 
 * Title:  Parallel GCD Two Slaves     
 * 
 * Description:  Parallel GCD is a program that calculates the
 *               Greatest Common Divisor (GCD) between three numbers
 *               using two slaves processes.          
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

/* TEST 1
 * java GcdSlave 1 
 * java GcdSlave 2 
 * java GcdMaster 2 4 8
 * 
 * OUTPUT:
 * Result = 2
 */

/* TEST 2
 * java GcdSlave 1 
 * java GcdSlave 2 
 * java GcdMaster 1 3 6
 * 
 * OUTPUT:
 * Result = 1
 */

import java.net.*;
import java.io.*;
import java.nio.file.*;

public class GcdMaster {
    public static void main(String[] args) throws IOException {
        final int ZERO = 0;
        final int processId = 0;
        final int amountOfProcess = 3;
        int iter = 2; // total number of iterations
        int result = -1; // stores the final GCD result
        int sent = 0;
        String values = "";
   
        int firstValue = 0; // X
        int secondValue = 0; // Y
        int thirdValue = 0; // Z
     

 
            firstValue = Integer.parseInt(args[0]);  // positivo diferente de zero
            secondValue = Integer.parseInt(args[1]); // positivo diferente de zero
            thirdValue = Integer.parseInt(args[2]);  // positivo diferente de zero
 
 
       // create socket
        DatagramSocket socket = new DatagramSocket();
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLocalHost();
        String ip = addressIP.getHostAddress();

        // make address file with IP:PORT
        String address = HelperClass.makeAddress(ip, port);
        HelperClass.makeAddressFile(processId, address);

        // wait slaves
        HelperClass.waitSlaves();

        Path fp = Paths.get("master");
        Files.createFile(fp);

        // get remote IP and PORT of the slave 1
        String hostIP = HelperClass.readRemoteIP(1);
        InetAddress remoteIPSlave1 = InetAddress.getByName(hostIP);
        int remotePortSlave1 = HelperClass.readRemotePort(1);

        // get remote IP and PORT of the slave 2
        hostIP = HelperClass.readRemoteIP(2);
        InetAddress remoteIPSlave2 = InetAddress.getByName(hostIP);
        int remotePortSlave2 = HelperClass.readRemotePort(2);

        byte[] receiveBuffer;
 if((firstValue>0) && (secondValue>0) && (thirdValue>0)){ 
        while (result == -1) {
            while (sent < iter) {
                if (sent == 0) {
                    values = HelperClass.makeMessage(firstValue, secondValue);
                    sendValueToSlave(socket, values, remoteIPSlave1, remotePortSlave1);
                } else {
                    values = HelperClass.makeMessage(secondValue, thirdValue);
                    sendValueToSlave(socket, values, remoteIPSlave2, remotePortSlave2);
                }
                sent++;
            }

            while (sent > 0) {
                receiveBuffer = new byte[255];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                socket.receive(receivePacket);
                String data = new String(receivePacket.getData());

                if (sent == 2) {
                    firstValue = (Integer.parseInt(data.trim()));
                } else {
                    secondValue = (Integer.parseInt(data.trim()));
                }
                sent--;
            }

            if (iter == 2 && (firstValue == 1 || secondValue == 1)) {
                result = 1;
            } else if (iter == 1) {
                result = secondValue;
            }
            iter--;
        }
        // these messages will finish the slaves processes
        values = HelperClass.makeMessage(ZERO, ZERO);
        sendValueToSlave(socket, values, remoteIPSlave1, remotePortSlave1);
        sendValueToSlave(socket, values, remoteIPSlave2, remotePortSlave2);

        HelperClass.closeFiles(amountOfProcess);
        Files.deleteIfExists(fp);
        System.out.println("Result = " + result);
    }
 
 else if ((firstValue<=0) && (secondValue<=0) && (thirdValue<=0))
 {

	 values = HelperClass.makeMessage(ZERO, ZERO);
     sendValueToSlave(socket, values, remoteIPSlave1, remotePortSlave1);
     sendValueToSlave(socket, values, remoteIPSlave2, remotePortSlave2);
     HelperClass.closeFiles(amountOfProcess);
     Files.deleteIfExists(fp);
     System.out.println("Wrong Enter");
     
 }
    	
    
    }// end of Main

    // send method
    private static void sendValueToSlave(DatagramSocket socket, String values, InetAddress remoteIP, int remotePort)
            throws IOException {
        byte[] sendBuffer = new byte[255];
        sendBuffer = values.toString().getBytes();
        DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
        socket.send(datagram);
    }
}
