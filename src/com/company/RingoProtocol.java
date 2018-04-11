package com.company;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class RingoProtocol {
    private static DatagramSocket socket;
    public final static byte NEW_NODE = 0;
    public final static byte UPDATE_IP_TABLE = 1;
    public final static byte PING_HELLO = 3;
    public final static byte PING_RESPONSE = 4;
    public final static byte RTT_UPDATE = 5;
    public final static byte KEEP_ALIVEQ = 6;
    public final static byte KEEP_ALIVEACK = 7;

    /**
     * sends the new node packet using Ringo design specifications
     * @param name ip address to send to
     * @param port associated port number
     * @param local_port port number of the current ringo
     * @throws IOException
     */
    public static void sendNewNode(String name, int port, int local_port) throws IOException {
        socket = new DatagramSocket();
        byte[] buf = new byte[256];
        InetAddress address = InetAddress.getByName(name);
        buf[0] = NEW_NODE;
        byte[] loc_port_bytes = ByteBuffer.allocate(4).putInt(local_port).array();
        System.arraycopy(loc_port_bytes, 0, buf, 1, loc_port_bytes.length);
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        socket.send(packet);
        System.out.println("sent new node");
        socket.close();
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
        System.arraycopy(loc_port_bytes, 0, sendData, 1, loc_port_bytes.length);
        System.arraycopy(timebytes, 0, sendData, loc_port_bytes.length + 1, timebytes.length);
        sendData[0] = PING_HELLO;
        DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, port);
        try {
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
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

        byte[] sendData = new byte[loc_port_bytes.length  + data.length + 1];
        System.arraycopy(loc_port_bytes, 0, sendData, 1, loc_port_bytes.length);
        System.arraycopy(data, 0, sendData, loc_port_bytes.length + 1, data.length);
        sendData[0] = PING_RESPONSE;
        DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, port);
        try {
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
