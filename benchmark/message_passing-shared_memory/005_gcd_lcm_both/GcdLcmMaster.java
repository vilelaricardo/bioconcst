/**
 * Concurrent Benchmarks
 * 
 * Title:  005_gcd_lcm_both
 * 
 * Description:  GCD_LCM_Both is a program that calculates the
 *               Greatest Common Divisor (GCD) or the Least Common Multiple (LCM)
 *               among three numbers with three slaves processes using the 
 *               message passing paradigm or can calculates GCD and LCM,
 *               in the same time, using threads.
 *               
 * Paradigm:     Message Passing and Shared Memory
 *               
 * Year:         2014
 * Company:      ICMC/USP - São Carlos
 *               University of São Paulo (USP)
 *               Institute of Mathematics and Computer Science (ICMC)
 *               
 * @author       George Gabriel Mendes Dourado
 * @version      1.0
 */

/*TEST1 GCD
 * java GcdLcmSlave 1
 * java GcdLcmSlave 2
 * java GcdLcmSlave 3
 * java GcdLcmMaster 0 9 18 6
 * 
 * Result = 3

 TEST2 GCD
 * java GcdLcmSlave 1
 * java GcdLcmSlave 2
 * java GcdLcmSlave 3
 * java GcdLcmMaster 0 8 12 7 
 * 
 * Result = 1
 */

/*TEST1 LCM
 * java GcdLcmSlave 1
 * java GcdLcmSlave 2
 * java GcdLcmSlave 3
 * java GcdLcmMaster 1 9 3 9
 * 
 * Result = 9


 TEST2 LCM
 * java GcdLcmSlave 1
 * java GcdLcmSlave 2
 * java GcdLcmSlave 3
 * java GcdLcmMaster 1 9 1 1
 * 
 * Result = 9


 TEST3 LCM
 * java GcdLcmSlave 1
 * java GcdLcmSlave 2
 * java GcdLcmSlave 3
 * java GcdLcmMaster 1 1 1 9 
 * 
 * Result = 9
 
 TEST4 LCM
 * java GcdLcmSlave 1
 * java GcdLcmSlave 2
 * java GcdLcmSlave 3
 * java GcdLcmMaster 1 18 9 7 
 * 
 * Result = 126
 */

