/**
 * Concurrent Benchmarks
 * 
 * Title:  Parallel Sieve of Eratosthenes        
 * 
 * Description:  This benchmark implements the sieve of eratosthenes 
 * 		 solution to find primes in an interval of numbers.  
 *
 * Paradigm:     Message Passing
 *               
 * Year:         2014
 * Company:      ICMC/USP - São Carlos
 *               University of São Paulo (USP)
 *               Institute of Mathematics and Computer Science (ICMC)
 *               
 * @author       George Gabriel Mendes Dourado
 * @version      1.0
 */

/* TEST 1
 * java Slave 1 4 100
 * java Slave 2 4 100
 * java Slave 3 4 100
 * java Master 0 4 100
 * 
 * OUTPUT:
 * ******** ALL PRIMES *******
 * 2 3 5 7 11 13 17 19 23 29
 * 31 37 41 43 47 53 59 61 67 71
 * 73 79 83 89 97
 */

import java.io.*;
import java.net.*;
import java.util.*;

public class Master {
    public static void main(String[] args) throws IOException {
        final int FINISH = -1;
        int processId = 0;
        int numberOfProcesses = 0;
        int range = 0; // the limit is 10000
        int currentPrime = 2;
        int size = 0;
        int remainder = 0;
        List<Integer> primes = new ArrayList<>();
        String out = "";

            processId = Integer.parseInt(args[0]); // FIXO 0 MESTRE
            numberOfProcesses = Integer.parseInt(args[1]); // NO MIN 2 
            range = Integer.parseInt(args[2]); // QUALQUER VALOR POSITIVO < 10000
  if((range>=20 && range<=10000)){

        // create socket
        DatagramSocket socket = new DatagramSocket();
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLocalHost();
        String ip = addressIP.getHostAddress();

        // make address file with IP:PORT
        String address = HelperClass.makeAddress(ip, port);
        HelperClass.makeAddressFile(0, address);

        // wait slaves
        HelperClass.waitSlaves(numberOfProcesses - 1);

        remainder = range % numberOfProcesses;
        size = range / numberOfProcesses;

        if (remainder != 0) {
            size = size + 1;
        }

        int array[] = new int[size];

        // initializing all numbers as unmarked (represented by zero)
        for (int i = 0; i < size; i++) {
            array[i] = 0;
        }

        // set multicast address
        InetAddress group = InetAddress.getByName("228.5.6.7");
        MulticastSocket socketBroadcast = new MulticastSocket(6789);
        socketBroadcast.joinGroup(group);

        // sending the current prime to the others processes
        for (int i = currentPrime; i < Math.sqrt(range); i++) {
            if (array[i] == 0) {
                DatagramPacket broadcastResult = new DatagramPacket(Integer.toString(i).getBytes(), Integer.toString(i).length(), group, 6789);
                socketBroadcast.send(broadcastResult);

                // marking the numbers that are multiples of the current prime
                for (int j = i * i; j < size; j += i) {
                    array[j] = 1;
                }
            }
        }

        DatagramPacket broadcastResult = new DatagramPacket(Integer.toString(FINISH).getBytes(), Integer.toString(FINISH).length(), group, 6789);

        // sending the value -1 to finish iterations of slaves
        socketBroadcast.send(broadcastResult);

        // checking the amount of primes in the master process
        for (int i = 2; i < size; i++) {
            if (array[i] == 0) {
                primes.add(i);
            }
        }

        byte[] sendBuffer = new byte[4048];
        byte[] receiveBuffer = new byte[4048];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

        int loop = 0;
        int iteration = 1;

        // reduce
        while (loop != 1) {
            socket.receive(receivePacket);
            String[] result = new String(receivePacket.getData()).trim().split(" ");

            for (int j = 0; j < result.length; j++) {
                primes.add(Integer.parseInt(result[j].trim()));
            }

            iteration = iteration * 2;
            loop = numberOfProcesses / iteration + (numberOfProcesses % iteration > 0 ? 1 : 0);
        }

        // sort the array
        Collections.sort(primes);

        System.out.print("\n******** ALL PRIMES *******\n");
        for (int k = 0; k < primes.size(); k++) {
            if (k % 10 == 0) {
                System.out.println();
            }
            System.out.print(" " + primes.get(k));
        }
        System.out.println();
        HelperClass.closeFiles(numberOfProcesses);
    }}
}
