package com.company;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.StringTokenizer;

public class Ringo {
    private static int local_port;
    public static String mode;
    private static int num_ringos;
    public static RttTable rtt_table; //since we have one ringo per physical main instance, these must be static
    public static IpTable ip_table;
    public static int[][] rtt_converted;
    public static String[] optimalRing;
    public static int poc_port;
    public static String poc_name = "";
    public static Listener listener_thread;
    public static ArrayList<byte[]> split_filedata;

    /**
     * Constructor for the ringo object
     * @param mode whether it is a sender, forwarder, or receiver
     * @param local_port port of this ringo
     * @param num_ringos number of ringos in the network
     */
    public Ringo(String mode_in, int local_port, int num_ringos, String poc_name2, int poc_port2) {
        mode = mode_in;
        this.local_port = local_port;
        this.num_ringos = num_ringos;
        rtt_table = new RttTable(num_ringos);
        ip_table = new IpTable(num_ringos, local_port);
        poc_name = poc_name2;
        poc_port = poc_port2;
    }

    public static ArrayList<String> getPath() {
        ArrayList<String> retval = new ArrayList<>();

        return retval;
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
        System.out.println("Active Threads: " + Thread.activeCount());
        startListener();
        contactPoC(poc_name, poc_port);
    }

    /**
     * Starts the listener thread for packet response
     */
    private void startListener() {
        listener_thread = new Listener(local_port, num_ringos);
        listener_thread.start();
    }

    /**
     * Sends new node packet to the point of contact, establishing this ringo in the network.
     * @param poc_name the ip address of the point of contact
     * @param poc_port the associated port number
     */
    private static void contactPoC(String poc_name, int poc_port) {
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
    public static void startUI() throws InterruptedException {

         System.out.println("Active Threads: " + Thread.activeCount());
        Scanner scan = new Scanner(System.in);
        String input = "";
        System.out.println("RINGO UI STARTED: ENTER A COMMAND ---------------------------------");
        while (!(input = scan.nextLine()).equals("stop")) {
            System.out.println("Active Threads: " + Thread.activeCount());
            if (input.equals("show-matrix")) {
                for(int i = 0; i < rtt_converted.length; i++) {
                    System.out.println(Arrays.toString(Ringo.rtt_table.test()[i]));
                }
            } else if (input.equals("show-ring")) {
                System.out.println(Arrays.toString(optimalRing));
            } else if (input.equals("disconnect")) {
                //implement disconnect of this ringo
                System.exit(1);
            } else if (input.contains("offline")) {
                int time = Integer.parseInt(input.split(" ")[1]);



                listener_thread.killAlive();
                //listener_thread.listening = false;
                listener_thread.join();
                System.out.println(Thread.activeCount());
                System.out.println("TEST");

                Thread.sleep((long)(time * 1000));

                listener_thread = new Listener(local_port, num_ringos);
                listener_thread.start();
                listener_thread.listening = true;



                //REBOOT
                System.out.println("REBOOTING...");
                rtt_table = new RttTable(num_ringos);
                ip_table = new IpTable(num_ringos, local_port);
                listener_thread.transitionExecuted = false;
                contactPoC(poc_name, poc_port);
                return;



            } else if (input.contains("send")) {
                if (!mode.equals("S")) {
                    System.out.println("Cannot send because you are not a sender node.");
                } else {
                    //handle send
                    StringTokenizer st = new StringTokenizer(input);
                    st.nextToken();
                    String filename = st.nextToken();
                    System.out.println("Handling send with " + filename);
                    try {
                        DatagramSocket socket = new DatagramSocket();
                        //TODO
                        //get neighbor ip and port does not matter which direction
                        String before = "";
                        String after = "";
                        String myip = "/" + InetAddress.getLocalHost().getHostAddress();
                        String myname = myip + ":" + local_port;
                        System.out.println(myname);
                        String ip_port_combo = "";
                        for (int i = 0; i < optimalRing.length; i++) {
                            if (optimalRing[i].equals(myname)) {
                                ip_port_combo = optimalRing[(i + 1) % optimalRing.length];
                            }
                        }
                        String[] ip_port_parts = ip_port_combo.split(":");
                        InetAddress destAddr;
                        if (ip_port_parts[0].contains("127.0.0.1")) {
                            destAddr = InetAddress.getLocalHost();
                        } else {
                            destAddr = InetAddress.getByName(ip_port_parts[0]);
                        }
                        int destPort = Integer.parseInt(ip_port_parts[1]);
                        System.out.println("Sending connect to :" + destPort);
                        //get my own ip and port
                        InetAddress my_addr = InetAddress.getLocalHost();
                        //send the packet and set a timer to see if we should try to resend
                        //TODO: implement resending with a thread that sleeps and then sends again
                        System.out.println("Sending connect packet");
                        RingoProtocol.sendConnect(socket, destAddr, destPort, my_addr.toString(), local_port);
                        //TODO: get the file and break it up into increments of 500 bytes each

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            }
        }
    }
}
