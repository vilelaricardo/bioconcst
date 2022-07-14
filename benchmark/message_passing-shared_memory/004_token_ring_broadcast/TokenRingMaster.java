
/**
 * Concurrent Benchmarks
 * 
 * Title:  Token Ring Broadcast      
 * 
 * Description:  Token Ring Broadcast is a program that simulates a token ring topology and,
 *               in addition, each process of the ring create n threads that will make operations
 *               in a shared variable. The Slaves processes makes use of barriers to synchronize
 *               the threads and finally, the Master process makes a broadcast of the final result
 *               to all the Slaves.
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

/* TEST WITH 4 PROCESSES
 * java TokenRingSlave 1 0 0 4
 * java TokenRingSlave 2 0 0 4
 * java TokenRingSlave 3 0 0 4
 * java TokenRingMaster 0 0 4
 * 
 * OUTPUT:
 * Result = 12
 * Broadcast final result.
 */

import java.io.FileReader;
import java.net.DatagramPacket;
import java.net.InetAddress;
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
        int amountOfProcesses = 0;
        int sharedVariable = 2;


            operation = Integer.parseInt(args[0]); // 0 OU 1 
            direction = Integer.parseInt(args[1]); // 0 OU 1
            amountOfProcesses = Integer.parseInt(args[2]); //  NO MIN 3

	if((operation==0) || (operation==1)){	
  		if((direction==0) || (direction==1)){   

        // create socket non-blocking address
        DatagramChannel channel = DatagramChannel.open();
        channel.socket().bind( new InetSocketAddress( 0 ));
        channel.configureBlocking(false);

        // get PORT
        int localPort = channel.socket().getLocalPort();
        
        // get local IP
        InetAddress addressIP = InetAddress.getLocalHost();
        String ip = addressIP.getHostAddress();
        
        // broadcast
        InetAddress group = InetAddress.getByName("228.5.6.7");
        MulticastSocket socket = new MulticastSocket(6789);
        socket.joinGroup(group);
        
        // make address file with IP:PORT
        String address = HelperClass.makeAddress(ip , localPort);
        HelperClass.makeAddressFile(0, address); 
        
        // identification of the clockwise and counterclockwise neighbors
        int clockwiseAddress = HelperClass.getClockwiseNeighbor(0, amountOfProcesses); 
        int counterclockwiseAddress = HelperClass.getCounterclockwiseNeighbor(0, amountOfProcesses);
        
        // wait neighbors
        HelperClass.waitNeighbors(clockwiseAddress, counterclockwiseAddress);
        
        // clockwise address
        int clockwiseSendPort = HelperClass.readNeighborPort(clockwiseAddress);
        String clockwiseIP = HelperClass.readNeighborIP(clockwiseAddress);
        
        // counterclockwise address
        int counterclockwiseSendPort = HelperClass.readNeighborPort(counterclockwiseAddress);
        String counterclockwiseclockwiseIP = HelperClass.readNeighborIP(counterclockwiseAddress);

        ByteBuffer buffer = HelperClass.getBuffer(Integer.toString(sharedVariable).getBytes());

        if (direction == 0) {
            channel.send(buffer, new InetSocketAddress(clockwiseIP, clockwiseSendPort));
        } else{
            channel.send(buffer, new InetSocketAddress(counterclockwiseclockwiseIP , counterclockwiseSendPort));
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
        } else{
            sharedVariable = sharedVariable * 2;
        }

        System.out.println("\nResult = " + sharedVariable);

        DatagramPacket broadcastResult = new DatagramPacket(Integer.toString(sharedVariable).getBytes(), 
                Integer.toString(sharedVariable).length(), group, 6789);

        socket.send(broadcastResult);
        socket.leaveGroup(group);
        socket.close();

        System.out.println("Broadcast final result.");

        double totalTime = ((double) System.nanoTime() - time) / 1_000_000_000;
        System.out.println("Time: " + totalTime);

        HelperClass.closeFiles(amountOfProcesses);
    }}}
}