/*TEST1 GCD-LCM
 * java GcdLcmSlave 1
 * java GcdLcmSlave 2
 * java GcdLcmMaster 2 8 1 6 
 * 
 * Result = 
 * GCD = 1
 * LCM = 24


 TEST3 GCD-LCM
 * java GcdLcmSlave 1
 * java GcdLcmSlave 2
 * java GcdLcmMaster 2 9 18 6
 * 
 * Result = 
 * GCD = 3
 * LCM = 18
 */

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class GcdLcmMaster {
    public static void main(String[] args) throws IOException {
        int lcm;
        int method = 0; // 0 to GCD (Parallel), 1 to LCM (Parallel), 2 to GCD + LCM (Parallel and Shared Memory)
        final int ZERO = 0;
        int fstValue = 50; //first value
        int sndValue = 100; //second value
        int trdValue = 10; //third value
        int recvValueOfProc1 = 0;
        int recvValueOfProc2 = 0;
        int recvValueOfProc3 = 0;
        String data1; // results
        String data2; // results
        String values;

   
            method = Integer.parseInt(args[0]);
            fstValue = Integer.parseInt(args[1]);
            sndValue = Integer.parseInt(args[2]);
            trdValue = Integer.parseInt(args[3]);
   
	if((fstValue>0) && (sndValue>0) && (trdValue>0)){ 
	if((method>=0) && (method<=2)){
	
        
        // create socket
        DatagramSocket socket = new DatagramSocket();
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLocalHost();
        String ip = addressIP.getHostAddress();

        // make address file with IP:PORT
        String address = HelperClass.makeAddress(ip, port);
        HelperClass.makeAddressFile(0, address);

        // wait slaves
        if (method == 0 || method == 1) {
            HelperClass.waitSlaves1();
        } else {
            HelperClass.waitSlaves2();
        }

        // get remote IP and PORT of the slave 1
        String hostIP = HelperClass.readRemoteIP(1);
        InetAddress remoteIP1 = InetAddress.getByName(hostIP);
        int remotePort1 = HelperClass.readRemotePort(1);

        // get remote IP and PORT of the slave 2
        hostIP = HelperClass.readRemoteIP(2);
        InetAddress remoteIP2 = InetAddress.getByName(hostIP);
        int remotePort2 = HelperClass.readRemotePort(2);

        if (method == 0 || method == 1) {
            // send values to the slave 1
            values = HelperClass.makeMessage1(method, fstValue, sndValue);
            sendValueToSlave(socket, values, remoteIP1, remotePort1);

            // send values to the slave 2
            values = HelperClass.makeMessage1(method, sndValue, trdValue);
            sendValueToSlave(socket, values, remoteIP2, remotePort2);
        } 
        if (method == 2) {
            // send values to the slave 1
            values = HelperClass.makeMessage2(method, fstValue, sndValue, trdValue);
            sendValueToSlave(socket, values, remoteIP1, remotePort1);

            // send values to the slave 2
            sendValueToSlave(socket, values, remoteIP2, remotePort2);
        }
        
        byte[] receiveBuffer = new byte[255];
        byte[] receiveBuffer2 = new byte[255];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        DatagramPacket receivePacket2 = new DatagramPacket(receiveBuffer2, receiveBuffer2.length);        
        
        for (int i = 1; i <= 2; i++) {
            if (i == 1) {
                socket.receive(receivePacket);
                data1 = new String(receivePacket.getData());
                if (method == 0 || method == 1) {
                    recvValueOfProc1 = (Integer.parseInt(data1.trim()));
                }
                if (method == 2) {
                    System.out.println(data1);
                }

            } else {
                socket.receive(receivePacket2);
                data2 = new String(receivePacket2.getData());
                if (method == 0 || method == 1) {
                    recvValueOfProc2 = (Integer.parseInt(data2.trim()));
                }
                if (method == 2) {
                    System.out.println(data2);
                }
            }
        }
        
        // set values with zero to finish the slave 3
        values = HelperClass.makeMessage1(method, ZERO, ZERO);

        if (method == 0) { // GCD
            // get remote IP and PORT of the slave 3
            hostIP = HelperClass.readRemoteIP(3);
            InetAddress remoteIP3 = InetAddress.getByName(hostIP);
            int remotePort3 = HelperClass.readRemotePort(3);
        
            if (recvValueOfProc1 == 1 || recvValueOfProc2 == 1) {
                System.out.println("gcd(" + fstValue + ", " + sndValue + ", " + trdValue + ") = " + 1);
                // send values to the slave 3
                sendValueToSlave(socket, values, remoteIP3, remotePort3);
            } else {
                values = HelperClass.makeMessage1(method, recvValueOfProc1, recvValueOfProc2);
                // send values to the slave 3
                sendValueToSlave(socket, values, remoteIP3, remotePort3);
                socket.receive(receivePacket);
                data1 = new String(receivePacket.getData());
                recvValueOfProc3 = (Integer.parseInt(data1.trim()));
                System.out.println("gcd(" + fstValue + ", " + sndValue + ", " + trdValue + ") = " + recvValueOfProc3);
            }
        }
        if (method == 1) { // LCM
             // get remote IP and PORT of the slave 3
             hostIP = HelperClass.readRemoteIP(3);
             InetAddress remoteIP3 = InetAddress.getByName(hostIP);
             int remotePort3 = HelperClass.readRemotePort(3);
             
            if (recvValueOfProc1 == recvValueOfProc2 || recvValueOfProc2 == 1) {                
                lcm = recvValueOfProc1;
                System.out.println("lcm(" + fstValue + ", " + sndValue + ", " + trdValue + ") = " + lcm);
                // send values to the slave 3
                sendValueToSlave(socket, values, remoteIP3, remotePort3);
            }
            if (recvValueOfProc1 == 1) {                
                lcm = recvValueOfProc2;
                System.out.println("lcm(" + fstValue + ", " + sndValue + ", " + trdValue + ") = " + lcm);
                // send values to the slave 3
                sendValueToSlave(socket, values, remoteIP3, remotePort3);
            } else {
                values = HelperClass.makeMessage1(method, recvValueOfProc1, recvValueOfProc2);
                // send values to the slave 3
                sendValueToSlave(socket, values, remoteIP3, remotePort3);
                socket.receive(receivePacket);
                data1 = new String(receivePacket.getData());
                recvValueOfProc3 = (Integer.parseInt(data1.trim()));
                System.out.println("lcm(" + fstValue + ", " + sndValue + ", " + trdValue + ") = " + recvValueOfProc3);
            }
        } 
        
        if (method == 0 || method == 1) {
            HelperClass.closeFiles1();
        } else {
            HelperClass.closeFiles2();
        } 
        } else{
        	
        
           
         if (method!=2){

	method = -1; fstValue= -1; sndValue = -1;
        	
       	 DatagramSocket socket = new DatagramSocket();
           int port = socket.getLocalPort();
           InetAddress addressIP = InetAddress.getLocalHost();
           String ip = addressIP.getHostAddress();

           // make address file with IP:PORT
           String address = HelperClass.makeAddress(ip, port);
           HelperClass.makeAddressFile(0, address);  
       	  
       	  	HelperClass.waitSlaves1();
       	    // get remote IP and PORT of the slave 1
       	    String hostIP = HelperClass.readRemoteIP(1);
       	    InetAddress remoteIP1 = InetAddress.getByName(hostIP);
       	    int remotePort1 = HelperClass.readRemotePort(1);

       	    // get remote IP and PORT of the slave 2
       	    hostIP = HelperClass.readRemoteIP(2);
       	    InetAddress remoteIP2 = InetAddress.getByName(hostIP);
       	    int remotePort2 = HelperClass.readRemotePort(2);
       	    
       	    // get remote IP and PORT of the slave 3
       	    hostIP = HelperClass.readRemoteIP(3);
       	    InetAddress remoteIP3 = InetAddress.getByName(hostIP);
       	    int remotePort3 = HelperClass.readRemotePort(3);
       	  
           // send values to the slave 1
           values = HelperClass.makeMessage1(method, fstValue, sndValue);
           sendValueToSlave(socket, values, remoteIP1, remotePort1);

           // send values to the slave 2
           values = HelperClass.makeMessage1(method, fstValue, sndValue);
           sendValueToSlave(socket, values, remoteIP2, remotePort2);
         
           // send values to the slave 3
           values = HelperClass.makeMessage1(method, fstValue, sndValue);
           sendValueToSlave(socket, values, remoteIP3, remotePort3);
           
           HelperClass.closeFiles1();
       } else
           {

	method = -1; fstValue= -1; sndValue = -1;
        	
       	 DatagramSocket socket = new DatagramSocket();
           int port = socket.getLocalPort();
           InetAddress addressIP = InetAddress.getLocalHost();
           String ip = addressIP.getHostAddress();

           // make address file with IP:PORT
           String address = HelperClass.makeAddress(ip, port);
           HelperClass.makeAddressFile(0, address);
	
       	HelperClass.waitSlaves2();
           // get remote IP and PORT of the slave 1
           String hostIP = HelperClass.readRemoteIP(1);
           InetAddress remoteIP1 = InetAddress.getByName(hostIP);
           int remotePort1 = HelperClass.readRemotePort(1);

           // get remote IP and PORT of the slave 2
           hostIP = HelperClass.readRemoteIP(2);
           InetAddress remoteIP2 = InetAddress.getByName(hostIP);
           int remotePort2 = HelperClass.readRemotePort(2);
       	
           		// send values to the slave 1
           	 	values = HelperClass.makeMessage1(method, fstValue, sndValue);
           	    sendValueToSlave(socket, values, remoteIP1, remotePort1);

           	    // send values to the slave 2
           	    values = HelperClass.makeMessage1(method, fstValue, sndValue);
           	    sendValueToSlave(socket, values, remoteIP2, remotePort2);
           	    
           	    HelperClass.closeFiles2();
           	
           }


}




}else {
	
	
    
  if (method!=2){  

	method = -1; fstValue= -1; sndValue = -1;
	
	 DatagramSocket socket = new DatagramSocket();
    int port = socket.getLocalPort();
    InetAddress addressIP = InetAddress.getLocalHost();
    String ip = addressIP.getHostAddress();

    // make address file with IP:PORT
    String address = HelperClass.makeAddress(ip, port);
    HelperClass.makeAddressFile(0, address);
	  
	  	HelperClass.waitSlaves1();
	    // get remote IP and PORT of the slave 1
	    String hostIP = HelperClass.readRemoteIP(1);
	    InetAddress remoteIP1 = InetAddress.getByName(hostIP);
	    int remotePort1 = HelperClass.readRemotePort(1);

	    // get remote IP and PORT of the slave 2
	    hostIP = HelperClass.readRemoteIP(2);
	    InetAddress remoteIP2 = InetAddress.getByName(hostIP);
	    int remotePort2 = HelperClass.readRemotePort(2);
	    
	    // get remote IP and PORT of the slave 3
	    hostIP = HelperClass.readRemoteIP(3);
	    InetAddress remoteIP3 = InetAddress.getByName(hostIP);
	    int remotePort3 = HelperClass.readRemotePort(3);
	  
    // send values to the slave 1
    values = HelperClass.makeMessage1(method, fstValue, sndValue);
    sendValueToSlave(socket, values, remoteIP1, remotePort1);

    // send values to the slave 2
    values = HelperClass.makeMessage1(method, fstValue, sndValue);
    sendValueToSlave(socket, values, remoteIP2, remotePort2);
  
    // send values to the slave 3
    values = HelperClass.makeMessage1(method, fstValue, sndValue);
    sendValueToSlave(socket, values, remoteIP3, remotePort3);
    
    HelperClass.closeFiles1();
} else
    {

method = -1; fstValue= -1; sndValue = -1;
	
	 DatagramSocket socket = new DatagramSocket();
    int port = socket.getLocalPort();
    InetAddress addressIP = InetAddress.getLocalHost();
    String ip = addressIP.getHostAddress();

    // make address file with IP:PORT
    String address = HelperClass.makeAddress(ip, port);
    HelperClass.makeAddressFile(0, address);
	HelperClass.waitSlaves2();
    // get remote IP and PORT of the slave 1
    String hostIP = HelperClass.readRemoteIP(1);
    InetAddress remoteIP1 = InetAddress.getByName(hostIP);
    int remotePort1 = HelperClass.readRemotePort(1);

    // get remote IP and PORT of the slave 2
    hostIP = HelperClass.readRemoteIP(2);
    InetAddress remoteIP2 = InetAddress.getByName(hostIP);
    int remotePort2 = HelperClass.readRemotePort(2);
	
    		// send values to the slave 1
    	 	values = HelperClass.makeMessage1(method, fstValue, sndValue);
    	    sendValueToSlave(socket, values, remoteIP1, remotePort1);

    	    // send values to the slave 2
    	    values = HelperClass.makeMessage1(method, fstValue, sndValue);
    	    sendValueToSlave(socket, values, remoteIP2, remotePort2);
    	    
    	    HelperClass.closeFiles2();
    	
    }

  


}
    }

    // send method
    private static void sendValueToSlave(DatagramSocket socket, String values, 
                                            InetAddress remoteIP, int remotePort) throws IOException {
        byte[] sendBuffer = new byte[255];
        sendBuffer = values.toString().getBytes();
        DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
        socket.send(datagram);
    }
}

