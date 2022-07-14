import java.net.*;
import java.io.*;

public class GcdLcmSlave {
    public static void main(String[] args) throws IOException {
        int processId = 0;
        int lcm = 0;
        int method = 0;
        int fstValueSlave = -1;
        int sndValueSlave = -1;
        int trdValueSlave = -1;
        int gcdPartialResult1 = -1;
        int gcdPartialResult2 = -1;
        int gcdFinalResult = -1;
        int lcmPartialResult1 = -1;
        int lcmPartialResult2 = -1;
        int lcmFinalResult = -1;


            processId = Integer.parseInt(args[0]);
     

        // create socket
        DatagramSocket socket = new DatagramSocket();
        int port = socket.getLocalPort();
        InetAddress addressIP = InetAddress.getLocalHost();
        String ip = addressIP.getHostAddress();

        // make address file with IP:PORT
        String address = HelperClass.makeAddress(ip, port);
        HelperClass.makeAddressFile(processId, address);

        // wait master
        HelperClass.waitMaster();

        // get remote IP and PORT of the master process
        String hostIP = HelperClass.readRemoteIP(0);
        InetAddress remoteIP = InetAddress.getByName(hostIP);
        int remotePort = HelperClass.readRemotePort(0);

        byte[] receiveBuffer = new byte[255];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(receivePacket);

        String[] result = new String(receivePacket.getData()).trim().split(",");
        method = Integer.parseInt(result[0]);
        fstValueSlave = Integer.parseInt(result[1]);
        sndValueSlave = Integer.parseInt(result[2]);
        
        if(!(method == -1)){

        if (method == 0) { // GCD
            gcd(socket, fstValueSlave, sndValueSlave, remoteIP, remotePort);
        }
        if (method == 1) { // LCM
            lcm(socket, fstValueSlave, sndValueSlave, lcm, remoteIP, remotePort);
        }
        if (method == 2) { // GCD and LCM
   	    trdValueSlave = Integer.parseInt(result[3]);
            Buffer sharedObject = new Buffer();
            sharedObject.gcdResult = gcdPartialResult1;
            sharedObject.lcmResult = lcmPartialResult1;

            GcdThread gcdThread_0 = new GcdThread();
            GcdThread gcdThread_1 = new GcdThread();
            GcdThread gcdThread_2 = new GcdThread();
            
            LcmThread lcmThread_0 = new LcmThread();
            LcmThread lcmThread_1 = new LcmThread();
            LcmThread lcmThread_2 = new LcmThread();

            if (processId == 1) {
                try {
                    gcdThread_0.setGcdThread(sharedObject, fstValueSlave, sndValueSlave);
                    gcdThread_0.start();
                    gcdThread_0.join();
                    
                    gcdPartialResult1 = sharedObject.gcdResult;
                    System.out.println("gcd(" + fstValueSlave + ", " + sndValueSlave + ") = " + gcdPartialResult1);
                    
                    gcdThread_1.setGcdThread(sharedObject, sndValueSlave, trdValueSlave);
                    gcdThread_1.start();
                    gcdThread_1.join();
                    
                    gcdPartialResult2 = sharedObject.gcdResult;
                    System.out.println("gcd(" + sndValueSlave + ", " + trdValueSlave + ") = " + gcdPartialResult2);
                    
                    String fstValueSlaveString = Integer.toString(fstValueSlave);
                    String sndValueSlaveString = Integer.toString(sndValueSlave);
                    String trdValueSlaveString = Integer.toString(trdValueSlave);
                    String gcdPartialResult1String = Integer.toString(gcdPartialResult1);
                    String gcdPartialResult2String = Integer.toString(gcdPartialResult2);

                    if (gcdPartialResult1 == 1 || gcdPartialResult2 == 1) {
                        System.out.println("gcd(" + gcdPartialResult1String + ", " + gcdPartialResult2String + ") = " + 1);

                        sendStringToMaster(socket, "gcd(" + fstValueSlaveString + ", " + sndValueSlaveString + ", "
                                + trdValueSlaveString + ") = " + 1, remoteIP, remotePort);

                    } else {
                        gcdThread_2.setGcdThread(sharedObject, gcdPartialResult1, gcdPartialResult2);
                        gcdThread_2.start();
                        gcdThread_2.join();
                        
                        
                        gcdFinalResult = sharedObject.gcdResult;

                        System.out.println("gcd(" + gcdPartialResult1String + ", " + gcdPartialResult2String + ") = " + gcdFinalResult);

                        sendStringToMaster(socket, "gcd(" + fstValueSlaveString + ", " + sndValueSlaveString + ", "
                                + trdValueSlaveString + ") = " + gcdFinalResult, remoteIP, remotePort);

                    }
                    
                    int computation1 = sharedObject.totalAmountOfComputation;    
                    System.out.println("computation: " + computation1);
                    
                } catch (InterruptedException e) {}
            }
            if (processId == 2) {
                try {
                    lcmThread_0.setLcmThread(sharedObject, fstValueSlave, sndValueSlave);
                    lcmThread_0.start();
                    lcmThread_0.join();
                    
                    lcmPartialResult1 = sharedObject.lcmResult;
                    System.out.println("lcm(" + fstValueSlave + ", " + sndValueSlave + ") = " + lcmPartialResult1);
                    
                    lcmThread_1.setLcmThread(sharedObject, sndValueSlave, trdValueSlave);
                    lcmThread_1.start();
                    lcmThread_1.join();
                    
                    lcmPartialResult2 = sharedObject.lcmResult;
                    System.out.println("lcm(" + sndValueSlave + ", " + trdValueSlave + ") = " + lcmPartialResult2);
                    
                    lcmThread_2.setLcmThread(sharedObject, lcmPartialResult1, lcmPartialResult2);
                    lcmThread_2.start();
                    lcmThread_2.join();
                    lcmFinalResult = sharedObject.lcmResult;

                    String fstValueSlaveString = Integer.toString(fstValueSlave);
                    String sndValueSlaveString = Integer.toString(sndValueSlave);
                    String trdValueSlaveString = Integer.toString(trdValueSlave);
                    String lcmPartialResult1String = Integer.toString(lcmPartialResult1);
                    String lcmPartialResult2String = Integer.toString(lcmPartialResult2);

                    System.out.println("lcm(" + lcmPartialResult1String + ", " + lcmPartialResult2String + ") = " + lcmFinalResult);

                    sendStringToMaster(socket, "lcm(" + fstValueSlaveString + ", " + sndValueSlaveString + ", "
                            + trdValueSlaveString + ") = " + lcmFinalResult, remoteIP, remotePort);
                            
                    int computation2 = sharedObject.totalAmountOfComputation;    
                    System.out.println("computation: " + computation2); 

                } catch (InterruptedException e) {}
            }
        }}
    }

