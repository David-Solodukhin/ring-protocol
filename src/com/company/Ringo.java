package com.company;

import java.io.FileInputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Ringo {
    public static int local_port;
    public static String mode;
    private static int num_ringos;
    public static RttTable rtt_table; //since we have one ringo per physical main instance, these must be static
    public static IpTable ip_table;
    public static int[][] rtt_converted;
    public static String[] optimalRing;
    public static int poc_port;
    public static String poc_name = "";
    public static Listener listener_thread;
    public static ArrayList<byte[]> split_filedata = new ArrayList<>();
    public static boolean is_sending = false;
    public static final Object is_sending_lock = new Object();
    public static boolean uiStarted = false;
    public static int numActiveRingos = 0;
    public static InetAddress receiver_address;
    public static int receiver_port;
    public static String optimal_neighbor;
    public static String suboptimal_neighbor;
    public static HashMap<Long, Boolean> activeRequests = new HashMap<Long, Boolean>();
    public static boolean received_send_begin;
    public static final Object connect_lock = new Object();
    public static boolean received_filedata_0;
    public static boolean received_filedata_1;
    public static final Object received_filedata_lock = new Object();
    public static boolean received_term;
    public static final Object terminate_lock = new Object();
    public static boolean use_suboptimal_path = false;
    public static final Object path_switching_lock = new Object();
    public static boolean suicide_pact = false;


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
        System.out.println("POC NAME: " + poc_name);
        this.poc_name = poc_name;
        this.poc_port = poc_port;
        System.out.println("Active Threads: " + Thread.activeCount());
        startListener();

        //contactPoC(poc_name, poc_port);
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
            //RingoProtocol.sendNewNode(poc_name, poc_port, local_port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * starts the terminal user interface and parses user input from that interface
     */
    public static void startUI() throws InterruptedException {
        uiStarted = true;
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



                listener_thread.killAlive(true);
                //listener_thread.listening = false;
                listener_thread.join();
                System.out.println(Thread.activeCount());
                System.out.println("TEST");

                Thread.sleep((long)(time * 1000));

                listener_thread = new Listener(local_port, num_ringos);
                listener_thread.resurrected = true;
                optimalRing = null;
                uiStarted = false;
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
                        //get neighbor ip and port
                        System.out.println("Optimal neighbor = " + optimal_neighbor);
                        String[] dest_parts = optimal_neighbor.split(":");
                        String[] dest_parts_2 = suboptimal_neighbor.split(":");
                        dest_parts[0] = dest_parts[0].replace("/", "");
                        dest_parts_2[0] = dest_parts_2[0].replace("/", "");
                        System.out.println("connect packet destination: " + dest_parts[0] + " " + dest_parts[1]);
                        InetAddress destAddr = InetAddress.getByName(dest_parts[0]);
                        InetAddress destAddrSuboptimal = InetAddress.getByName(dest_parts_2[0]);
                        int destPort = Integer.parseInt(dest_parts[1]);
                        int destPortSuboptimal = Integer.parseInt(dest_parts_2[1]);
                        System.out.println("Sending connect to :" + destPort);
                        //get my own ip and port
                        String my_addr = InetAddress.getLocalHost().getHostAddress();
                        //send the packet and set a timer to see if we should try to resend
                        //set the sending flag to true
                        for (Map.Entry<String, IpTableEntry> entry: ip_table.getTable().entrySet()) {
                            byte[] empty_data = new byte[1];
                            empty_data[0] = 0;
                            RingoProtocol.reliableSend(listener_thread.ringoSocket, empty_data, entry.getValue().getAddress(), entry.getValue().getPort(), local_port, RingoProtocol.FILE_DONE, 5);
                        }
                        boolean rec = false;
                        while(!rec) {
                            synchronized (connect_lock) {
                                rec = received_send_begin;
                            }
                            if (!rec) {
                                System.out.println("Sending connect packet");
                                RingoProtocol.sendConnect(listener_thread.ringoSocket, destAddr, destPort, my_addr, local_port);
                                Thread.sleep(500);
                            } else {
                                break;
                            }
                        }
                        System.out.println("Finished reliable send of connect and got send-begin");
                        synchronized (connect_lock) {
                            received_send_begin = false;
                        }
                        // get the file and break it up into increments of 500 bytes each
                        Path path = Paths.get(filename);
                        byte[] file_byte_data = Files.readAllBytes(path);
                        byte[] current_split_bytes;
                        if (file_byte_data.length <= RingoProtocol.SEND_DATA_SIZE) {
                            split_filedata.add(file_byte_data);
                        } else {
                            current_split_bytes = new byte[RingoProtocol.SEND_DATA_SIZE];
                            for (int i = 0; i < file_byte_data.length; i++) {
                                if (i % current_split_bytes.length == 0 && i != 0) {
                                    split_filedata.add(current_split_bytes);
                                    if (file_byte_data.length - current_split_bytes.length < RingoProtocol.SEND_DATA_SIZE) {
                                        current_split_bytes = new byte[file_byte_data.length - current_split_bytes.length];
                                    } else {
                                        current_split_bytes = new byte[RingoProtocol.SEND_DATA_SIZE];
                                    }
                                }
                                current_split_bytes[i % current_split_bytes.length] = file_byte_data[i];
                            }
                            split_filedata.add(current_split_bytes);
                        }
                        int seq_num = 0;
                        //These can be switched in order to change between optimal and suboptimal path.
                        InetAddress current_path_ip = destAddr;
                        int current_path_port = destPort;
                        for (byte[] packet_bytes: split_filedata) {
                            //perform reliable file send and wait for the ack
                            //TODO: use keep alive detection to check if a node on our path goes down and then react to it by switching the path

                            if (seq_num == 0) {
                                //wait for a seq num of 0 to send and receive the ack
                                boolean recv0 = false;
                                while (!recv0) {
                                    synchronized (received_filedata_lock) {
                                        recv0 = received_filedata_0;
                                    }
                                    synchronized (path_switching_lock) {
                                        if (use_suboptimal_path) {
                                            current_path_ip = destAddrSuboptimal;
                                            current_path_port = destPortSuboptimal;
                                        } else {
                                            current_path_ip = destAddr;
                                            current_path_port = destPort;
                                        }
                                    }
                                    if (!recv0) {
                                        System.out.println("Trying to send file data with seq num of 0");
                                        System.out.println("Threads: " + Thread.activeCount());
                                        RingoProtocol.sendData(listener_thread.ringoSocket, current_path_ip, current_path_port, 0, packet_bytes);
                                        Thread.sleep(500);
                                    }
                                }
                                synchronized (received_filedata_lock) {
                                    received_filedata_0 = false;
                                }
                            } else {
                                boolean recv1 = false;
                                while (!recv1) {
                                    synchronized (received_filedata_lock) {
                                        recv1 = received_filedata_1;
                                    }
                                    synchronized (path_switching_lock) {
                                        if (use_suboptimal_path) {
                                            current_path_ip = destAddrSuboptimal;
                                            current_path_port = destPortSuboptimal;
                                        } else {
                                            current_path_ip = destAddr;
                                            current_path_port = destPort;
                                        }
                                    }
                                    if (!recv1) {
                                        System.out.println("Trying to send file data with seq num of 1");
                                        RingoProtocol.sendData(listener_thread.ringoSocket, current_path_ip, current_path_port, 1, packet_bytes);
                                        Thread.sleep(500);
                                    }
                                }
                                synchronized (received_filedata_lock) {
                                    received_filedata_1 = false;
                                }
                            }


                            //closing the loop to flip between sending 0 and 1 as the seq_num
                            if (seq_num == 0) {
                                seq_num = 1;
                            } else {
                                seq_num = 0;
                            }
                        }

                        //TODO: terminate the connection
                        System.out.println("Terminating the connection");
                        synchronized (is_sending_lock) {
                            is_sending = false;
                        }
                        synchronized (terminate_lock) {
                            received_term = false;
                        }
                        boolean recvterm = false;
                        while (!recvterm) {
                            synchronized (terminate_lock) {
                                recvterm = received_term;
                            }
                            if (!recvterm) {
                                System.out.println("Trying to send terminate");
                                RingoProtocol.sendTerminate(listener_thread.ringoSocket, current_path_ip, current_path_port);
                                Thread.sleep(500);
                            }
                        }
                        synchronized (terminate_lock) {
                            received_term = false;
                        }
                        //clear the packet buffer
                        split_filedata.clear();

                        //if we used suboptimal path recalculate the optimal ring and set the bool back to use optimal path
                        synchronized (path_switching_lock) {
                            if (use_suboptimal_path) {
                                use_suboptimal_path = false;
                                listener_thread.formOptimalRing();
                            }
                        }
                        for (Map.Entry<String, IpTableEntry> entry: ip_table.getTable().entrySet()) {
                            byte[] empty_data = new byte[1];
                            empty_data[0] = 1;
                            RingoProtocol.reliableSend(listener_thread.ringoSocket, empty_data, entry.getValue().getAddress(), entry.getValue().getPort(), local_port, RingoProtocol.FILE_DONE, 5);
                        }



                    } catch (NoSuchFileException e) {
                        System.out.println("Could not open your file...does it really exist?");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            }
        }
    }

    public static void kill_urself_loser() {
        try {
            long time = 1;
            listener_thread.killAlive(true);
            //listener_thread.listening = false;
            listener_thread.join();
            System.out.println(Thread.activeCount());
            System.out.println("TEST");

            Thread.sleep((long) (time * 1000));

            listener_thread = new Listener(local_port, num_ringos);
            listener_thread.resurrected = true; //this listener will send a new node on listener start
            optimalRing = null;
            uiStarted = false;
            listener_thread.start();
            listener_thread.listening = true;


            //REBOOT
            System.out.println("REBOOTING...");

            rtt_table = new RttTable(num_ringos);
            ip_table = new IpTable(num_ringos, local_port);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
