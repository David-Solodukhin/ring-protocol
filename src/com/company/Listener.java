package com.company;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static com.company.Ringo.*;

/*
important questions 4 Daniel:
MOST IMPORTANT//////////////////////////////

-figure out what should happen when a node goes down for some time, but comes back on while a file still hasn't reached the receiver.
in that case, how do we let all nodes including that one know that it shouldn't be in the opt ring at that moment.

-what should actually happen when a node goes down. What happens right when a node reboots? How are the rtt tables and ip tables for all other nodes modified?
-right now, the only way a node obtains a full rtt_table is when a node finishes building its setup_vector(base vector) it will flood rtt with it. since every node will create it's
setup_vector and flood it, every node will get the complete picture. However, when one node goes down and comes back on, none of the other nodes will flood again since their setup_vector
was constructed in the beginning. !!!!!!!!!THIS IS THE REASON WHY CURRENTLY WHEN A NODE GOES DOWN AND THEN COMES BACK ON, DOESN'T PROCEED TO STARTUI!!!!!!!!!
/////////////////////////////////////////////
MISC FILE SENDING
-how large is the max file size?
-when should opt ring be recalculated?
-how will file be split up?
-how will acks work between ringos for file transfer? 1, 0, 1, 0, 1, 0
-figure out what happens if an ack never makes it.
-how do you handle duplicate acks?


 */
public class Listener extends Thread{
    public DatagramSocket ringoSocket;
    private int port = 0;
    private int currentShortestRingLength = 0;
    public boolean listening = true;
    private final Object rtt_lock = new Object();
    private final Object ip_lock = new Object();
    public final Object alive_lock = new Object();
    private RttVector setupVector;
    boolean startRtt = false;
    private int numringos;
    private int num_files_transferred = 0;


    public long syncTimeStamp = 0;
    public static final Object syncTimeStampLock = new Object();

    public HashSet<Thread> actives = new HashSet<Thread>();
    public HashMap<String, KeepAliveListener> keepalives = new HashMap<String, KeepAliveListener>();

    private boolean added_setupVector = false;
    public boolean transitionExecuted = false;
    public boolean resurrected = false;
    DatagramPacket packetZ = null;

