import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Car {
    public static void main(String[] args) throws Exception {
        int carSize = 0;
        int socketId;
        int trips = 0;
        int maxTurns = 0;
        int passengersInTheCar = 0;
        int passengersAtThePark = 0;
        int amountOfPassengers = 0;
        int turns = 0;


            amountOfPassengers = Integer.parseInt(args[0]); // CLASSE MAIN 
            carSize = Integer.parseInt(args[1]); // FIXO 2
            maxTurns = Integer.parseInt(args[2]); // MAIOR QUE 1 

 if(maxTurns>=1 && maxTurns<=10){

        int vetRanks[] = new int[carSize];
        passengersAtThePark = amountOfPassengers;

        // create socket 1
        socketId = 1;
        DatagramSocket socket1 = new DatagramSocket();
        int port1 = socket1.getLocalPort();
        InetAddress addressIP = InetAddress.getLocalHost();
        String ip = addressIP.getHostAddress();

        // make address file with IP:PORT
        String address1 = HelperClass.makeAddress(ip, port1);
        HelperClass.makeAddressCarFile(socketId, address1);

        // create socket 2
        socketId = 2;
        DatagramSocket socket2 = new DatagramSocket();
        int port2 = socket2.getLocalPort();

        // make address file with IP:PORT
        String address2 = HelperClass.makeAddress(ip, port2);
        HelperClass.makeAddressCarFile(socketId, address2);

        // create socket 3
        socketId = 3;
        DatagramSocket socket3 = new DatagramSocket();
        int port3 = socket3.getLocalPort();

        // make address file with IP:PORT
        String address3 = HelperClass.makeAddress(ip, port3);
        HelperClass.makeAddressCarFile(socketId, address3);

        // create synch file
        Path fp = Paths.get("wait1");
        Files.createFile(fp);

        // wait synch files
        HelperClass.waitFile(amountOfPassengers + 2);

        byte[] sendBuffer;
        byte[] receiveBuffer;

        while (true) {
            // tell "carSize" passengers that car has just arrived to the gate
            for (int i = 0; i < carSize; i++) {
                receiveBuffer = new byte[255];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                socket1.receive(receivePacket);
                String data = new String(receivePacket.getData());
                vetRanks[i] = (Integer.parseInt(data.trim()));

                String hostIP = HelperClass.readPassengerIP(vetRanks[i], 1);
                InetAddress remoteIP = InetAddress.getByName(hostIP);
                int remotePort = HelperClass.readPassengerPort(vetRanks[i], 1);

                String dataString = Integer.toString(vetRanks[i]);
                sendBuffer = new byte[255];
                sendBuffer = dataString.getBytes();
                DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
                socket1.send(datagram);
            }
            
            // waiting car fill up
            while (passengersInTheCar < carSize) {
                receiveBuffer = new byte[255];
                DatagramPacket receivePacket2 = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                
                // waiting for a passenger
                socket2.receive(receivePacket2);
                String data = new String(receivePacket2.getData());
                vetRanks[passengersInTheCar] = (Integer.parseInt(data.trim()));
                
                // counting how many passengers there are now in the car
                passengersInTheCar++;
            }

            trips++; // counting how many trips have been done

            System.out.printf("The adventure number %d begins with %d passenger(s)! "
                    + "There is(are) %d passenger(s) at the park.\n", trips, passengersInTheCar, passengersAtThePark);

            Thread.sleep(500); // having a funny and crazy trip
            
            // waiting car be empty out
            while (passengersInTheCar > 0) {
                // tell passengers that lap is over
                String hostIP = HelperClass.readPassengerIP(vetRanks[(passengersInTheCar - 1)], 2);
                InetAddress remoteIP = InetAddress.getByName(hostIP);
                int remotePort = HelperClass.readPassengerPort(vetRanks[(passengersInTheCar - 1)], 2);

                String dataString = Integer.toString(vetRanks[(passengersInTheCar - 1)]);
                sendBuffer = new byte[255];
                sendBuffer = dataString.getBytes();
                DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
                socket1.send(datagram);

                receiveBuffer = new byte[255];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                
                /* wait passenger to keep out from car. 
                   It also receives the amount of trips that this passenger has done. */
                socket3.receive(receivePacket);
                String data = new String(receivePacket.getData());
                turns = (Integer.parseInt(data.trim()));

                /* if amount of trips done by this passenger > maxTurns then
                   passenger is departing from park. */
                if (turns >= maxTurns) {
                    passengersAtThePark--;
                }
                // there is now minus one passenger in the car
                passengersInTheCar--;

            }

            // if park is empty, I must go out!
            if (passengersAtThePark == 0) {
                break; // Yes! I´m going out!
            } else if (passengersAtThePark < carSize) {
                carSize = passengersAtThePark; // there are few passengers at the park
            }
            System.out.printf("There are %d passengers at the park! \n", passengersAtThePark);

        }

        String hostIP = HelperClass.readMainIP(2);
        InetAddress remoteIP = InetAddress.getByName(hostIP);
        int remotePort = HelperClass.readMainPort(2);

        /* tell to main that car is out! 
           It also send amount of trips done by the car in this execution. */
        String dataString = Integer.toString(trips);
        sendBuffer = new byte[255];
        sendBuffer = dataString.getBytes();
        DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
        socket1.send(datagram);

        for (int i = 1; i < 4; i++) {
            new File("car_socket_" + i + ".txt").delete();
        }
    }}
}
