package com.company;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class RingoProtocol {
    private static DatagramSocket socket;
    public final static byte NEW_NODE = 0;
    public final static byte UPDATE_IP_TABLE = 1;
    public final static byte PING_HELLO = 3;
    public final static byte PING_RESPONSE = 4;
    public final static byte RTT_UPDATE = 5;
    public final static byte KEEP_ALIVEQ = 6;
    public final static byte KEEP_ALIVEACK = 7;
    public final static byte CONNECT = 8;
    public final static byte SEND_BEGIN = 9;
    public final static byte FILE_DATA = 10;
    public final static byte ACK = 11;
    public final static byte TERMINATE = 12;
    public final static byte TERMINATED = 13;
    public final static byte RTT_REQUEST = 14;
    public final static byte I_AM_RECEIVER = 15;
    public final static int SEND_DATA_SIZE = 500;


    public final static byte RELIABLE_Q = 126;
    public final static byte RELIABLE_A = 127;


    public final static Object activeRequestsTableLock = new Object();
    /**
     * NOTE: the destination is discovered by the response to this packet
     * @param socket
     * @param address
     * @param port
     * @param senderIp
     * @param senderPort
     */

    public static void sendImReceiver(DatagramSocket socket, InetAddress address, int port, int my_port) {
        byte[] buf = new byte[1 + Integer.BYTES];
        buf[0] = I_AM_RECEIVER;
        byte[] my_port_bytes = ByteBuffer.allocate(Integer.BYTES).putInt(my_port).array();
        System.arraycopy(my_port_bytes, 0, buf, 1, my_port_bytes.length);
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        try {
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendConnect(DatagramSocket socket, InetAddress address, int port, String senderIp, int senderPort)  {
        byte[] buf = new byte[256];
        buf[0] = CONNECT;
        byte[] sender_ip_bytes = senderIp.getBytes();
        byte[] sender_port_bytes = ByteBuffer.allocate(Integer.BYTES).putInt(senderPort).array();
        int tmp_accumulator = 1;
        System.arraycopy(sender_ip_bytes, 0, buf, tmp_accumulator, sender_ip_bytes.length);
        tmp_accumulator += sender_ip_bytes.length;
        System.arraycopy(sender_port_bytes, 0, buf, tmp_accumulator, sender_port_bytes.length);
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        try {
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void sendBegin(DatagramSocket socket, InetAddress address, int port, String senderIp, int senderPort, String destIp, int destPort) {
        byte[] buf = new byte[256];
        buf[0] = SEND_BEGIN;
        byte[] sender_ip_bytes = senderIp.getBytes();
        byte[] sender_port_bytes = ByteBuffer.allocate(Integer.BYTES).putInt(senderPort).array();
        byte[] dest_ip_bytes = destIp.getBytes();
        byte[] dest_port_bytes = ByteBuffer.allocate(Integer.BYTES).putInt(destPort).array();
        int tmp_accumulator = 1;
        System.arraycopy(sender_ip_bytes, 0, buf, tmp_accumulator, sender_ip_bytes.length);
        tmp_accumulator += sender_ip_bytes.length;
        System.arraycopy(sender_port_bytes, 0, buf, tmp_accumulator, sender_port_bytes.length);
        tmp_accumulator += sender_port_bytes.length;
        System.arraycopy(dest_ip_bytes, 0, buf, tmp_accumulator, dest_ip_bytes.length);
        tmp_accumulator += dest_ip_bytes.length;
        System.arraycopy(dest_port_bytes, 0, buf, tmp_accumulator, dest_port_bytes.length);
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        try {
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * This only sends 502 bytes of data
     */
    public static void sendData(DatagramSocket socket, InetAddress address, int port, String destIp, int destPort, int seqNum, byte[] data) {
        if (data.length > 502) {
            System.out.println("Failed to send a packet because its length was too large in a file data transfer");
            return;
        }
        //only allocate as much as we need to make receiving easy
        byte[] buf = new byte[10 + data.length];
        buf[0] = FILE_DATA;
        byte[] dest_ip_bytes = destIp.getBytes();
        byte[] dest_port_bytes = ByteBuffer.allocate(Integer.BYTES).putInt(destPort).array();
        byte[] seq_num_bytes = ByteBuffer.allocate(Integer.BYTES).putInt(seqNum).array();
        int tmp_accumulator = 1;
        System.arraycopy(dest_ip_bytes, 0, buf, tmp_accumulator, dest_ip_bytes.length);
        tmp_accumulator += dest_ip_bytes.length;
        System.arraycopy(dest_port_bytes, 0, buf, tmp_accumulator, dest_port_bytes.length);
        tmp_accumulator += dest_port_bytes.length;
        System.arraycopy(seq_num_bytes, 0, buf, tmp_accumulator, seq_num_bytes.length);
        tmp_accumulator += seq_num_bytes.length;
        System.arraycopy(data, 0, buf, tmp_accumulator, data.length);
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        try {
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void sendAck(DatagramSocket socket, InetAddress address, int port, String destIp, int destPort, int seqNum) {
        //only allocate as much as we need to make receiving easy
        byte[] buf = new byte[10];
        buf[0] = ACK;
        byte[] dest_ip_bytes = destIp.getBytes();
        byte[] dest_port_bytes = ByteBuffer.allocate(Integer.BYTES).putInt(destPort).array();
        byte[] seq_num_bytes = ByteBuffer.allocate(Integer.BYTES).putInt(seqNum).array();
        int tmp_accumulator = 1;
        System.arraycopy(dest_ip_bytes, 0, buf, tmp_accumulator, dest_ip_bytes.length);
        tmp_accumulator += dest_ip_bytes.length;
        System.arraycopy(dest_port_bytes, 0, buf, tmp_accumulator, dest_port_bytes.length);
        tmp_accumulator += dest_port_bytes.length;
        System.arraycopy(seq_num_bytes, 0, buf, tmp_accumulator, seq_num_bytes.length);
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        try {
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendTerminate(DatagramSocket socket, InetAddress address, int port, String destIp, int destPort) {
        //only allocate as much as we need to make receiving easy
        byte[] buf = new byte[10];
        buf[0] = TERMINATE;
        byte[] dest_ip_bytes = destIp.getBytes();
        byte[] dest_port_bytes = ByteBuffer.allocate(Integer.BYTES).putInt(destPort).array();
        int tmp_accumulator = 1;
        System.arraycopy(dest_ip_bytes, 0, buf, tmp_accumulator, dest_ip_bytes.length);
        tmp_accumulator += dest_ip_bytes.length;
        System.arraycopy(dest_port_bytes, 0, buf, tmp_accumulator, dest_port_bytes.length);
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        try {
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendTerminateAck(DatagramSocket socket, InetAddress address, int port, String destIp, int destPort) {
        //only allocate as much as we need to make receiving easy
        byte[] buf = new byte[10];
        buf[0] = TERMINATED;
        byte[] dest_ip_bytes = destIp.getBytes();
        byte[] dest_port_bytes = ByteBuffer.allocate(Integer.BYTES).putInt(destPort).array();
        int tmp_accumulator = 1;
        System.arraycopy(dest_ip_bytes, 0, buf, tmp_accumulator, dest_ip_bytes.length);
        tmp_accumulator += dest_ip_bytes.length;
        System.arraycopy(dest_port_bytes, 0, buf, tmp_accumulator, dest_port_bytes.length);
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        try {
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * sends the new node packet using Ringo design specifications
     * @param name ip address to send to
     * @param port associated port number
     * @param local_port port number of the current ringo
     * @throws IOException
     */
    public static void sendNewNode(DatagramSocket socket ,String name, int port, int local_port) throws IOException {
        //socket = new DatagramSocket();
        if (Ringo.poc_name.equals("0") || Ringo.poc_port == 0) {
            return;
        }
        byte[] buf = new byte[256];
        InetAddress address = InetAddress.getByName(name);
        buf[0] = NEW_NODE;
        System.out.println("PORT: "+port);
        byte[] loc_port_bytes = ByteBuffer.allocate(4).putInt(local_port).array();



        //System.arraycopy(loc_port_bytes, 0, buf, 1, loc_port_bytes.length);
        //DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        //socket.send(packet);
        reliableSend(socket, loc_port_bytes, address, port, local_port, NEW_NODE, 5);

        System.out.println("sent new node");
        //socket.close();
    }

    /**
     * Sends an UpdateIp packet with the serialized table.
     * @param socket socket to use to send the packet
     * @param address destination address
     * @param port destination port
     * @param data serialized table
     */
    public void sendUpdateIpTable(DatagramSocket socket, InetAddress address, int port, byte[] data) {
        byte[] sendData = new byte[data.length + 1];
        System.arraycopy(data, 0, sendData, 1, data.length);
        sendData[0] = UPDATE_IP_TABLE;
        //System.out.println(address.toString());
        //System.out.println("Sending updateIP");
        DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, port);
        try {
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends the first part of the RTT calculation protocol with the timestamp.
     * @param socket socket to use to send the packet
     * @param address destination address
     * @param port destination port
     * @param time milliseconds for supposed current time (can be anything though)
     * @param local_port port of current ringo
     */
    public void sendPingHello(DatagramSocket socket, InetAddress address, int port, long time, int local_port) {
        byte[] timebytes = ByteBuffer.allocate(Long.BYTES).putLong(time).array();
        byte[] loc_port_bytes = ByteBuffer.allocate(Integer.BYTES).putInt(local_port).array();
        byte[] sendData = new byte[loc_port_bytes.length + timebytes.length + 1];


        System.arraycopy(loc_port_bytes, 0, sendData, 0, loc_port_bytes.length);
        System.arraycopy(timebytes, 0, sendData, loc_port_bytes.length, timebytes.length);
        //reliableSend(socket, sendData, address, port, local_port, PING_HELLO, 5);
        reliableSendTime(socket, sendData, address, port, local_port, PING_HELLO, 8);
        //sendData[0] = PING_HELLO;
        /*DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, port);
        try {
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace(); //have to fix this since reliable send resends same data which is why rtt is so damn high
        }*/
    }

    /**
     * second part of the RTT calculation protocol. Sends the response packet with the data received from the ping hello
     * @param socket socket to use to send the packet
     * @param address destination address
     * @param port destination port
     * @param data data containing the time from the ping hello
     * @param local_port port of the current ringo
     */
    public void sendPingResponse(DatagramSocket socket, InetAddress address, int port, byte[] data, int local_port) {
        byte[] loc_port_bytes = ByteBuffer.allocate(Integer.BYTES).putInt(local_port).array();

        byte[] sendData = new byte[loc_port_bytes.length  + data.length ];
        System.arraycopy(loc_port_bytes, 0, sendData, 0, loc_port_bytes.length);
        System.arraycopy(data, 0, sendData, loc_port_bytes.length, data.length);
        //sendData[0] = PING_RESPONSE;
        reliableSend(socket, sendData, address, port, local_port, PING_RESPONSE, 8);

        /*DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, port);
        try {
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }*/

    }

    public static void sendUpdateRTTTable(DatagramSocket socket, InetAddress address, int port, byte[] data) {
        byte[] sendData = new byte[data.length + 1];
        System.arraycopy(data, 0, sendData, 1, data.length);
        sendData[0] = RTT_UPDATE;
        //System.out.println(address.toString());
        //System.out.println("Sending updateIP");
        DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, port);
        try {
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendRTTRequest(DatagramSocket socket, InetAddress address, int port, long time, int local_port) {
        System.out.println("what the fuck");
        byte[] timebytes = ByteBuffer.allocate(Long.BYTES).putLong(time).array();
        byte[] loc_port_bytes = ByteBuffer.allocate(Integer.BYTES).putInt(local_port).array();
        byte[] sendData = new byte[loc_port_bytes.length + timebytes.length + 1];
        System.arraycopy(loc_port_bytes, 0, sendData, 1, loc_port_bytes.length);
        System.arraycopy(timebytes, 0, sendData, loc_port_bytes.length + 1, timebytes.length);
        sendData[0] = RTT_REQUEST;
        DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, port);
        try {
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /*
    Blocking reliable send
     */
    public static int reliableSend(DatagramSocket socket, byte[] data, InetAddress address, int port, int local_port, byte header, int tries) {
        //long timestamp = System.currentTimeMillis();



        byte[] tmp = new byte[data.length + 2];
        tmp[0] = RELIABLE_Q;
        tmp[1] = header;
        System.arraycopy(data, 0, tmp, 2, data.length);

        DatagramPacket packet = new DatagramPacket(tmp, tmp.length, address, port);


        data = Listener.trim(data); //we don't need this array to be accurate anymore so we trim
        synchronized (activeRequestsTableLock) {
            Ringo.activeRequests.put((long)Arrays.hashCode(data), false); //add request to map
            //System.out.println("PUTTING: " + Arrays.toString(data));
            //System.out.println("HASHCODE: " + (long)Arrays.hashCode(data));
        }

        boolean rec = false;
        while(!rec && tries > 0) {
            System.out.println("I'm TRYING!" + port);
            synchronized (activeRequestsTableLock) {
                if (Ringo.activeRequests.get((long)Arrays.hashCode(data)) == null) {
                    System.out.println("reliable send failed: no such request");
                    return -1;
                }

                rec = Ringo.activeRequests.get((long)Arrays.hashCode(data)); //check if request fullfilled
            }
            if (rec) {
                break;
            }

            try {
                socket.send(packet);
                tries--;
                Thread.sleep(950); //wait some time for response
            }catch (Exception e) {
                e.printStackTrace();
            }





        }
        if (tries == 0) {
            System.out.println("failed to deliver in time to a node: is it down?");
            return -1;

        }


        synchronized (activeRequestsTableLock) {
            Ringo.activeRequests.put((long)Arrays.hashCode(data), false); //i feel like this is better than remove in case of multiple requests for same data
        }
        return 1;
    }



    public static int reliableSendTime(DatagramSocket socket, byte[] data, InetAddress address, int port, int local_port, byte header, int tries) {
        long timestamp = System.currentTimeMillis();



        byte[] tmp = new byte[data.length + 2]; //the data is useless
        tmp[0] = RELIABLE_Q;
        tmp[1] = header;
        System.arraycopy(data, 0, tmp, 2, data.length);

        DatagramPacket packet = new DatagramPacket(tmp, tmp.length, address, port);


        data = Listener.trim(data); //we don't need this array to be accurate anymore so we trim
        synchronized (activeRequestsTableLock) {
            Ringo.activeRequests.put((long)Arrays.hashCode(data), false); //add request to map
            //System.out.println("PUTTING: " + Arrays.toString(data));
            //System.out.println("HASHCODE: " + (long)Arrays.hashCode(data));
        }

        boolean rec = false;
        while(!rec && tries > 0) {
            System.out.println("I'm TRYING!" + port);
            synchronized (activeRequestsTableLock) {
                if (Ringo.activeRequests.get((long)Arrays.hashCode(data)) == null) {
                    System.out.println("reliable send failed: no such request");
                    return -1;
                }

                rec = Ringo.activeRequests.get((long)Arrays.hashCode(data)); //check if request fullfilled
            }
            if (rec) {


                break;
            }

            try {
                timestamp = System.currentTimeMillis();
                synchronized (Ringo.listener_thread.syncTimeStampLock) {
                    //System.out.println("BEFORE PINGING TIMESTAMP: " + timestamp);
                    Ringo.listener_thread.syncTimeStamp = timestamp; //set the updated timestamp for sent
                }
                socket.send(packet);
                tries--;
                Thread.sleep(150); //wait some time for response
            }catch (Exception e) {
                e.printStackTrace();
            }





        }
        if (tries == 0) {
            System.out.println("failed to deliver in time to a node: is it down?");
            return -1;

        }
        synchronized (activeRequestsTableLock) {
            Ringo.activeRequests.put((long)Arrays.hashCode(data), false); //i feel like this is better than remove in case of multiple requests for same data
        }




        return 1;
    }

}
