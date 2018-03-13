package com.company;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;

public class Ringo {
    private int local_port;
    private String mode;
    private int num_ringos;
    public static RttTable rtt_table; //since we have one ringo per physical main instance, these must be static
    public static IpTable ip_table;
    public static int[][] rtt_converted;
    public static String[] optimalRing;
    public Ringo(String mode, int local_port, int num_ringos) {
        this.mode = mode;
        this.local_port = local_port;
        this.num_ringos = num_ringos;
        rtt_table = new RttTable(num_ringos);
        ip_table = new IpTable(num_ringos, local_port);
    }

    public int getNum_ringos() {
        return num_ringos;
    }

    public void startup(String poc_name, int poc_port) {
        startListener();
        contactPoC(poc_name, poc_port);
    }

    private void startListener() {
        Listener listener_thread = new Listener(local_port, num_ringos);
        listener_thread.start();
    }

    private void contactPoC(String poc_name, int poc_port) {
        if (poc_name.equals("0") || poc_port == 0) {
            return;
        }
        try {
            RingoProtocol.sendNewNode(poc_name, poc_port, local_port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void waitForOptimumRing() {

    }

     public static void startUI() {
        Scanner scan = new Scanner(System.in);
        String input = "";
        while (!(input = scan.nextLine()).equals("stop")) {
            if (input.equals("show-matrix")) {
                for(int i = 0; i < rtt_converted.length; i++) {
                    System.out.println(Arrays.toString(rtt_converted[i]));
                }
            } else if (input.equals("show-ring")) {
                System.out.println(Arrays.toString(optimalRing));
            } else if (input.equals("disconnect")) {
                //implement disconnect of this ringo
            }
        }
    }
}
