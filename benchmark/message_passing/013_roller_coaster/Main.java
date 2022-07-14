/**
 * Concurrent Benchmarks
 * 
 * Title:  Parallel Roller Coaster    
 * 
 * Description:  This benchmark implements the roller coaster program
 *               where we have a car of x seats and we have n passengers
 *               to trip.
 * 
 * Paradigm:     Message Passing               
 *               
 * Year:         2014
 * Company:      ICMC/USP - São Carlos
 *               University of São Paulo (USP)
 *               Institute of Mathematics and Computer Science (ICMC)
 * 
 * @author       Paulo Sérgio Lopes de Souza
 * @java_code    George Gabriel Mendes Dourado
 * @version      1.0
 */

/*
 * TEST 
 * java Main 4
 * java Car 4 2 1
 * java Passenger 1 1 4
 * java Passenger 2 1 4
 * java Passenger 3 1 4
 * java Passenger 4 1 4
 * 
 * OUTPUT:
 * // Main
 * Passenger 2 said goodbye with 1 turns done. 
 * Passenger 1 said goodbye with 1 turns done. 
 * Passenger 4 said goodbye with 1 turns done. 
 * Passenger 3 said goodbye with 1 turns done. 
 * Car says goodbye after 2 trips done!
 * 
 * // Car
 * The adventure number 1 begins with 2 passenger(s)! There is(are) 4 passenger(s) at the park.
 * There are 2 passengers at the park! 
 * The adventure number 2 begins with 2 passenger(s)! There is(are) 2 passenger(s) at the park. 
 */

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        int amountOfPassengers = 0;
        int socketId;
        int pid;
        int trips;
        int maxTurns =1;

            amountOfPassengers = Integer.parseInt(args[0]); // NO MAX 10 E MIN 2 
            maxTurns = Integer.parseInt(args[1]);
 if(maxTurns>=1 && maxTurns<=10){

        // create socket 1
        socketId = 1;
        DatagramSocket socket1 = new DatagramSocket();
        int port1 = socket1.getLocalPort();
        InetAddress addressIP = InetAddress.getLocalHost();
        String ip = addressIP.getHostAddress();

        // make address file with IP:PORT
        String address1 = HelperClass.makeAddress(ip, port1);
        HelperClass.makeAddressMainFile(socketId, address1);

        // create socket 2
        socketId = 2;
        DatagramSocket socket2 = new DatagramSocket();
        int port2 = socket2.getLocalPort();

        // make address file with IP:PORT
        String address2 = HelperClass.makeAddress(ip, port2);
        HelperClass.makeAddressMainFile(socketId, address2);

        // create synch file
        Path fp = Paths.get("wait0");
        Files.createFile(fp);

        // wait synch files
        HelperClass.waitFile(amountOfPassengers + 2);

        byte[] receiveBuffer;

        // join passenger processes
        for (int i = 0; i < amountOfPassengers; i++) {
            receiveBuffer = new byte[255];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket1.receive(receivePacket);
            String[] result = new String(receivePacket.getData()).trim().split(",");
            pid = Integer.parseInt(result[0]);
            trips = Integer.parseInt(result[1]);
            System.out.printf("Passenger %d said goodbye with %d turns done. \n", pid, trips);
        }

        // join car process
        receiveBuffer = new byte[255];
        DatagramPacket receivePacket2 = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket2.receive(receivePacket2);
        String data = new String(receivePacket2.getData());
        trips = (Integer.parseInt(data.trim()));

        // car process sent all trips done by it in this execution
        System.out.printf("Car says goodbye after %d trips done! \n", trips);

        HelperClass.closeFile(amountOfPassengers + 2);

        for (int i = 1; i < 3; i++) {
            new File("main_socket_" + i + ".txt").delete();
        }
    }}
}
