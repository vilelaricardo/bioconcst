import java.net.*;
import java.io.*;

public class GcdSlave {
    public static void main(String[] args) throws IOException {
        int processId = 1; // 1 or 2 or 3
        Integer firstValueSlave; // X if slave process 1 or Y if slave process 2
        Integer secondValueSlave; // Y if slave process 1 or Z if slave process 2

            processId = Integer.parseInt(args[0]);

        // create socket
        DatagramSocket socket = new DatagramSocket();
        InetAddress addressIP = InetAddress.getLocalHost();
        String ip = addressIP.getHostAddress();
        int port = socket.getLocalPort();

        // make address file with IP:PORT
        String address = HelperClass.makeAddress(ip, port);
        HelperClass.makeAddressFile(processId, address);

        // wait master
        HelperClass.waitMaster();

        // get remote IP and PORT of the master process
        String hostIP = HelperClass.readRemoteIP(0);
        InetAddress remoteIPMaster = InetAddress.getByName(hostIP);
        int remotePortMaster = HelperClass.readRemotePort(0);

	    // receives the values of the master process
        byte[] receiveBuffer = new byte[255];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(receivePacket);
        String[] result = new String(receivePacket.getData()).trim().split(",");
        firstValueSlave = Integer.parseInt(result[0]);
        secondValueSlave = Integer.parseInt(result[1]);
        
        

	if(firstValueSlave != -1 && secondValueSlave != -1){

        // calculates the GCD
        int gcdResult = gcd(firstValueSlave, secondValueSlave);
        String dataString = Integer.toString(gcdResult);
        
        if (firstValueSlave != 0 && firstValueSlave != 0) {
            // sends result to master process
            byte[] sendBuffer = new byte[255];
            sendBuffer = dataString.getBytes();
            DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIPMaster, remotePortMaster);
            socket.send(datagram);
            socket.close();
        }}
		
	// delete address file
	new File("parallel_gcd" + processId + ".txt").delete();
    }

    // method to calculate the GCD
    private static int gcd(int firstValueSlave, int secondValueSlave) {
        if (firstValueSlave == 0 && secondValueSlave == 0) {
            // finish
            return 0;
        } else {
            while (firstValueSlave != secondValueSlave) {
                if (firstValueSlave < secondValueSlave) {
                    secondValueSlave = secondValueSlave - firstValueSlave;
                } else {
                    firstValueSlave = firstValueSlave - secondValueSlave;
                }
            }
        }
        return firstValueSlave;
    }
}

