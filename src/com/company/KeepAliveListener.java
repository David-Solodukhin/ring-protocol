package com.company;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

/**
 * Created by David on 4/5/2018.
 */
public class KeepAliveListener extends Thread{
    String ip = "";
    int rtt = 0;
    int port;
    DatagramSocket ringoSocket;
    public boolean rec = false;
    public boolean listening = true;
    public final Object lock = new Object();

    public void run() {
        boolean listening = true;
        try {
            ringoSocket = Ringo.listener_thread.ringoSocket;
        }catch(Exception e) {
            e.printStackTrace();
        }



        //TODO: instead of making a new socket for each keepalive thread, just reuse listener thread socket
        /*
            this thread will just use the listener thread's socket to send a packet with keepalive to a specific ip.
            a ringo will respond in the listener thread and send a response which will be captured in this ringo's
            listener thread.

            subtract system time here when waiting.

         */
        System.out.println("keep alive listener started");
        while(isListening()) //look through queue of received packets and parse them one by one(no concurrent receive)
        {
            try {

                byte[] alivePayload = new byte[1];
                alivePayload[0] = RingoProtocol.KEEP_ALIVEQ;
                long ts = System.currentTimeMillis();
                InetAddress address;
                if (ip.equals("/127.0.0.1")) {
                    address = InetAddress.getLocalHost();
                } else {
                    address = InetAddress.getByName(ip);
                }

                DatagramPacket toSend = new DatagramPacket(alivePayload, alivePayload.length, address, port);
                ringoSocket.send(toSend);
                
                boolean received = false;
                rec = false;
                //System.out.println("waiting for a response");
                while(!received)  {
                    synchronized (this) {
                        if (rec == true) {
                           // System.out.println("received an ack");
                            received = true;
                        } else {

                            long dts = System.currentTimeMillis() - ts;
                            //System.out.println(((dts) ) + " " + rtt);
                            if ((dts) > rtt + 900) { //play around with the tm val
                                System.out.println("OH NO A NODE IS PROBABLY DOWN!");
                                listening = false;
                                Ringo.listener_thread.removeRingo(ip);
                                return;
                            }
                        }


                    }

                }
                this.sleep(1); //play around with this value as well.




            }catch(IOException e) {
                e.printStackTrace();
            }catch(InterruptedException z) {
                z.printStackTrace();
                System.exit(1);
            }
        }
        System.out.println("this keep alive listener has ended");


    }
    public KeepAliveListener(String ip, int RTT) {
        String[] parts = ip.split(":");
        this.ip = parts[0];
        this.rtt = RTT;
        this.port = Integer.parseInt(parts[1]);
    }
    public boolean isListening() {
        synchronized (lock) {
            return listening;
        }
    }

}
