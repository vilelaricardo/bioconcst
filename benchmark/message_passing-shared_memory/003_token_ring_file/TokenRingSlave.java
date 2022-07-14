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
        int clockwiseSendPort;
        int counterclockwiseSendPort;
        int processAmount = 0;
        int serverPort = 0;
        String data;
        String data1;
        

            processId = Integer.parseInt(args[0]);
            operation = Integer.parseInt(args[1]);
            direction = Integer.parseInt(args[2]);
            processAmount = Integer.parseInt(args[3]);
        
if((operation==0) || (operation==1)){	
  		if((direction==0) || (direction==1)){   
        
        DatagramSocket server = new DatagramSocket(); // dynamic port binding
        int port = server.getLocalPort();
        ValiparInitializer.publishPort(processId, port);

        ValiparInitializer.notifyClients(String.valueOf(processId));

        int beforeProcess = (processId + processAmount + 1) % processAmount;
        int afterProcess = (processId + processAmount - 1) % processAmount;

        ValiparInitializer.waitServer(String.valueOf(beforeProcess));
        ValiparInitializer.waitServer(String.valueOf(afterProcess));

        clockwiseSendPort = ValiparInitializer.getPort(beforeProcess);
        counterclockwiseSendPort = ValiparInitializer.getPort(afterProcess);

        Semaphore tokenToProducer = new Semaphore(0);
        tokenToProducer.release();
        
        Buffer sharedObject = new Buffer();

        byte[] receiveBuffer  = new byte[255];
        
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        server.receive(receivePacket);
        data = new String(receivePacket.getData());
        int sharedVariable = (Integer.parseInt(data.trim()));
        
        sharedObject.setSharedObject(sharedVariable);
        
        if (operation == 0) {
            sharedObject.setSharedIntIncrement();
        } else{
            sharedObject.setSharedIntMultiplie();
        }
        
        Producer producer1 = new Producer();
        producer1.setProducer(tokenToProducer, operation);
        producer1.setSharedObject(sharedObject);
        producer1.start(); 
        
        Producer producer2 = new Producer();
        producer2.setProducer(tokenToProducer, operation);
        producer2.setSharedObject(sharedObject); 
        producer2.start(); 
        
        Producer producer3 = new Producer();
        producer3.setProducer(tokenToProducer, operation);
        producer3.setSharedObject(sharedObject); 
        producer3.start(); 
       
		producer1.join();
        producer2.join();        
        producer3.join();
        
        sharedVariable = sharedObject.getSharedObject();
        
        byte [] sendBuffer = new byte [255];
        DatagramSocket clockwiseSocket = new DatagramSocket(); 
        DatagramSocket counterclockwiseSocket = new DatagramSocket();
        InetAddress serverIP = InetAddress.getLocalHost();

        if (direction == 0) {
            serverPort = clockwiseSendPort; 
        } else {
            serverPort = counterclockwiseSendPort;
        } 
  
        String dataString = Integer.toString(sharedVariable);
        sendBuffer = dataString.getBytes();
        DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, serverIP, serverPort);

        if (direction == 0) {
            clockwiseSocket.send(datagram);
            clockwiseSocket.close();    
        } else{
            counterclockwiseSocket.send(datagram);
            counterclockwiseSocket.close(); 
        } 
        
        ValiparInitializer.clean(String.valueOf(processId));
    }}}
}

