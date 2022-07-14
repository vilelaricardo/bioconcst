import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Passenger {
    public static void main(String[] args) throws Exception {
        int processId = 0;
        int maxTurns = 0;
        int amountOfPassengers = 0;
        int socketId;
        int turns = 0;
        int buf;
        String data = "";


            processId = Integer.parseInt(args[0]); // FIXO 1, 2, 3 .....
            maxTurns = Integer.parseInt(args[1]); // CLASSE CAR
            amountOfPassengers = Integer.parseInt(args[0]); // CLASSE MAIN 

 if(maxTurns>=1 && maxTurns<=10){

        // create socket 1
        socketId = 1;
        DatagramSocket socket1 = new DatagramSocket();
        int port1 = socket1.getLocalPort();
        InetAddress addressIP = InetAddress.getLocalHost();
        String ip = addressIP.getHostAddress();

        // make address file with IP:PORT
        String address1 = HelperClass.makeAddress(ip, port1);
        HelperClass.makeAddressPassengerFile(processId, socketId, address1);

        // create socket 2
        socketId = 2;
        DatagramSocket socket2 = new DatagramSocket();
        int port2 = socket2.getLocalPort();

        // make address file with IP:PORT
        String address2 = HelperClass.makeAddress(ip, port2);
        HelperClass.makeAddressPassengerFile(processId, socketId, address2);

        // create synch file
        Path fp = Paths.get("wait" + (processId + 1));
        Files.createFile(fp);

        // wait files
        HelperClass.waitFile(amountOfPassengers + 2);

        byte[] sendBuffer;
        byte[] receiveBuffer;

        String hostIP = "";
        int remotePort;
        InetAddress remoteIP;

        while (turns < maxTurns) {
            // Is there a car ready in the gate?
            hostIP = HelperClass.readCarIP(1);
            remoteIP = InetAddress.getByName(hostIP);
            remotePort = HelperClass.readCarPort(1);

            String dataString = Integer.toString(processId);
            sendBuffer = new byte[255];
            sendBuffer = dataString.getBytes();
            DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
            socket1.send(datagram);

            receiveBuffer = new byte[255];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket1.receive(receivePacket);
            data = new String(receivePacket.getData());
            buf = (Integer.parseInt(data.trim()));
            //System.out.println("buf = " + buf);

            // tell car that a passenger has just arrived
            hostIP = HelperClass.readCarIP(2);
            remoteIP = InetAddress.getByName(hostIP);
            remotePort = HelperClass.readCarPort(2);

            dataString = Integer.toString(processId);
            sendBuffer = new byte[255];
            sendBuffer = dataString.getBytes();
            DatagramPacket datagram2 = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
            socket1.send(datagram2);

            // waiting my lap finish
            receiveBuffer = new byte[255];
            DatagramPacket receivePacket2 = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket2.receive(receivePacket2);
            data = new String(receivePacket2.getData());
            buf = (Integer.parseInt(data.trim()));
            //System.out.println("buf = " + buf);

            // amount of turns
            turns++;

            /* tell car passenger has just keep out from the car. it sends to
               the car the amount of trips that this passenger has done */
            hostIP = HelperClass.readCarIP(3);
            remoteIP = InetAddress.getByName(hostIP);
            remotePort = HelperClass.readCarPort(3);

            dataString = Integer.toString(turns);
            sendBuffer = new byte[255];
            sendBuffer = dataString.getBytes();
            DatagramPacket datagram3 = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
            socket1.send(datagram3);

            // walking at the park
            Thread.sleep(500); 
        }

        String values = HelperClass.makeMessage(processId, turns);

        hostIP = HelperClass.readMainIP(1);
        remoteIP = InetAddress.getByName(hostIP);
        remotePort = HelperClass.readMainPort(1);

        // tell to main that passenger get out of the park
        sendBuffer = new byte[255];
        sendBuffer = values.toString().getBytes();
        DatagramPacket datagram4 = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
        socket1.send(datagram4);

        for (int i = 1; i < 3; i++) {
            new File("passenger_" + processId + "_socket_" + i + ".txt").delete();
        }
    }}
}
