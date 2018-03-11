package com.company;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

public class RingoProtocol {
    private static DatagramSocket socket;
    public final static String NEW_NODE = "0000";

    public static void sendNewNode(String name, int port) throws IOException {
        socket = new DatagramSocket();
        byte[] buf = new byte[256];
        InetAddress address = InetAddress.getByName(name);
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        buf = NEW_NODE.getBytes();
        socket.send(packet);
    }
}
