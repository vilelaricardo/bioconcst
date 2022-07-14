import java.io.*;
import java.net.*;
import java.nio.file.*;

public class Slave {
    public static void main(String[] args) throws IOException {
        int range = 0; // the limit is 10000
        int numberOfProcesses = 0;
        int processId = 0;
        int size = 0;
        int lowValue = 0;
        int highValue = 0;
        int index = 0;


            processId = Integer.parseInt(args[0]);
            numberOfProcesses = Integer.parseInt(args[1]);
            range = Integer.parseInt(args[2]);

if((range>=20 && range<=10000)){
   
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

        int remainder = range % numberOfProcesses;
        size = range / numberOfProcesses;

        // set lowValue, highValue and size
        if (remainder != 0) {
            if (remainder > processId) {
                lowValue = processId;
                highValue = (processId + processId) + 1;
                size = size + 1;
            } else {
                lowValue = remainder;
                highValue = remainder + processId;
            }
        }

        int array[] = new int[size];

        if (remainder == 0) {
            highValue = processId;
        }

        lowValue = (processId * size) + lowValue;
        highValue = ((processId + 1) * (size - 1)) + highValue;

        // create multicast socket
        InetAddress group = InetAddress.getByName("228.5.6.7");
        MulticastSocket socketBroadcast = new MulticastSocket(6789);
        socketBroadcast.joinGroup(group);

        byte[] buf = new byte[4048];
        DatagramPacket recv = new DatagramPacket(buf, buf.length);

        int count = 0;
        int currentPrime = 0;
        StringBuilder builder = new StringBuilder();
        String out = "";

        System.out.print("\n******** PRIMES *******\n");

        while (currentPrime != -1) {
            // receive currentPrime
            socketBroadcast.receive(recv);
            String data = new String(recv.getData());
            currentPrime = (Integer.parseInt(data.trim()));
            if (currentPrime == -1) {
                for (int i = 0; i < size; i++) {
                    if (array[i] == 0) {
                        if (count % 10 == 0) {
                            System.out.println();
                        }
                        System.out.print((i + lowValue) + " ");
                        out = HelperClass.makeMessagePrimes(builder, (i + lowValue));
                        // primes.add(i);
                        count++;
                    }
                }
                System.out.println();
                break;
            } else {
                for (int j = 0; j < size; j++) {
                    if ((j + lowValue) % currentPrime == 0) {
                        index = j;
                        break;
                    }
                }

                // exclude the others multiples of this currentPrime
                for (int y = index; y < size; y += currentPrime) {
                    array[y] = 1;
                }
            }
        }

        byte[] sendBuffer = new byte[4048];
        byte[] receiveBuffer = new byte[4048];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

        int x = 0;
        int loop = 0;
        int iteration = 1;

        // reduce
        while (loop != 1) {
            boolean isLast = HelperClass.thisProcessIsLast(processId, iteration, numberOfProcesses);
            int value = processId / iteration + (processId % iteration > 0 ? 1 : 0);

            if (value % 2 == 0 && !isLast) { // receive data of other slave
                socket.receive(receivePacket);
                String[] result = new String(receivePacket.getData()).trim().split(" ");

                for (int j = 0; j < result.length; j++) {
                    x = Integer.parseInt(result[j].trim());
                    out = HelperClass.makeMessagePrimes(builder, x);
                }

            } else if (value % 2 != 0) {
                // destination to send data
                int toSend = ((((processId / iteration) - 1) + (((processId % iteration) > 0) ? 1 : 0)) * iteration);

                // get destination address
                String hostIP = HelperClass.readRemoteIP(toSend);
                InetAddress remoteIP = InetAddress.getByName(hostIP);
                int remotePort = HelperClass.readRemotePort(toSend);

                sendBuffer = out.getBytes();
                DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
                socket.send(datagram);
                break;
            }
            iteration = iteration * 2;
            loop = numberOfProcesses / iteration + (numberOfProcesses % iteration > 0 ? 1 : 0);
        }
    }}
}
