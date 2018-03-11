package com.company;

import java.io.IOException;
import java.net.*;

public class Listener extends Thread{
    private byte[] receiveData = new byte[1024]; //TODO: modify these byte array sizes
    private byte[] sendData = new byte[1024];
    private DatagramSocket serverSocket;
    private int port = 0;
    public IpTable ip_table;
    public RttTable rtt_table;
    public boolean listening = true;
    public Listener(int port) {
        this.port = port;
    }
    public void run() {
        try {
            serverSocket = new DatagramSocket(port);
        } catch (SocketException e) {
            e.printStackTrace();
        }


        while(listening) //look through queue of received packets and parse them one by one(no concurrent receive)
        {
            try {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                //listener thread blocks on this until something is received
                serverSocket.receive(receivePacket);

                //String query = new String(receivePacket.getData());
                //receivePacket.getData();
                //InetAddress IPAddress = receivePacket.getAddress();
                //int port = receivePacket.getPort();

                parsePacket(receivePacket);

                //send packet using same port from incoming packet and IPaddress
                //DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
                //serverSocket.send(sendPacket);
            } catch(IOException e) {
                System.out.println("some connection with client failed");
                e.printStackTrace();
            }
        }



        //we have finished listening for packets.
        /*TODO: cleanup for listener thread
        WAIT FOR CHILDREN TO EXIT before exiting

         listener thread is killed on return
         */
        return;
    }
    public void parsePacket(DatagramPacket packet) {

        InetAddress IPAddress = packet.getAddress();
        int port = packet.getPort();
        byte[] data = packet.getData();
        /*TODO: implement cases on packet headers.
        1. parse packet header from payload bytes
        2. identify what needs to be done, call that particular method with a new thread.
        3. mark all data structures used by that thread as synchronized(use either a sync block, semaphore or intrinsic lock)
        4. if needed, send a response to whoever and then return/exit thread.

        NOTE: IT IS THE JOB OF THE RTT RESPONSE THREAD METHOD TO give the signal for the listener to stop listening and yield
         */


        System.out.println("Got a new packet! ");
        switch (data[0]) {
            case RingoProtocol.NEW_NODE:
                System.out.println("Got a new_node packet!");
                break;
            default:
                break;
        }
    }
}
