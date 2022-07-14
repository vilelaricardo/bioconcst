import java.net.*;
import java.io.*;


public class GcdLcmSlave {
    public static void main(String[] args) throws IOException {
        int processId = 0;
        int lcm = 0;
        int method = 0;
        int firstValueSlave;
        int secondValueSlave;


            processId = Integer.parseInt(args[0]);

        // create socket
        DatagramSocket socket = new DatagramSocket();
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLocalHost();
        String ip = addressIP.getHostAddress();

        // make address file with IP:PORT
        String address = HelperClass.makeAddress(ip, port);
        HelperClass.makeAddressFile(processId, address);

        // wait master
        HelperClass.waitMaster();

        // get remote IP and PORT of the master process
        String hostIP = HelperClass.readRemoteIP(0);
        InetAddress remoteIP = InetAddress.getByName(hostIP);
        int remotePort = HelperClass.readRemotePort(0);

        byte[] receiveBuffer = new byte[255];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(receivePacket);

        String[] result = new String(receivePacket.getData()).trim().split(",");
        method = Integer.parseInt(result[0]);
        firstValueSlave = Integer.parseInt(result[1]);
        secondValueSlave = Integer.parseInt(result[2]);

 if(method != -1 && firstValueSlave != -1 && secondValueSlave != -1 ) {      
        if (processId == 3) {
            if ((method == 1 && (firstValueSlave == secondValueSlave || secondValueSlave == 1)) ||
                (method == 1 && firstValueSlave == 1)) {
                System.exit(0);     
           }
        }
        
        int finalResult = 0;
        if (method == 0) { // GCD
            finalResult = gcd(firstValueSlave, secondValueSlave);
            System.out.println("gcd(" + firstValueSlave + ", " + secondValueSlave + ") = " + finalResult);
        } else if (method == 1) { // LCM
            finalResult = lcm(firstValueSlave, secondValueSlave, lcm);
            System.out.println("lcm(" + firstValueSlave + ", " + secondValueSlave + ") = " + finalResult);
        }

        byte[] sendBuffer = new byte[255];
        String dataString = Integer.toString(finalResult);
        sendBuffer = dataString.getBytes();
        DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
        socket.send(datagram);
        socket.close();
 }

 }

    // method to calculate the GCD
    private static int gcd(int firstValueSlave, int secondValueSlave) {
        int tempFirstValueSlave = 0;
        int tempSecondValueSlave = 0;
        if (firstValueSlave == 0 && secondValueSlave == 0) {
            // this process is finalized;
            System.exit(0); 
        } else {
            // otherwise, calculates the GCD
            tempFirstValueSlave = firstValueSlave;
            tempSecondValueSlave = secondValueSlave;
            while (tempFirstValueSlave != tempSecondValueSlave) {
                if (tempFirstValueSlave < tempSecondValueSlave) {
                    tempSecondValueSlave = tempSecondValueSlave - tempFirstValueSlave;
                } else {
                    tempFirstValueSlave = tempFirstValueSlave - tempSecondValueSlave;
                }
            }
        }
        return tempFirstValueSlave;
    }

    // method to calculate the LCM
    private static int lcm(int firstValueSlave, int secondValueSlave, int lcm) throws IOException {
        if (firstValueSlave == secondValueSlave || secondValueSlave == 1) {
            lcm = firstValueSlave;
        } else if (firstValueSlave == 1) {
            lcm = secondValueSlave;
        } else {
            lcm = 0;
        }
        
        int tempFirstValueSlave = 0;
        int tempSecondValueSlave = 0;
        if (lcm == 0) {
            tempFirstValueSlave = firstValueSlave;
            tempSecondValueSlave = secondValueSlave;
            while (tempFirstValueSlave != tempSecondValueSlave) {
                while (tempFirstValueSlave < tempSecondValueSlave) {
                    tempFirstValueSlave += firstValueSlave;
                }
                while (tempSecondValueSlave < tempFirstValueSlave) {
                    tempSecondValueSlave += secondValueSlave;
                }
            }
            lcm = tempFirstValueSlave;
        }
        return lcm;
    }
}
