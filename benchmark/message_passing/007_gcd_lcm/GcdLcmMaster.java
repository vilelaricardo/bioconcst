/*Concurrent Benchmarks
 * 
 * Title:  007_gcd_lcm
 * 
 * Description:  Parallel GCD/LCM is a program that calculates the
 *               Greatest Common Divisor (GCD) or the Least Common Multiple (LCM)
 *               between three numbers using three slaves processes.
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
 * java GcdLcmSlave 1
 * java GcdLcmSlave 2
 * java GcdLcmSlave 3
 * java GcdLcmMaster 0 9 18 6
 * 
 * OUTPUT:
 * gcd(9, 18, 6) = 3
 */

/* TEST 2
 * java GcdLcmSlave 1
 * java GcdLcmSlave 2
 * java GcdLcmSlave 3
 * java GcdLcmMaster 1 9 18 6
 * 
 * OUTPUT:
 * lcm(9, 18, 6) = 18
 */

import java.net.*;
import java.io.*;

public class GcdLcmMaster {
 public static void main(String[] args) throws IOException { 

        int lcm;
        int method = 0; // 0 to GCD, 1 to LCM
        final int ZERO = 0;
        final int processId = 0;
        int firstValue = 0;
        int secondValue = 0;
        int thirdValue = 0;
        int receivedValueOfProcess1 = 0;
        int receivedValueOfProcess2 = 0;
        int receivedValueOfProcess3 = 0;
        String data1;
        String data2;
            method = Integer.parseInt(args[0]);  // 0 ou 1 
            firstValue = Integer.parseInt(args[1]); // positivo diferente de zero 
            secondValue = Integer.parseInt(args[2]); // positivo diferente de zero
            thirdValue = Integer.parseInt(args[3]); // positivo diferente de zero

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
 
        // get remote IP and PORT of the slave 1
        String hostIP = HelperClass.readRemoteIP(1);
        InetAddress remoteIP1 = InetAddress.getByName(hostIP);
        int remotePort1 = HelperClass.readRemotePort(1);

        // get remote IP and PORT of the slave 2
        hostIP = HelperClass.readRemoteIP(2);
        InetAddress remoteIP2 = InetAddress.getByName(hostIP);
        int remotePort2 = HelperClass.readRemotePort(2);
        
        //if((firstValue>0) && (secondValue>0) && (thirdValue>0)&&(method>-1)&&(method<2)){ 
	        
	if ((firstValue>0) && (secondValue>0) && (thirdValue>0)){

	if((method>-1)&&(method<2)){	
        // send values to the slave 1
        String values = HelperClass.makeMessage(method, firstValue, secondValue);
        sendValueToSlave(socket, values, remoteIP1, remotePort1);

        // send values to the slave 2
        values = HelperClass.makeMessage(method, secondValue, thirdValue);
        sendValueToSlave(socket, values, remoteIP2, remotePort2);

        byte[] receiveBuffer = new byte[255];
        byte[] receiveBuffer2 = new byte[255];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        DatagramPacket receivePacket2 = new DatagramPacket(receiveBuffer2, receiveBuffer2.length);

        for (int i = 1; i <= 2; i++) {
            if (i == 1) {
                socket.receive(receivePacket);
                data1 = new String(receivePacket.getData());
                receivedValueOfProcess1 = (Integer.parseInt(data1.trim()));
            } else {
                socket.receive(receivePacket2);
                data2 = new String(receivePacket2.getData());
                receivedValueOfProcess2 = (Integer.parseInt(data2.trim()));
            }
        }

        // get remote IP and PORT of the slave 3
        hostIP = HelperClass.readRemoteIP(3);
        InetAddress remoteIP3 = InetAddress.getByName(hostIP);
        int remotePort3 = HelperClass.readRemotePort(3);

        // set values with zero to finish the slave 3
        values = HelperClass.makeMessage(method, ZERO, ZERO);

        if (method == 0) { // GCD
            if (receivedValueOfProcess1 == 1 || receivedValueOfProcess2 == 1) {
                System.out.println("gcd(" + firstValue + ", " + secondValue + ", " + thirdValue + ") = " + 1);
                // send values to the slave 3
                sendValueToSlave(socket, values, remoteIP3, remotePort3);
            } else {
                values = HelperClass.makeMessage(method, receivedValueOfProcess1, receivedValueOfProcess2);
                // send values to the slave 3
                sendValueToSlave(socket, values, remoteIP3, remotePort3);
                socket.receive(receivePacket);
                data1 = new String(receivePacket.getData());
                receivedValueOfProcess3 = (Integer.parseInt(data1.trim()));
                socket.close();
                System.out.println("gcd(" + firstValue + ", " + secondValue + ", " + thirdValue + ") = " + receivedValueOfProcess3);
            }
        } else if (method == 1) { // LCM
            if (receivedValueOfProcess1 == receivedValueOfProcess2 || receivedValueOfProcess2 == 1) {
                lcm = receivedValueOfProcess1;
                System.out.println("lcm(" + firstValue + ", " + secondValue + ", " + thirdValue + ") = " + lcm);
                // send values to the slave 3
                sendValueToSlave(socket, values, remoteIP3, remotePort3);
            } else if (receivedValueOfProcess1 == 1) {
                lcm = receivedValueOfProcess2;
                System.out.println("lcm(" + firstValue + ", " + secondValue + ", " + thirdValue + ") = " + lcm);
                // send values to the slave 3
                sendValueToSlave(socket, values, remoteIP3, remotePort3);
            } else {
                values = HelperClass.makeMessage(method, receivedValueOfProcess1, receivedValueOfProcess2);
                // send values to the slave 3
                sendValueToSlave(socket, values, remoteIP3, remotePort3);
                socket.receive(receivePacket);
                data1 = new String(receivePacket.getData());
                receivedValueOfProcess3 = (Integer.parseInt(data1.trim()));
                socket.close();
                System.out.println("lcm(" + firstValue + ", " + secondValue + ", " + thirdValue + ") = " + receivedValueOfProcess3);
            }
        }

        HelperClass.closeFiles();
    }else{
//if(!((firstValue>=0) && (secondValue>=0) && (thirdValue>=0))){
//	if(!((method>-1)&&(method<2))){
	 firstValue = -1;
	 secondValue= -1;
	 method = -1;
	 
     // get remote IP and PORT of the slave 3
     hostIP = HelperClass.readRemoteIP(3);
     InetAddress remoteIP3 = InetAddress.getByName(hostIP);
     int remotePort3 = HelperClass.readRemotePort(3);
     
     
     String values = HelperClass.makeMessage(method, firstValue, thirdValue);
     sendValueToSlave(socket, values, remoteIP1, remotePort1);
     sendValueToSlave(socket, values, remoteIP2, remotePort2);
     sendValueToSlave(socket, values, remoteIP3, remotePort3);	
 	HelperClass.closeFiles();
	System.out.println("Wrong Enter"); 
 }

}
 
else{
//if(!((firstValue>=0) && (secondValue>=0) && (thirdValue>=0))){
//	if(!((method>-1)&&(method<2))){
	 firstValue = -1;
	 secondValue= -1;
	 method = -1;
	 
     // get remote IP and PORT of the slave 3
     hostIP = HelperClass.readRemoteIP(3);
     InetAddress remoteIP3 = InetAddress.getByName(hostIP);
     int remotePort3 = HelperClass.readRemotePort(3);
     
     
     String values = HelperClass.makeMessage(method, firstValue, thirdValue);
     sendValueToSlave(socket, values, remoteIP1, remotePort1);
     sendValueToSlave(socket, values, remoteIP2, remotePort2);
     sendValueToSlave(socket, values, remoteIP3, remotePort3);	
 	HelperClass.closeFiles();
	System.out.println("Wrong Enter"); 
 } }

    // send method
    private static void sendValueToSlave(DatagramSocket socket, String values, InetAddress remoteIP, int remotePort) throws IOException {
        byte[] sendBuffer = new byte[255];
        sendBuffer = values.toString().getBytes();
        DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
        socket.send(datagram);
    }
}
