import java.net.*;
import java.io.*;
import java.nio.file.*;

public class GcdSlave {
    public static void main(String[] args) throws IOException {
        int processId = 1; // 1 or 2
        int firstValueSlave; // X if slave process 1 or Y if slave process 2
        int secondValueSlave; // Y if slave process 1 or Z if slave process 2

       
            processId = Integer.parseInt(args[0]);
       

        // create socket
        DatagramSocket socket = new DatagramSocket();
        InetAddress addressIP = InetAddress.getLocalHost();
        String ip = addressIP.getHostAddress();
        int port = socket.getLocalPort();

        // make address file with IP:PORT
        String address = HelperClass.makeAddress(ip, port);
        HelperClass.makeAddressFile(processId, address);

        Path fp = Paths.get("slave" + processId);
        Files.createFile(fp);

        // wait master
        HelperClass.waitMaster();

        // get remote IP and PORT of the master process
        String hostIP = HelperClass.readRemoteIP(0);
        InetAddress remoteIP = InetAddress.getByName(hostIP);
        int remotePort = HelperClass.readRemotePort(0);

        byte[] receiveBuffer;
        byte[] sendBuffer;

        while (true) {
            receiveBuffer = new byte[255];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);
            String[] result = new String(receivePacket.getData()).trim().split(",");
            firstValueSlave = Integer.parseInt(result[0]);
            secondValueSlave = Integer.parseInt(result[1]);

            int gcdResult = gcd(firstValueSlave, secondValueSlave);

            if (gcdResult == -1) {
                break;
            }

            String dataString = Integer.toString(gcdResult);
            sendBuffer = new byte[255];
            sendBuffer = dataString.getBytes();
            DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
            socket.send(datagram);
        }
        socket.close();
        Files.deleteIfExists(fp);
    }

    // method to calculate the GCD
    private static int gcd(int firstValueSlave, int secondValueSlave) {
        if (firstValueSlave == 0 && secondValueSlave == 0) {
            return -1;
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
