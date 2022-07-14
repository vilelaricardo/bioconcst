import java.io.FileReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.Semaphore;
import java.nio.file.*;
import java.util.concurrent.CyclicBarrier;
import java.net.MulticastSocket;

public class TokenRingSlave {
    public static void main(String args[]) throws Exception {
        int processId = 0;
        int operation = 0; // 0 or 1 => 0 increment the shared variable, 1 multiplies the shared variable
        int direction = 0; // 0 or 1 => 0 clockwise, 1 counterclockwise
        int amountOfProcesses = 0;
        int serverPort = 0;
        String data;


            processId = Integer.parseInt(args[0]);
            operation = Integer.parseInt(args[1]);
            direction = Integer.parseInt(args[2]);
            amountOfProcesses = Integer.parseInt(args[3]);

if((operation==0) || (operation==1)){	
  		if((direction==0) || (direction==1)){   
        
        // create socket
        DatagramSocket socket = new DatagramSocket(); 
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLocalHost();
        String ip = addressIP.getHostAddress();
        
        InetAddress group = InetAddress.getByName("228.5.6.7");
        MulticastSocket socketBroadcast = new MulticastSocket(6789);
        socketBroadcast.joinGroup(group);
        
        InetAddress serverIP = InetAddress.getLocalHost();
        
        // make address file with IP:PORT
        String address = HelperClass.makeAddress(ip, port); 
        HelperClass.makeAddressFile(processId, address); 
   
        // identification of the clockwise and counterclockwise neighbors
        int clockwiseAddress = HelperClass.getClockwiseNeighbor(processId, amountOfProcesses); 
        int counterclockwiseAddress = HelperClass.getCounterclockwiseNeighbor(processId, amountOfProcesses);
        
        // wait neighbors
        HelperClass.waitNeighbors(clockwiseAddress, counterclockwiseAddress);
        
        int clockwiseSendPort = HelperClass.readNeighborPort(clockwiseAddress);
        int counterclockwiseSendPort = HelperClass.readNeighborPort(counterclockwiseAddress);
                
        Semaphore tokenToProducer = new Semaphore(0);
        tokenToProducer.release();

        CyclicBarrier barrier1 = new CyclicBarrier(3);
        CyclicBarrier barrier2 = new CyclicBarrier(3);

        Buffer sharedObject = new Buffer();

        Producer producer1 = new Producer();
        producer1.setProducer(operation, tokenToProducer, barrier1, barrier2);
        producer1.start();

        Producer producer2 = new Producer();
        producer2.setProducer(operation, tokenToProducer, barrier1, barrier2);
        producer2.start();

        producer1.setSharedObject(sharedObject);
        producer2.setSharedObject(sharedObject);

        byte[] receiveBuffer = new byte[255];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(receivePacket);
        data = new String(receivePacket.getData());
        int sharedVariable = (Integer.parseInt(data.trim()));

        System.out.println("Received of the neighbor = " + sharedVariable);
        sharedObject.setSharedObject(sharedVariable);

        if (operation == 0) {
            sharedObject.setSharedIntIncrement();
        } else{
            sharedObject.setSharedIntMultiplie();
        }

        barrier1.await();
        barrier2.await();

        sharedVariable = sharedObject.getSharedObject();

        if (direction == 0) {
            serverPort = clockwiseSendPort;
        } else{
            serverPort = counterclockwiseSendPort;
        }

        byte[] sendBuffer = new byte[255];
        String dataString = Integer.toString(sharedVariable);
        sendBuffer = dataString.getBytes();
        DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, serverIP, serverPort);

        if (direction == 0) {
            socket.send(datagram);
        } else{
            socket.send(datagram);
        }

        socket.close();
        
        byte[] buf = new byte[128];
        DatagramPacket recv = new DatagramPacket(buf, buf.length);

        socketBroadcast.receive(recv);
        System.out.print("Broadcast received = ");
        System.out.println(new String(recv.getData()));
        socketBroadcast.leaveGroup(group);
        socketBroadcast.close();
        
        socket.close();
    }}}
}
