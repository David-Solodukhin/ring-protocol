package com.company;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class Listener extends Thread{
    private byte[] receiveData = new byte[1024]; //TODO: modify these byte array sizes
    private byte[] sendData = new byte[1024];
    private DatagramSocket ringoSocket;
    private int port = 0;
    private int currentShortestRingLength = 0;
    public boolean listening = true;
    private final Object rtt_lock = new Object();
    private final Object ip_lock = new Object();
    private RttVector setupVector;
    private int numringos;
    private boolean added_setupVector = false;

    public Listener(int port, int numringos) {
        this.port = port;
        this.numringos = numringos;
        try {
            this.setupVector = new RttVector(0, InetAddress.getByName(InetAddress.getLocalHost().getHostAddress()).toString() + port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void run() {
        try {
            ringoSocket = new DatagramSocket(port);
        } catch (SocketException e) {
            e.printStackTrace();
        }


        while(listening) //look through queue of received packets and parse them one by one(no concurrent receive)
        {
            try {
                byte[] receive = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receive, receive.length);
                //listener thread blocks on this until something is received
                ringoSocket.receive(receivePacket);


                //TODO: make a worker class for this thread
                Thread t1 = new Thread() {

                    public void run() {
                        parsePacket(receivePacket.getData(), receivePacket.getAddress());
                        return;
                    }
                };

                t1.start();



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

        return;
    }
    public void parsePacket(byte[] data, InetAddress IPAddress) {
        //InetAddress IPAddress = packet.getAddress();
        //int port = packet.getPort();
        //byte[] data = packet.getData();
        /*TODO: implement cases on packet headers.
        1. parse packet header from payload bytes
        2. identify what needs to be done, call that particular method with a new thread.
        3. mark all data structures used by that thread as synchronized(use either a sync block, semaphore or intrinsic lock)
        4. if needed, send a response to whoever and then return/exit thread.

        NOTE: IT IS THE JOB OF THE RTT RESPONSE THREAD METHOD TO give the signal for the listener to stop listening and yield
         */

        boolean startRtt = false;
        //System.out.println("Got a new packet! ");
        switch (data[0]) {
            case RingoProtocol.NEW_NODE:
                System.out.println("Got a new_node packet!");
                synchronized (ip_lock) {
                    startRtt = actAsPoc(IPAddress, data);
                }
                break;

            case RingoProtocol.UPDATE_IP_TABLE:
                System.out.println("Got updateIp table");
                synchronized (ip_lock) {
                    if (Ringo.optimalRing == null) {
                        startRtt = handleUpdateIp(data);
                    }

                }
                break;
            case RingoProtocol.PING_HELLO:
                System.out.println("Got ping hello!");
               // Ringo.ip_table.printTable();
                sendRttResponse(IPAddress, data);
                break;
            case RingoProtocol.PING_RESPONSE:
                System.out.println("Got ping response!");
                if (Ringo.optimalRing == null) {
                    synchronized (rtt_lock) {
                        handlePingResponse(IPAddress, data);
                    }

                }
               // System.out.println("Finished Table:");

                break;
            case RingoProtocol.RTT_UPDATE:
                //System.out.println("Got an RTT table update");


                byte[] payload = new byte[data.length - 1];
                System.arraycopy(data, 1, payload, 0, data.length - 1); // -1 because header is removed
                if (Ringo.optimalRing == null) {
                    synchronized (rtt_lock) {
                        updateRTT(payload);
                        System.out.println("RTT Table updated");
                    }
                    floodRTT();

                } else if(Ringo.optimalRing != null){ //a child(we don't know who) is lagging behind so we just send our rtt to everyone again
                    //System.out.println("why is this happening??????????????"+ Ringo.optimalRing + " " + startRtt);
                    floodRTT();
                }

                break;

            default:
                break;
        }
        //Ringo.ip_table.printTable();
        synchronized (rtt_lock) {
            if (startRtt) {
                System.out.println("Finished Table:");
                Ringo.ip_table.printTable();
                sendCompleteIpTable(); //for lagging nodes
                sendRttPings();
            }
        }


    }
    private void sendCompleteIpTable() {
        try {



            IpTable tabletosend = Ringo.ip_table;
            //send the current network situation to the new node
            byte[] ip_table_bytes;
            byte[] ip_table_bytes_for_all;

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ObjectOutputStream os = new ObjectOutputStream(out);

                os.writeObject(tabletosend);
                ByteArrayOutputStream out2 = new ByteArrayOutputStream();
                ObjectOutputStream os2 = new ObjectOutputStream(out2);
                os2.writeObject(Ringo.ip_table);
                ip_table_bytes_for_all = out2.toByteArray();
                ip_table_bytes = out.toByteArray();



            ArrayList<IpTableEntry> update_destinations = Ringo.ip_table.getTargetsExcludingOne(InetAddress.getByName(InetAddress.getLocalHost().getHostAddress()), this.port);
            for (IpTableEntry entry: update_destinations) {
                //System.out.println("Sending update to " + entry.getAddress() + entry.getPort());
                RingoProtocol ringoproto = new RingoProtocol();
                ringoproto.sendUpdateIpTable(ringoSocket, entry.getAddress(), entry.getPort(), ip_table_bytes_for_all);
                //System.out.println("Sending update to " + entry.getAddress() + ":" + entry.getPort());
            }
        }catch(Exception e) {
            e.printStackTrace();
        }

    }
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
        long rttTime = System.currentTimeMillis() - startTime;

        int retardedRtt = (int) rttTime;
        setupVector.pushRTT(address.toString() + port, retardedRtt);
        if (setupVector.getIps().size() == numringos -1 && !added_setupVector) {
            System.out.println("i am now merging my own distance vectors into the rtt table");
            //System.out.println(Ringo.rtt_table.getIps());
            Ringo.rtt_table.pushVector(setupVector.getSrcIp(), setupVector);
            //System.out.println(Ringo.rtt_table.getIps());
            //System.out.println("-------------------");
            //System.out.println(setupVector.printVector() + "<-setupVECTOR");
            //System.exit(1);
            added_setupVector = true;


//After the lagging child forms its own vector, it floods
            floodRTT();
        }

    }

    //update the Ip table with the data from the packet
    private boolean handleUpdateIp(byte[] data) {
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
        boolean startRTT = Ringo.ip_table.merge(ipTable);
        return startRTT;
    }

    private boolean actAsPoc(InetAddress address, byte[] data) {
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
        IpTable tabletosend = new IpTable(Ringo.ip_table.getNumRingos(), this.port);
        try {
            tabletosend.addEntry(InetAddress.getByName(InetAddress.getLocalHost().getHostAddress()), this.port);
        } catch (Exception e) {
            e.printStackTrace();
        }
        tabletosend.merge(Ringo.ip_table);
        boolean startRTT = Ringo.ip_table.addEntry(address, port);
        //send the current network situation to the new node
        byte[] ip_table_bytes;
        byte[] ip_table_bytes_for_all;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream os = new ObjectOutputStream(out);

            os.writeObject(tabletosend);
            ByteArrayOutputStream out2 = new ByteArrayOutputStream();
            ObjectOutputStream os2 = new ObjectOutputStream(out2);
            os2.writeObject(Ringo.ip_table);
            ip_table_bytes_for_all = out2.toByteArray();
            ip_table_bytes = out.toByteArray();
            //send update back to new node
            //System.out.println("updating " + address.toString() + ":" + port + "with the following table:");
            //tabletosend.printTable();
            //RingoProtocol.sendUpdateIpTable(ringoSocket, address, port, ip_table_bytes_for_all);
            //Ringo.ip_table.printTable();
            ArrayList<IpTableEntry> update_destinations = Ringo.ip_table.getTargetsExcludingOne(InetAddress.getByName(InetAddress.getLocalHost().getHostAddress()), this.port);
            for (IpTableEntry entry: update_destinations) {
                //System.out.println("Sending update to " + entry.getAddress() + entry.getPort());
                RingoProtocol ringoproto = new RingoProtocol();
                ringoproto.sendUpdateIpTable(ringoSocket, entry.getAddress(), entry.getPort(), ip_table_bytes_for_all);
                //System.out.println("Sending update to " + entry.getAddress() + ":" + entry.getPort());
            }
            //System.out.println("----------------------------");

        } catch (Exception e) {
            e.printStackTrace();
        }
        return startRTT;
    }

    private void sendRttPings() {
        System.out.println("I AM PINGING");
        long startTime = System.currentTimeMillis();
        for (Map.Entry<String, IpTableEntry> entry : Ringo.ip_table.getTable().entrySet()) {
            try {
                if (!entry.getKey().equals(InetAddress.getByName(InetAddress.getLocalHost().getHostAddress()).toString() + this.port)) {
                    //System.out.println("Send ping hello to " + entry.getValue().getAddress() + ":" + entry.getValue().getPort());
                    RingoProtocol protocol = new RingoProtocol();
                    //System.out.println("hello");
                    protocol.sendPingHello(ringoSocket, entry.getValue().getAddress(), entry.getValue().getPort(), startTime, this.port);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

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
    }

    /*
      this method assumes that the data portion of the packet is structured contiguously in the following manner:
      [header: 8 bits][bits representing an RttTable object]


     */
    private void updateRTT(byte[] data) {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ObjectInputStream is;
        RttTable tmp;
        try {
            is = new ObjectInputStream(in);
            tmp = (RttTable) is.readObject(); //deserialize from remaining bytes
        }catch(Exception e) {
            e.printStackTrace();
            System.out.println("non recoverable error");
            System.exit(1);
            return;
        }
        /*
        Since every update is an rtt_table, we don't need the specific ip and port of the sender. we just merge.
         */
        //System.out.println("i am now merging my rtt_table");
        //System.out.println(Ringo.rtt_table.getIps());

            Ringo.rtt_table.merge(tmp);


        //System.out.println(Ringo.rtt_table.getIps());
        //System.out.println("here");



    }
    private void floodRTT() {
        /*synchronized (rtt_lock) {
            System.out.println("separator ------------------------");
            for(String ip: Ringo.rtt_table.getIps()) {
                System.out.println("|"+Ringo.rtt_table.getVector(ip).printVector()+"|");
            }
        }
*/
        //System.out.println("helloooooo");

        for (Map.Entry<String,IpTableEntry> entry: Ringo.ip_table.getTable().entrySet()) {
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
                    toSend[0] = 0x5; //header for RTTUPDATE

                    DatagramPacket sendPacket = new DatagramPacket(toSend, toSend.length, IPAddress, dstPort);
                    try {
                        ringoSocket.send(sendPacket);
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println("failed to send packet to an ip when flooding RTT");
                        System.exit(1);
                        return;
                    }
                }
            }catch(Exception e) {
                e.printStackTrace();
            }
        }
        synchronized (rtt_lock) {
            if (Ringo.rtt_table.isComplete()) {//inefficient but whatever, can technically move this so it's not o(2n) but o(n) before
                //call optimal ring formation method
                System.out.println("i'm done with all my setup before optimal ring needs to be found");
                formOptimalRing();
                return;
            }
        }

    }
    /*
    TODO:
    so far, ipRing just contains the ip addresses of the ringos in order of the shortest ring
     */
    public void formOptimalRing() {
        //System.out.println("-------------------------------------");

        synchronized (rtt_lock) {
            if (Ringo.optimalRing != null) {
                System.out.println("wanted to form optimal ring for this ringo but it was already there");
                return;
            }
        }
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
        Ringo.startUI();
        /*
        TODO: figure out what to do with this string array which represents the optimal ring and let other nodes know you're done?
        edge cases: somehow some nodes don't come up with the same optimal ring?


         */

    }
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
        //System.out.println(Arrays.toString(order));
        return order;
    }
}
