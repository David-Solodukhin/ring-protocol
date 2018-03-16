package com.company;

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


    /**
     * Constructor for the ringo object
     * @param mode whether it is a sender, forwarder, or receiver
     * @param local_port port of this ringo
     * @param num_ringos number of ringos in the network
     */
    public Ringo(String mode, int local_port, int num_ringos) {
        this.mode = mode;
        this.local_port = local_port;
        this.num_ringos = num_ringos;
        rtt_table = new RttTable(num_ringos);
        ip_table = new IpTable(num_ringos, local_port);
    }

    /**
     * getter for the number of ringos
     * @return number of ringos in the network
     */
    public int getNum_ringos() {
        return num_ringos;
    }

    /**
     * function that starts threading and network initialization
     * @param poc_name ip address of the point of contact
     * @param poc_port port number of the point of contact
     */
    public void startup(String poc_name, int poc_port) {
        System.out.println(Thread.activeCount());
        startListener();
        contactPoC(poc_name, poc_port);
    }

    /**
     * Starts the listener thread for packet response
     */
    private void startListener() {
        Listener listener_thread = new Listener(local_port, num_ringos);
        listener_thread.start();
    }

    /**
     * Sends new node packet to the point of contact, establishing this ringo in the network.
     * @param poc_name the ip address of the point of contact
     * @param poc_port the associated port number
     */
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

    /**
     * starts the terminal user interface and parses user input from that interface
     */
    public static void startUI() {

         System.out.println(Thread.activeCount());
        Scanner scan = new Scanner(System.in);
        String input = "";
        System.out.println("RINGO UI STARTED: ENTER A COMMAND ---------------------------------");
        while (!(input = scan.nextLine()).equals("stop")) {
            if (input.equals("show-matrix")) {
                for(int i = 0; i < rtt_converted.length; i++) {
                    System.out.println(Arrays.toString(Ringo.rtt_table.test()[i]));
                }
            } else if (input.equals("show-ring")) {
                System.out.println(Arrays.toString(optimalRing));
            } else if (input.equals("disconnect")) {
                //implement disconnect of this ringo
                System.exit(1);
            }
        }
    }
}
