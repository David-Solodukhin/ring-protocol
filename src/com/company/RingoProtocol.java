package com.company;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

public class RingoProtocol {
    private static Socket socket;
    private final static String NEW_NODE = "0000";

    public static void sendNewNode(String name, int port) throws IOException {
        socket = new Socket(name, port);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        out.println(NEW_NODE);
    }
}