    int activeThreads = 0;
    boolean inTransit = false;
    /**
     * Constructor for the listener thread that spawns threads to handle incoming packets
     * @param port the ringos port number
     * @param numringos the total number of ringos in the network
     */
    public Listener(int port, int numringos) {
        this.port = port;
        this.numringos = numringos;
        try {
            this.setupVector = new RttVector(0, InetAddress.getByName(InetAddress.getLocalHost().getHostAddress()).toString() + ":" + port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Code that is run when the listener thread is started. This will look for incoming packets and spawn new threads
     * for them which will then react to the type of packet in a different function.
     */
    public void run() {
        try {
            ringoSocket = new DatagramSocket(port);
            System.out.println(Ringo.poc_name+ Ringo.poc_port);



            Thread t1 = new Thread() {
                boolean running = true;
                public void run() {
                    try {
                        RingoProtocol.sendNewNode(ringoSocket, Ringo.poc_name, Ringo.poc_port, port);
                    }catch(Exception e) {
                        e.printStackTrace();
                    }


                }};
            t1.start();



        } catch (Exception e) {
            e.printStackTrace();
        }

        //System.out.println("Active threads: " + Thread.activeCount());
        while(listening) //look through queue of received packets and parse them one by one(no concurrent receive)
        {
            try {
                byte[] receive = new byte[7000]; //holy shit fuck us amirite??
                DatagramPacket receivePacket = new DatagramPacket(receive, receive.length);
                //System.out.println("received something");
                //listener thread blocks on this until something is received
                ringoSocket.receive(receivePacket);
                synchronized (ip_lock) {
                    activeThreads++;
                }


                //TODO: make a worker class for this thread
                Thread t1 = new Thread() {
                    boolean running = true;
                    public void run() {

                            //System.out.println("------------------"+receivePacket.getPort()+"------------------------");
                            //System.out.println("Active Threads: " + Thread.activeCount());
                        /*
                        //either first byte is part of the normal protocol in which case it won't be reliable_X, or it will be. no data mismanaged


                         */
                        byte[] data;
                        if (receivePacket.getData()[0] == RingoProtocol.RELIABLE_Q) { //[RELIABLE_X][PROTOCOL_HEADER_X][data]
                            System.out.println("Found a reliable packet...sending an ack");
                            data = new byte[receivePacket.getData().length-1];
                            System.arraycopy(receivePacket.getData(),1, data, 0, data.length); //for cases
                            //System.out.println("NEED TO SEND A RELIABLE ACK");
                            sendAckForReliable(data, receivePacket);


                        } else if (receivePacket.getData()[0] == RingoProtocol.RELIABLE_A) { //any packet that has reliable_a has only 1 header byte
                            System.out.println("Found an ack to my reliable packet");
                            data = new byte[receivePacket.getData().length - 1];
                            System.arraycopy(receivePacket.getData(), 1, data, 0, data.length);
                            handleReliableAck(data);
                            return;
                        } else {
                            data = receivePacket.getData();
                        }

                         //we want the protocol byte as well
                            parsePacket(data, receivePacket.getAddress(), receivePacket.getPort(), receivePacket);


                        //System.out.println("thread finished");

                        synchronized (ip_lock) {
                            activeThreads--;

                            actives.remove(this);

                        }

                        return;
                    }
                };

                t1.start();
                actives.add(t1);



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


        System.out.println("Listener run complete");
        System.out.println(Thread.activeCount());
        /*while (actives.iterator().hasNext()) {
            try {
                System.out.println("here!!!!");
                actives.iterator().next().join();
                actives.iterator().remove();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }*/

        /*


        u should add any listener cleanup code here cause this is where listener dies once listening is set to false;
         */
        ringoSocket.close();

        return;
    }

    private void handleReliableAck(byte[] data) { //data should contain no header bytes
        data = trim(data);
        //System.out.println("RECIEVED: " + Arrays.toString(data));
        synchronized (RingoProtocol.activeRequestsTableLock) {
            if (Ringo.activeRequests.get((long)Arrays.hashCode(data)) != null) {
                Ringo.activeRequests.put((long)Arrays.hashCode(data), true);
            } else {
                System.out.println("there was no request to fullfill!");
                System.out.println("got ack for : " + Arrays.toString(data));

                System.exit(1);
                return;
            }

        }
    }

    private void sendAckForReliable(byte[] data, DatagramPacket pack) { //assuming there is a header byte already in data
        try {
            byte[] tmp = new byte[data.length];
            System.arraycopy(data, 0, tmp, 0, data.length); //copy since orig could be used
            tmp[0] = RingoProtocol.RELIABLE_A; //replace header byte with reliable ack
            //System.out.println("SENDING ACK FOR: " + Arrays.toString(data));
            DatagramPacket packet = new DatagramPacket(tmp, tmp.length, pack.getAddress(), pack.getPort());
            ringoSocket.send(packet);
        } catch(Exception e) {
            e.printStackTrace();
        }


    }
    static byte[] trim(byte[] bytes)
    {
        int i = bytes.length - 1;
        while (i >= 0 && bytes[i] == 0)
        {
            --i;
        }

        return Arrays.copyOf(bytes, i + 1);
    }
    private void requestRTTS() {
        //System.out.println("HERE");
        for (Map.Entry<String, IpTableEntry> entry : ip_table.getTable().entrySet()) {
            try {
                if (!entry.getKey().equals(InetAddress.getByName(InetAddress.getLocalHost().getHostAddress()).toString() + this.port)) {

                    RingoProtocol.sendRTTRequest(ringoSocket, entry.getValue().getAddress(), entry.getValue().getPort(), 1020, this.port);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * Parses packet data and reacts in a thread safe manner. Then checks for completion of different stages in the
     * initialization process for the network.
     * @param data bytes taken from the data of the UDP packet
     * @param IPAddress address of the packet's original sender
     */
    public void parsePacket(byte[] data, InetAddress IPAddress, int Bport, DatagramPacket packetZ) {
        //InetAddress IPAddress = packet.getAddress();
        //int port = packet.getPort();
        //byte[] data = packet.getData();
        /*
        1. parse packet header from payload bytes
        2. identify what needs to be done, call that particular method with a new thread.
        3. mark all data structures used by that thread as synchronized(use either a sync block, semaphore or intrinsic lock)
        4. if needed, send a response to whoever and then return/exit thread.

        NOTE: IT IS THE JOB OF THE RTT RESPONSE THREAD METHOD TO give the signal for the listener to stop listening and yield
         */

        switch (data[0]) {
            case RingoProtocol.NEW_NODE:
                System.out.println("Got a new_node packet!");
                //check if a file send is in progress
                if (is_sending) {
                    //send a fuck off packet
                    byte[] empty_data = new byte[2];
                    RingoProtocol.reliableSend(ringoSocket, empty_data, IPAddress, Bport, Ringo.local_port, RingoProtocol.FUCK_OFF, 6);
                    suicide_pact = true;
                    return;
                }
                synchronized (ip_lock) {

                    transitionExecuted = false;
                    Ringo.numActiveRingos++; //for resurrected node
                    startRtt = actAsPoc(IPAddress, data);
                }

                break;

            case RingoProtocol.UPDATE_IP_TABLE:
                System.out.println("Got updateIp table");
                synchronized (ip_lock) {
                   // if (!startRtt) { //Ringo.optimalRing == null &&
                        startRtt = handleUpdateIp(data);
                    //}

                }
                break;
            case RingoProtocol.PING_HELLO:
                System.out.println("Got ping hello!");
               // Ringo.ip_table.printTable();
                sendRttResponse(IPAddress, data);
                break;
            case RingoProtocol.PING_RESPONSE:
                System.out.println("Got ping response!");
               // if (Ringo.optimalRing == null) {
                    synchronized (rtt_lock) {
                        handlePingResponse(IPAddress, data);
                 //   }

                }
               // System.out.println("Finished Table:");

                break;
            case RingoProtocol.KEEP_ALIVEACK:
                //System.out.println("got alive ack!");
                synchronized (keepalives.get(IPAddress.toString() + ":" + Bport)) {
                    try {
                        keepalives.get(IPAddress.toString() + ":" + Bport).rec = true;
                    }catch(NullPointerException e) {
                        e.printStackTrace();
                        System.exit(1);
                    }

                }
                return;
            case RingoProtocol.KILL_YOURSELF:
                Ringo.kill_urself_loser();
                return;
            case RingoProtocol.KEEP_ALIVEQ:
                //System.out.println("got alive Q!");

                    sendAliveAck(IPAddress, Bport);
            return;
            case RingoProtocol.RTT_REQUEST:
                rtt_requestResponse(IPAddress, Bport);
                return;
            case RingoProtocol.RTT_UPDATE:
                System.out.println("Got an RTT table update");


                byte[] payload = new byte[data.length - 1];
                System.arraycopy(data, 1, payload, 0, data.length - 1); // -1 because header is removed
                synchronized (rtt_lock) {



                    updateRTT(payload);
                    System.out.println("RTT Table updated");
                    //floodRTT();


                }

                break;
             case RingoProtocol.CONNECT:
                 System.out.println("Got connect");
                 //TODO
                 //handle a connection
                 //check if this node is a receiver
                 boolean is_receiver = false;
                 if (Ringo.mode.equals("R")) {
                     is_receiver = true;
                 }
                 //if so send a send-begin packet
                 if (is_receiver) {
                     System.out.println("Trying to send begin packet");
                     try {
                         DatagramSocket socket = new DatagramSocket();
                         byte[] data_without_header = new byte[data.length - 1];
                         System.arraycopy(data, 1, data_without_header, 0, data.length - 1);
                         ByteArrayInputStream in = new ByteArrayInputStream(data_without_header);
                         byte[] sender_ip_bytes = new byte[100];
                         byte[] sender_port_bytes = new byte[Integer.BYTES];
                         try {
                             in.read(sender_port_bytes);
                             in.read(sender_ip_bytes);
                         } catch (Exception e) {
                             e.printStackTrace();
                         }
                         int sender_port = ByteBuffer.wrap(sender_port_bytes).getInt();
                         String sender_ip = new String(sender_ip_bytes);
                         String my_addr = InetAddress.getLocalHost().getHostAddress();
                         RingoProtocol.sendBegin(ringoSocket, IPAddress, Bport, sender_ip, sender_port, my_addr, Ringo.local_port);
                     } catch (Exception e) {
                         e.printStackTrace();
                     }
                 } else {
                     forward(IPAddress, Bport, data);
                 }
                 return;
             case RingoProtocol.SEND_BEGIN:
                 System.out.println("Received send begin");
                 //TODO
                 //if this is the sender then send the first packet of the file
                 if (Ringo.mode.equals("S")) {
                     synchronized (Ringo.connect_lock) {
                         Ringo.received_send_begin = true;
                     }
                 } else {
                     //if this is not then forward it along
                     forward(IPAddress, Bport, data);
                 }
                 return;
             case RingoProtocol.FILE_DATA:
                 //TODO
                 //if this is the reciever then send an ack
                 //if not then forward
                 System.out.println("Got file data deciding what to do with it");
                 if (Ringo.mode.equals("R")) {
                     System.out.println("Sending an ack");
                     byte[] data_without_header = new byte[data.length - 1];
                     System.arraycopy(data, 1, data_without_header, 0, data.length - 1);
                     ByteArrayInputStream in = new ByteArrayInputStream(data_without_header);
                     byte[] seq_num_bytes = new byte[Integer.BYTES];
                     try {
                         in.read(seq_num_bytes);
                     } catch (Exception e) {
                         e.printStackTrace();
                     }
                     int seq_num_response = ByteBuffer.wrap(seq_num_bytes).getInt();
                     RingoProtocol.sendAck(ringoSocket, IPAddress, Bport, seq_num_response);
                     //store the file data
                    // byte[] file_data = new byte[data.length - 1 - Integer.BYTES];
                     //System.arraycopy(data, 1 + Integer.BYTES, file_data, 0, data.length - 1 - Integer.BYTES);
                     byte[] size = new byte[4];
                     System.arraycopy(packetZ.getData(), Integer.BYTES + 1, size, 0, 4);
                System.out.println(Arrays.toString(size));
                      int a = new BigInteger(size).intValue();
                     System.out.println("LENGTH: " + a);
                      byte[] file_data = new byte[a];
                      System.out.println(Arrays.toString(packetZ.getData()));
                     System.arraycopy(data, 1 + 2 * Integer.BYTES, file_data, 0, a);


                     //file_data = trim(file_data);
                     Ringo.split_filedata.add(file_data);
                 } else {
                     System.out.println("Forwarding");
                     forward(IPAddress, Bport, data);
                 }
                 return;
             case RingoProtocol.ACK:
                 System.out.println("Got a file ack deciding what to do with it");
                 //TODO
                 //if this is the sender then mark the packet as sent
                 //also then send the next packet in the sequence
                 //if not then foward it along the ring
                 if (Ringo.mode.equals("S")) {
                     System.out.println("Accepting the ack");
                     byte[] data_without_header = new byte[data.length - 1];
                     System.arraycopy(data, 1, data_without_header, 0, data.length - 1);
                     ByteArrayInputStream in = new ByteArrayInputStream(data_without_header);
                     byte[] seq_num_bytes = new byte[Integer.BYTES];
                     try {
                         in.read(seq_num_bytes);
                     } catch (Exception e) {
                         e.printStackTrace();
                     }
                     int seq_num_response = ByteBuffer.wrap(seq_num_bytes).getInt();
                     synchronized (Ringo.received_filedata_lock) {
                         if (seq_num_response == 0) {
                             Ringo.received_filedata_0 = true;
                         } else {
                             Ringo.received_filedata_1 = true;
                         }
                     }
                 } else {
                     System.out.println("Forwarding the ack");
                     forward(IPAddress, Bport, data);
                 }
                 return;
             case RingoProtocol.TERMINATE:
                 //TODO
                 //if this is the receiver then we know the incoming packets are complete
                 //form the file and store it on our system
                 //send a terminate ack
                 //if not just forward this along
                 System.out.println("Got terminate...deciding what to do");
                 if (Ringo.mode.equals("R")) {
                     System.out.println("Sending a terminate ack");
                     RingoProtocol.sendTerminateAck(ringoSocket, IPAddress, Bport);
                     //finish the file and write it to disk
                     int file_byte_length = 0;
                     for (byte[] entry: Ringo.split_filedata) {
                         file_byte_length += entry.length;
                     }
                     byte[] file_bytes = new byte[file_byte_length];
                     int file_bytes_loc = 0;
                     for (byte[] entry: Ringo.split_filedata) {
                         for (int i = 0; i < entry.length; i++) {
                             file_bytes[file_bytes_loc++] = entry[i];
                         }
                     }
                     try {
                         Files.write(Paths.get("./transferred_file_" + num_files_transferred++), file_bytes);
                         //clear the buffer
                         Ringo.split_filedata.clear();
                     } catch (Exception e) {
                         e.printStackTrace();
                     }
                 } else {
                     System.out.println("Forwarding");
                     forward(IPAddress, Bport, data);
                 }
                 return;
             case RingoProtocol.FUCK_OFF:
                 return;
             case RingoProtocol.TERMINATED:
                 //TODO
                 //if this is the sender then we know the file was sent successfully
                 //clear any data associated with sending the file and notify the UI
                 //if not forward
                 System.out.println("Got a terminate ack...deciding what to do with it");
                 //TODO
                 //if this is the sender then mark the packet as sent
                 //also then send the next packet in the sequence
                 //if not then foward it along the ring
                 if (Ringo.mode.equals("S")) {
                     System.out.println("Accepting the ack");
                     synchronized (Ringo.terminate_lock) {
                         Ringo.received_term = true;
                     }
                 } else {
                     System.out.println("Forwarding the ack");
                     forward(IPAddress, Bport, data);
                 }
                 return;
             case RingoProtocol.I_AM_RECEIVER:
                 //if sender then record that u know the receiver
                 if (Ringo.mode.equals("S")) {
                     System.out.println("Found the receiver");
                     //TODO: set receiver address and port in Ringo.receiver_address etc
                     byte[] data_without_header = new byte[data.length - 1];
                     System.arraycopy(data, 1, data_without_header, 0, data.length - 1);
                     byte[] receiver_port_bytes = new byte[Integer.BYTES];
                     ByteArrayInputStream in = new ByteArrayInputStream(data_without_header);
                     try {
                         in.read(receiver_port_bytes);
                     } catch (Exception e) {
                         e.printStackTrace();
                     }
                     Ringo.receiver_port = ByteBuffer.wrap(data_without_header).getInt();
                     Ringo.receiver_address = IPAddress;
                 }
                 return;
             case RingoProtocol.FILE_DONE:
                 System.out.print("Toggling is_sending boolean to ");
                 synchronized (is_sending_lock) {
                     byte[] data_without_header = new byte[data.length - 1];
                     System.arraycopy(data, 1, data_without_header, 0, data.length - 1);
                     byte zero = 0;
                     byte one = 1;
                     byte[] empty_data = new byte[1];
                     if (data_without_header[0] == zero) {
                         System.out.println("true");
                         is_sending = true;
                     } else {
                         System.out.println("false");
                         if (suicide_pact) {
                             //this guy has to tell everyone to kill themselves
                             try {
                                 ArrayList<IpTableEntry> everyonebutme = ip_table.getTargetsExcludingOne(InetAddress.getLocalHost(), local_port);
                                 for (IpTableEntry entry: everyonebutme) {
                                     RingoProtocol.reliableSend(ringoSocket, empty_data, entry.getAddress(), entry.getPort(), local_port, RingoProtocol.KILL_YOURSELF, 6);
                                 }
                             } catch (Exception e) {
                                 e.printStackTrace();
                             }
                             is_sending = false;
                             Ringo.kill_urself_loser();
                             return;
                         }
                         is_sending = false;
                     }
                 }

            default:
                return;
        }
        //Ringo.ip_table.printTable();

        synchronized (rtt_lock) {
            if (startRtt && !transitionExecuted) {
                transitionExecuted = true;
                System.out.println("Finished IP Table:");
                ip_table.printTable();
                sendCompleteIpTable(); //for lagging nodes
                System.out.println("sending RTT pings ----------------------------------------------------");
                sendRttPings();

                Ringo.numActiveRingos = numringos;


            }
        }

        synchronized (rtt_lock) {
           preOptimal();

                return;
            }
        }

    private void preOptimal() {
        if (Ringo.rtt_table.isComplete()) {//inefficient but whatever, can technically move this so it's not o(2n) but o(n) before
            //call optimal ring formation method
            System.out.println("i'm done with all my setup before optimal ring needs to be found");
            // if (Ringo.optimalRing==null)

            if (keepalives.size() != Ringo.rtt_table.getIps().size() - 1) {

                killAlive(false);

                for (String ip: Ringo.rtt_table.getIps()) {
                    if (!ip.equals(setupVector.getSrcIp())) {
                        System.out.println("starting listener for: " + ip);
                        startKeepAlive(ip);
                    }

                }
            }

            System.out.println("Threads alive right now: "+Thread.activeCount());
            System.out.println("threads that didn't die: " +  activeThreads);
            //System.exit(1);

            formOptimalRing();
    }

    synchronized (rtt_lock) {

            System.out.println("thread done");
        }

    }

    private void rtt_requestResponse(InetAddress IPAddress, int Bport) {
        System.out.println("a node has requested me to send my rtt_table");
        if (setupVector.getIps().size() == numActiveRingos - 1) { //only if your setupvector is complete do we want it
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream os;
            byte[] serializedTable;
            try {
                os = new ObjectOutputStream(out);
                os.writeObject(Ringo.rtt_table);

                serializedTable = out.toByteArray();
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("failed to serialize table");
                System.exit(1);
                return;
            }

            byte[] toSend = new byte[serializedTable.length];
            // System.arraycopy(serializedTable, 0, toSend, 1, serializedTable.length);
            //toSend[0] = 0x5; //header for RTTUPDATE



            RingoProtocol.sendUpdateRTTTable(ringoSocket, IPAddress, Bport, serializedTable);
        }

    }

    private void forward(InetAddress prev_ip, int prev_port, byte[] data) {
        try {
            System.out.println("Fowarding a packet");
            String myaddr = "";

            myaddr = "/" + InetAddress.getLocalHost().getHostAddress();

            String next_in_ring = getNextInRing(prev_ip, prev_port, myaddr, Ringo.local_port);
            System.out.println("Forwarding to " + next_in_ring);
            String[] next_parts = next_in_ring.split(":");
            next_parts[0] = next_parts[0].replace("/", "");
            InetAddress addr = InetAddress.getByName(next_parts[0]);

            if (next_parts[0].contains("127.0.0.1")) {
                addr = InetAddress.getLocalHost();
            }
            //TODO: use RingoProtocol.forward()
            RingoProtocol.forward(ringoSocket, addr, Integer.parseInt(next_parts[1]), data);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private String getNextInRing(InetAddress prev_addr, int prev_port, String myaddr, int myport) {
        int my_loc_in_ring = 0;
        for (int i = 0; i < Ringo.optimalRing.length; i++) {
            if (Ringo.optimalRing[i].equals(myaddr + ":" + myport)) {
                my_loc_in_ring = i;
            }
        }
        System.out.println("My location in optimal ring: " + my_loc_in_ring);
        int prev_loc = 0;
        if (my_loc_in_ring == 0) {
            prev_loc = Ringo.optimalRing.length -1;
        } else {
            prev_loc = my_loc_in_ring - 1;
        }
        String left_neighbor = Ringo.optimalRing[prev_loc];
        int next_loc = 0;
        if (my_loc_in_ring  ==  Ringo.optimalRing.length- 1) {
            next_loc = 0;
        } else {
            next_loc = my_loc_in_ring + 1;
        }
        String right_neighbor = Ringo.optimalRing[next_loc];
        if (left_neighbor.equals("/" + prev_addr.getHostAddress() + ":" + prev_port)) {
            return right_neighbor;
        }
        return left_neighbor;
    }

    private void sendAliveAck(InetAddress ip, int port){
        byte[] toSend = new byte[1];
        toSend[0] = RingoProtocol.KEEP_ALIVEACK;
        DatagramPacket aliveack = new DatagramPacket(toSend, toSend.length, ip, port);
        try {
            ringoSocket.send(aliveack);
        }catch(Exception e) {
            ringoSocket.close();
            return;
        }

    }

    private void startKeepAlive(String ip) {
        System.out.println("Keep Alive ip: " + ip);
        System.out.println("THE SETUP VECTOR: " + setupVector.printVector());
        KeepAliveListener k = new KeepAliveListener(ip, setupVector.getRTT(ip));
        keepalives.put(ip, k);
        k.start();
        System.out.println("Started keep alive");
    }

    /**
     * Sends the current Ringo's fully complete Ip Table to all other ringos.
     */
    private void sendCompleteIpTable() {
        try {

        System.out.println("sending my complete ip table out to everyone");

            IpTable tabletosend = ip_table;
            //send the current network situation to the new node
            byte[] ip_table_bytes;
            byte[] ip_table_bytes_for_all;

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ObjectOutputStream os = new ObjectOutputStream(out);

                os.writeObject(tabletosend);
                ByteArrayOutputStream out2 = new ByteArrayOutputStream();
                ObjectOutputStream os2 = new ObjectOutputStream(out2);
                os2.writeObject(ip_table);
                ip_table_bytes_for_all = out2.toByteArray();
                ip_table_bytes = out.toByteArray();



            ArrayList<IpTableEntry> update_destinations = ip_table.getTargetsExcludingOne(InetAddress.getByName(InetAddress.getLocalHost().getHostAddress()), this.port);
            for (IpTableEntry entry: update_destinations) {
                RingoProtocol ringoproto = new RingoProtocol();
                //ringoproto.sendUpdateIpTable(ringoSocket, entry.getAddress(), entry.getPort(), ip_table_bytes_for_all);
                RingoProtocol.reliableSend(ringoSocket,ip_table_bytes, entry.getAddress(), entry.getPort(), port, RingoProtocol.UPDATE_IP_TABLE, 8);
                System.out.println("reliable send completed");
            }
        }catch(Exception e) {
            e.printStackTrace();
        }

        if (Ringo.mode.equals("R")) {
            HashMap<String, IpTableEntry> ip_table = Ringo.ip_table.getTable();
            for (Map.Entry<String, IpTableEntry> entry: ip_table.entrySet()) {
                try {
                    DatagramSocket socket = new DatagramSocket();
                    byte[] buf = new byte[Integer.BYTES];
                    byte[] my_port_bytes = ByteBuffer.allocate(Integer.BYTES).putInt(Ringo.local_port).array();
                    System.arraycopy(my_port_bytes, 0, buf, 0, my_port_bytes.length);
                    System.out.println("Sending an I am receiver to " + entry.getValue().getAddress().getHostAddress() + ":" + entry.getValue().getPort());
                    RingoProtocol.reliableSendTime(socket, buf, entry.getValue().getAddress(), entry.getValue().getPort(), Ringo.local_port, RingoProtocol.I_AM_RECEIVER, 15);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    /**
     * handles the RTT calculations given a ping response and updates the RTT vector associated with the local ringo.
     * @param address address of the response sender
     * @param data packet data from the ping response packet
     */
    private void handlePingResponse(InetAddress address, byte[] data) {

        byte[] time_bytes = new byte[Long.BYTES];
        int port = 0;
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        in.read(); //read header
        byte[] loc_port_bytes = new byte[4]; //2 bytes for ints
        try {
            in.read(loc_port_bytes);
            in.read(time_bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        port = ByteBuffer.wrap(loc_port_bytes).getInt();


        long startTime = ByteBuffer.wrap(time_bytes).getLong();
        long rttTime;
        rttTime = System.currentTimeMillis() - startTime;
        /*synchronized (syncTimeStampLock) {
            rttTime = System.currentTimeMillis() - syncTimeStamp;


            syncTimeStamp = 0;
        }*/

        int retardedRtt = (int) rttTime - 150 * 2 < 0 ? 1: (int) rttTime - 150 * 2; //since we have reliability issues: avg # tries * sleeping in between tries should be subtracted
        //System.out.println("RTT: " + retardedRtt);
        //System.out.println("THE VECTOR: " + port + " " + setupVector.printVector());
        setupVector.pushRTT(address.toString() + ":" + port, retardedRtt);
        //System.out.println(setupVector.printVector() + setupVector.getIps().size());
        if (setupVector.getIps().size() != numringos - 1) { //if we only have some rtts, continously ask everyone for it.
            //sendRttPings();
            //System.out.println("NOT YET!!!");
        }
        if (setupVector.getIps().size() == numringos -1 && !added_setupVector) {
            System.out.println("i am now merging my own distance vectors into the rtt table");
            //System.out.println(Ringo.rtt_table.getIps());
            Ringo.rtt_table.pushVector(setupVector.getSrcIp(), setupVector);
            //System.out.println(Ringo.rtt_table.getIps());
            //System.out.println("-------------------");
            //System.out.println(setupVector.printVector() + "<-setupVECTOR");
            //System.exit(1);
            added_setupVector = true;
            /*for (String ip: Ringo.rtt_table.getIps()) {
                System.out.println(Ringo.rtt_table.getVector(ip).printVector() + " |");
            }
*/
//After the lagging child forms its own vector, it floods
            System.out.println("i'm sending my table with my own distance vector in it to everyone i know");
            floodRTT(); //only after making my own vector, do i just send it to everyone?
            if (resurrected) {
                //System.out.println("here");
                requestRTTS();
            }
        }

    }

    /**
     * merges the ip table in the packet data with the current IP Table.
     * @param data bytes containing a serialized ip table
     * @return whether or not the local ip table is complete
     */
    private boolean handleUpdateIp(byte[] data) {
       // System.out.println("this node is updating its IP table");
        byte[] table_bytes = new byte[data.length-1];
        System.arraycopy(data, 1, table_bytes, 0, data.length -1);
        ByteArrayInputStream in = new ByteArrayInputStream(table_bytes);
        ObjectInputStream objin;
        IpTable ipTable = null;
        try {
            objin = new ObjectInputStream(in);
            ipTable = (IpTable) objin.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }
        boolean startRTT = ip_table.merge(ipTable);
        //System.out.println("START RTT: " + startRTT);
        return startRTT;
    }

    /**
     * Uses data about the ringo that sent the new node packet in order to act as its point of contact to the rest of
     * the network. Sends updates to the new node as well as the rest of the network.
     * @param address address of the new node
     * @param data data from the packet containing the true port of the new node
     * @return whether or not this new node completes the ip table
     */

    //kill the listener, kill all keepalives, make new listener

    private boolean actAsPoc(InetAddress address, byte[] data) {
        System.out.println("I AM ACTING AS POC");
        int port = 0;
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        in.read(); //read header
        byte[] loc_port_bytes = new byte[4]; //2 bytes for ints
        try {
            in.read(loc_port_bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        port = ByteBuffer.wrap(loc_port_bytes).getInt();
        IpTable tabletosend = new IpTable(ip_table.getNumRingos(), this.port);
        try {
            tabletosend.addEntry(InetAddress.getByName(InetAddress.getLocalHost().getHostAddress()), this.port);
        } catch (Exception e) {
            e.printStackTrace();
        }
        tabletosend.merge(ip_table);
        boolean startRTT = ip_table.addEntry(address, port);
        //send the current network situation to the new node
        byte[] ip_table_bytes;
        byte[] ip_table_bytes_for_all;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream os = new ObjectOutputStream(out);

            os.writeObject(tabletosend);
            ByteArrayOutputStream out2 = new ByteArrayOutputStream();
            ObjectOutputStream os2 = new ObjectOutputStream(out2);
            os2.writeObject(ip_table);
            ip_table_bytes_for_all = out2.toByteArray();
            ip_table_bytes = out.toByteArray();
            ArrayList<IpTableEntry> update_destinations = ip_table.getTargetsExcludingOne(InetAddress.getByName(InetAddress.getLocalHost().getHostAddress()), this.port);
            for (IpTableEntry entry: update_destinations) {
                RingoProtocol ringoproto = new RingoProtocol();
               // System.out.println("sending ip table shit");
                //System.out.println(entry.getAddress().toString()+ "memes" + entry.getPort());
                ringoproto.sendUpdateIpTable(ringoSocket, entry.getAddress(), entry.getPort(), ip_table_bytes_for_all);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return startRTT;
    }

    /**
     * Sends out ping hello packets requesting the RTT to all of the known nodes in the ringo network (contains
     * timestamp in the packet.
     */
    private void sendRttPings() {
        System.out.println("I AM PINGING");
        long startTime = System.currentTimeMillis();
        for (Map.Entry<String, IpTableEntry> entry : ip_table.getTable().entrySet()) {
            try {
                if (!entry.getKey().equals(InetAddress.getByName(InetAddress.getLocalHost().getHostAddress()).toString() + this.port)) {
                    RingoProtocol protocol = new RingoProtocol();
                    protocol.sendPingHello(ringoSocket, entry.getValue().getAddress(), entry.getValue().getPort(), startTime, this.port);
                    System.out.println("successfully received confirmation of ping from :" + entry.getValue().getPort());
                    //RingoProtocol.reliableSend(ringoSocket, entry.getValue().getAddress(), entry.getValue().getPort(), port, )
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Parses the hello packet for the time and forms a new response packet with that data. Then sends it back to
     * sender.
     * @param address address of sender
     * @param data data of the ping hello packet
     */
    private void sendRttResponse(InetAddress address, byte[] data) {
        int port = 0;
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        in.read(); //read header
        byte[] loc_port_bytes = new byte[4]; //2 bytes for ints
        byte[] time_bytes = new byte[8];
        try {
            in.read(loc_port_bytes);
            in.read(time_bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        port = ByteBuffer.wrap(loc_port_bytes).getInt();
        RingoProtocol protocol = new RingoProtocol();
       protocol.sendPingResponse(ringoSocket, address, port, time_bytes, this.port);
        //RingoProtocol.reliableSend(ringoSocket, time_bytes, )
    }

    /**
     * updates the current RTT table with the serialized table in the data of the received packet.
     * @param data serialized rtt table
     */
    private void updateRTT(byte[] data) {
        //System.out.println("why isn't this printing");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ObjectInputStream is;
        RttTable tmp;
        try {
            is = new ObjectInputStream(in);
            tmp = (RttTable) is.readObject(); //deserialize from remaining bytes
        }catch(Exception e) {
            e.printStackTrace();
            System.out.println("non recoverable error from deserialization");
            System.exit(1);
            return;
        }
        /*
        Since every update is an rtt_table, we don't need the specific ip and port of the sender. we just merge.
         */
       /* System.out.println("i am now merging my rtt_table BEFORE:");
        System.out.println(Ringo.rtt_table.getIps());
*/
            Ringo.rtt_table.merge(tmp);
/*
        System.out.println("after:");
        System.out.println(Ringo.rtt_table.getIps());
        System.out.println("table was actually merged");
*/

//System.out.println(Ringo.rtt_table.test());
        //System.out.println(tmp.getIps());
preOptimal();
    }

    /**
     * Send RTT update packets to all other ringos
     */
    private void floodRTT() {
        /*synchronized (rtt_lock) {
            System.out.println("separator ------------------------");
            for(String ip: Ringo.rtt_table.getIps()) {
                System.out.println("|"+Ringo.rtt_table.getVector(ip).printVector()+"|");
            }
        }
*/
       // System.out.println("FLOODING RTT");
        for (Map.Entry<String,IpTableEntry> entry: ip_table.getTable().entrySet()) {
            try {
                if (!entry.getKey().equals(InetAddress.getByName(InetAddress.getLocalHost().getHostAddress()).toString() + this.port)) {
                    //System.out.println("sending my RTT_Table");
                    int dstPort = entry.getValue().getPort(); //DANIEL change this to get the dst port of the ringo with the associated ip
                    InetAddress IPAddress = entry.getValue().getAddress();
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    ObjectOutputStream os;
                    byte[] serializedTable;
                    try {
                        os = new ObjectOutputStream(out);
                        os.writeObject(Ringo.rtt_table);

                        serializedTable = out.toByteArray();
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println("failed to serialize table");
                        System.exit(1);
                        return;
                    }

                    byte[] toSend = new byte[serializedTable.length + 1];
                    System.arraycopy(serializedTable, 0, toSend, 1, serializedTable.length);


                    RingoProtocol.reliableSend(ringoSocket, serializedTable, IPAddress, dstPort, port, RingoProtocol.RTT_UPDATE, 8);
                    System.out.println("successfully sent everyone my rtt_table");
                    toSend[0] = 0x5; //header for RTTUPDATE

                    /*DatagramPacket sendPacket = new DatagramPacket(toSend, toSend.length, IPAddress, dstPort);

                    try {
                        ringoSocket.send(sendPacket);
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println("failed to send packet to an ip when flooding RTT");
                        System.exit(1);
                        return;
                    }*/
                }
            }catch(Exception e) {
                e.printStackTrace();
            }
        }

    }
    /*
    TODO:
    so far, ipRing just contains the ip addresses of the ringos in order of the shortest ring
     */

    /**
     * Use the data in RTT table to form an optimal ring based on the design specifications.
     */
    public void formOptimalRing() {
        /*synchronized (rtt_lock) {
            if (Ringo.optimalRing != null) {
                System.out.println("wanted to form optimal ring for this ringo but it was already there");
                return;
            }
        }*/
        System.out.println("re-forming optimal ring");
        int[][] converted = Ringo.rtt_table.convert();
        Ringo.rtt_converted = converted;
        int[] ringRaw = getShortestHamiltonianCycle(converted);
        String[] ipRing = new String[ringRaw.length];
        int i = 0;
        for(int ringoIndex: ringRaw) {

            String ip = Ringo.rtt_table.getInverseMap().get(ringoIndex);
            ipRing[i] = ip;
            i++;
        }
        Ringo.optimalRing = ipRing;

        //have the sender calculate the which of the two paths are better and store them for easy use later
        if (Ringo.mode.equals("S")) {
            try {
                String before = "";
                String after = "";
                String myip = "/" + InetAddress.getLocalHost().getHostAddress();
                String myname = myip + ":" + Ringo.local_port;
                System.out.println("TEST" + Ringo.receiver_address);
                String receivername = Ringo.receiver_address.getHostAddress() + ":" + Ringo.receiver_port;
                System.out.println(myname);
                int my_loc = 0;
                int receiver_loc = 0;
                for (int k = 0; k < Ringo.optimalRing.length; k++) {
                    if (Ringo.optimalRing[k].equals(myname)) {
                        int after_num = k + 1;
                        if (after_num == Ringo.optimalRing.length) {
                            after_num = 0;
                        }
                        int before_num = k - 1;
                        if (before_num < 0) {
                            before_num = Ringo.optimalRing.length - 1;
                        }
                        after = Ringo.optimalRing[after_num];
                        before = Ringo.optimalRing[before_num];
                        my_loc = k;
                    }
                    System.out.println("Checking receivername of /" + receivername + " against " + Ringo.optimalRing[k]);
                    if (Ringo.optimalRing[k].equals("/" + receivername)) {
                        receiver_loc = k;
                    }
                }
                int right_sum = 0;
                String prev_name = Ringo.optimalRing[my_loc];
                for (int j = (my_loc + 1) % Ringo.optimalRing.length; j < Ringo.optimalRing.length; j = (j + 1) % Ringo.optimalRing.length)  {
                    System.out.println("Measuring from " + prev_name + " to  " + Ringo.optimalRing[j]);
                    RttVector prev_vector = rtt_table.getVector(prev_name);
                    right_sum += prev_vector.getRTT(Ringo.optimalRing[j]);
                    prev_name = Ringo.optimalRing[j];
                    System.out.println("Right sum new: " + right_sum);
                    if (j == receiver_loc) {
                        break;
                    }
                }
                int left_sum = 0;
                prev_name = Ringo.optimalRing[my_loc];
                for (int j = (my_loc - 1) % Ringo.optimalRing.length; j < Ringo.optimalRing.length; j = (j - 1) % Ringo.optimalRing.length)  {
                    if (j == -1) {
                        j = Ringo.optimalRing.length -1;
                    }
                    RttVector prev_vector = rtt_table.getVector(prev_name);
                    System.out.println("current optimal ring iterator " + j);
                    left_sum += prev_vector.getRTT(Ringo.optimalRing[j]);
                    prev_name = Ringo.optimalRing[j];

                    System.out.println("Left sum new: " + left_sum);

                    if (j == receiver_loc) {
                        break;
                    }
                }
                System.out.println("Setting optimal path for the sender");
                System.out.println("Right direction sum : " + right_sum);
                System.out.println("Left direction sum : " + left_sum);

                if (right_sum > left_sum) {
                    Ringo.optimal_neighbor = before;
                    Ringo.suboptimal_neighbor = after;
                } else {
                    Ringo.optimal_neighbor = after;
                    Ringo.suboptimal_neighbor = before;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (!Ringo.uiStarted) {
            try {
                System.out.println("threadcount: " + activeThreads);
                Ringo.startUI();
            }catch(InterruptedException e) {
                e.printStackTrace();
            }
        }


        /*
        TODO: figure out what to do with this string array which represents the optimal ring and let other nodes know you're done?
        edge cases: somehow some nodes don't come up with the same optimal ring?
         //AT THIS POINT, THE RINGO MUST HAVE BEEN TAKEN OFFLINE SINCE NOTHING RETURNS FROM STARTUI BESIDES GOING OFFLINE
         //THE FOLLOWING IS RESET CODE FOR THE LISTENER CONTINUING FROM THE OFFLINE CODE FROM THE UI



         */






    }

    /**
     * Helper function for the optimal ring that finds the shortest hamiltonian cycle of a simplified graph (2d int
     * array)
     * @param dist 2d distance array used to represent the graph
     * @return int array representing the cycle
     */
    public int[] getShortestHamiltonianCycle(int[][] dist) {
        int n = dist.length;
        int[][] dp = new int[1 << n][n]; //2^n cells containing n entries. Literal magic. Donald Knuth would be proud.
        for (int[] d : dp)
            Arrays.fill(d, Integer.MAX_VALUE / 2);
        dp[1][0] = 0;
        for (int mask = 1; mask < 1 << n; mask += 2) {
            for (int i = 1; i < n; i++) {
                if ((mask & 1 << i) != 0) {
                    for (int j = 0; j < n; j++) {
                        if ((mask & 1 << j) != 0) {
                            dp[mask][i] = Math.min(dp[mask][i], dp[mask ^ (1 << i)][j] + dist[j][i]);
                        }
                    }
                }
            }
        }
        int res = Integer.MAX_VALUE;
        for (int i = 1; i < n; i++) {
            res = Math.min(res, dp[(1 << n) - 1][i] + dist[i][0]);
        }

        // reconstruct path
        int cur = (1 << n) - 1;
        int[] order = new int[n];
        int last = 0;
        for (int i = n - 1; i >= 1; i--) {
            int bj = -1;
            for (int j = 1; j < n; j++) {
                if ((cur & 1 << j) != 0 && (bj == -1 || dp[cur][bj] + dist[bj][last] > dp[cur][j] + dist[j][last])) {
                    bj = j;
                }
            }
            order[i] = bj;
            cur ^= 1 << bj;
            last = bj;
        }
        currentShortestRingLength = res;
        return order;
    }

    public void removeRingo(String ip) {
        System.out.println("Ringo is being removed!");
        Ringo.rtt_table.removeEntry(ip);
        ip_table.removeEntry(ip);
       // numringos--;
        Ringo.numActiveRingos--;
        synchronized (Ringo.path_switching_lock) {
            //check if this ringo is in the current path
            try {
                if (Ringo.mode.equals("S")) {
                    InetAddress myaddr = InetAddress.getLocalHost();
                    int myport = local_port;
                    String myid = "/" + myaddr.getHostAddress() + ":" + myport;
                    String receiver = "/" + receiver_address.getHostAddress() + ":" + receiver_port;
                    int my_loc_in_ring = 0;
                    System.out.println("OPT: " + Arrays.toString(optimalRing));

                    for (int i = 0; i < Ringo.optimalRing.length; i++) {
                        if (Ringo.optimalRing[i].equals(myaddr + ":" + myport)) {
                            my_loc_in_ring = i;
                        }
                    }
                    System.out.println("receiver: " + receiver);
                    System.out.println("ip: " + ip);
                    boolean add = false;
                    if (optimalRing[(my_loc_in_ring + 1) % optimalRing.length].equals(optimal_neighbor)) {
                        add = true;
                    }
                    while (!(optimalRing[my_loc_in_ring % optimalRing.length].equals( receiver))) {
                        System.out.println(optimalRing[my_loc_in_ring % optimalRing.length]);
                        if (optimalRing[my_loc_in_ring % optimalRing.length].equals(ip)) {
                            Ringo.use_suboptimal_path = true;
                        }
                        if (add) {
                            my_loc_in_ring++;
                        } else {
                            my_loc_in_ring--;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
/*
kills all keepalive threads and then begins process of killing this listener.
 */
    public void killAlive(boolean reset) {
        for (String ip: keepalives.keySet()) {
            keepalives.get(ip).listening = false;
            try {
                keepalives.get(ip).join();
            }catch(Exception e) {
                e.printStackTrace();
            }

        }
        System.out.println("killed all");
        if (reset) {
            listening = false;
        }

        //ringoSocket.close();

    }

}