    private static void gcd(DatagramSocket socket, int fstValueSlave, int sndValueSlave, InetAddress remoteIP, int remotePort) throws IOException {
        if (fstValueSlave == 0 && sndValueSlave == 0) {
            // this process is finalized;
            System.exit(0);
        } else {
            // otherwise, calculates the GCD
            int tempfstValueSlave = fstValueSlave, tempsndValueSlave = sndValueSlave;
            while (tempfstValueSlave != tempsndValueSlave) {
                if (tempfstValueSlave < tempsndValueSlave) {
                    tempsndValueSlave = tempsndValueSlave - tempfstValueSlave;
                } else {
                    tempfstValueSlave = tempfstValueSlave - tempsndValueSlave;
                }
            }
            System.out.println("gcd(" + fstValueSlave + ", " + sndValueSlave + ") = " + tempfstValueSlave);
            sendValueToMaster(socket, tempfstValueSlave, remoteIP, remotePort);
        }
    }

    private static void lcm(DatagramSocket socket, int fstValueSlave, int sndValueSlave, int lcm, InetAddress remoteIP, int remotePort)
            throws IOException {
        if (fstValueSlave == sndValueSlave || sndValueSlave == 1) {
        }
        if (fstValueSlave == 1) {
            lcm = sndValueSlave;
        } else {
            lcm = 0;
        }

        if (lcm == 0) {
            int tempfstValueSlave = fstValueSlave, tempsndValueSlave = sndValueSlave;
            while (tempfstValueSlave != tempsndValueSlave) {
                while (tempfstValueSlave < tempsndValueSlave) {
                    tempfstValueSlave += fstValueSlave;
                }
                while (tempsndValueSlave < tempfstValueSlave) {
                    tempsndValueSlave += sndValueSlave;
                }
            }
            lcm = tempfstValueSlave;
        }
        System.out.println("lcm(" + fstValueSlave + ", " + sndValueSlave + ") = " + lcm);
        sendValueToMaster(socket, lcm, remoteIP, remotePort);
    }

    private static void sendValueToMaster(DatagramSocket socket, int value, InetAddress remoteIP, int remotePort) throws IOException {
        byte[] sendBuffer = new byte[255];
        String dataString = Integer.toString(value);
        sendBuffer = dataString.getBytes();
        DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
        socket.send(datagram);
    }

    private static void sendStringToMaster(DatagramSocket socket, String value, InetAddress remoteIP, int remotePort) throws IOException {
        byte[] sendBuffer = new byte[255];
        sendBuffer = value.getBytes();
        DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
        socket.send(datagram);
    }
}
