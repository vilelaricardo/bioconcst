/**
 * Concurrent Benchmarks
 * 
 * Title:  Token Ring with Threads      
 * 
 * Description:   Token Ring with Thread is a program that simulates the token ring topology and,
 *                in addition, each process of the ring create n threads that will make operations in
 *                a shared variable.
 *
 * Paradigm:      Message Passing and Shared Memory
 *               
 * Year:         2014
 * Company:      ICMC/USP - São Carlos
 *               University of São Paulo (USP)
 *               Institute of Mathematics and Computer Science (ICMC)
 *               
 * @author       George Gabriel Mendes Dourado
 * @version      1.0
 */

/*TEST WITH 4 PROCESS AND 3 THREADS FOR EACH 
 * WITH INCREMENT OF THE SHARED VARIABLE
 * 
 * java TokenRingSlave 1 0 0 4
 * java TokenRingSlave 2 0 0 4
 * java TokenRingSlave 3 0 0 4
 * java TokenRingMaster 0 0 4
 * 
 * Result = 15
 */

/*TEST WITH 4 PROCESS AND 3 THREADS FOR EACH 
 * WITH MULTIPLIE OF THE SHARED VARIABLE
 * 
 * java TokenRingSlave 1 1 1 4
 * java TokenRingSlave 2 1 1 4
 * java TokenRingSlave 3 1 1 4
 * java TokenRingMaster 1 1 4
 * 
 * Result = 16384
 */

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.Semaphore;
import java.nio.file.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.net.MulticastSocket;

public class TokenRingMaster {
    public static void main(String args[]) throws Exception {
        long time = System.nanoTime();
        int operation = 0; // 0 or 1 => 0 increment the shared variable, 1 multiplies the shared variable
        int direction = 0; // 0 or 1 => 0 clockwise, 1 counterclockwise
        int clockwiseSendPort = 0;
        int counterclockwiseSendPort = 0;
        int processAmount = 0;
        int serverPort = 2001;
        int sharedVariable = 2;
        String data;
        
  
            operation = Integer.parseInt(args[0]);
            direction = Integer.parseInt(args[1]); 
            processAmount = Integer.parseInt(args[2]);

	if((operation==0) || (operation==1)){	
  		if((direction==0) || (direction==1)){   
        DatagramChannel channel = DatagramChannel.open();
        channel.socket().bind(null); // automatically assigned socket address 
        channel.configureBlocking(false);

        int localPort = channel.socket().getLocalPort();
        ValiparInitializer.publishPort(0, localPort);

        ValiparInitializer.notifyClients(0);
        
        ValiparInitializer.waitServer(String.valueOf(1));
        ValiparInitializer.waitServer(String.valueOf(2));

        clockwiseSendPort = ValiparInitializer.getPort(1);
        counterclockwiseSendPort = ValiparInitializer.getPort(processAmount - 1);

        if (direction == 0) {
            serverPort = clockwiseSendPort;
        } else {
            serverPort = counterclockwiseSendPort;
        }

        ByteBuffer buffer = ValiparInitializer.getBuffer(Integer.toString(sharedVariable).getBytes());

        if (direction == 0) {
            channel.send(buffer, new InetSocketAddress("localhost", clockwiseSendPort));
        } else{
            channel.send(buffer, new InetSocketAddress("localhost", counterclockwiseSendPort));
        }
        
        int value = 0;
        SocketAddress addr;
        do {
            buffer = ByteBuffer.allocate(128);
            addr = channel.receive(buffer);
            if (addr != null) {
                buffer.flip();
                byte[] message = new byte[buffer.remaining()];
                buffer.get(message, 0, message.length);
                buffer.clear();
                String strMessage = new String(message).trim();
                value = Integer.parseInt(strMessage);
            }
        } while (addr == null);

		sharedVariable = value;

        if (operation == 0) {
            sharedVariable++;
        } else if (operation == 1) {
            sharedVariable = sharedVariable * 2;
        }

        System.out.println("\nResult = " + sharedVariable);
        
        double totalTime = ((double) System.nanoTime() - time) / 1_000_000_000;
        System.out.println("Time: " + totalTime); 
    
        ValiparInitializer.clean();
    }}}
}
