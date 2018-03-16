package com.company;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class IpTable implements Serializable {
    private HashMap<String, IpTableEntry> table;

    private int numRingos;

    /**
     * Constructor for the Ip Table.
     * @param numRingos total number of ringos in the network
     * @param local_port port being used for the current ringo building the table
     */
    public IpTable(int numRingos, int local_port) {
        this.numRingos = numRingos;
        this.table = new HashMap<>();
        try {
            addEntry(InetAddress.getByName(InetAddress.getLocalHost().getHostAddress()), local_port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds an entry in the Ip Table.
     * @param address Ip address of the entry
     * @param port port of the entry
     * @return whether or not the Ip Table is full, indicating discovery completion
     */
    public boolean addEntry(InetAddress address, int port) {
        table.put(address.toString() + port, new IpTableEntry(address, port, true));
        boolean ret = false;
        if (table.size() == numRingos) {
            ret = true;
        }
        return ret;
    }

    /**
     * Getter for number of ringos
     * @return number of ringos
     */
    public int getNumRingos() {
        return numRingos;
    }

    /**
     * Gets all of the Ip Table excluding the entry specified. This allows for the current node to get all others
     * without having to send to itself
     * @param address_to_exclude Ip address of the ringo to exclude from the results
     * @param exclude_port Associated port number
     * @return an array of all of the entries without the excluded ringo
     */
    public ArrayList<IpTableEntry> getTargetsExcludingOne(InetAddress address_to_exclude, int exclude_port) {
        ArrayList<IpTableEntry> arrayList = new ArrayList<>();
        for (Map.Entry<String, IpTableEntry> entry: table.entrySet()) {
            if ( !(entry.getValue().getAddress().equals(address_to_exclude) && (entry.getValue().getPort() == exclude_port))) {
                arrayList.add(entry.getValue());
            }
        }
        return arrayList;
    }

    /**
     * getter for the internal hashmap of entries
     * @return internal hashmap
     */
    public HashMap<String, IpTableEntry> getTable() {
        return table;
    }

    /**
     * merges an IpTable object with this IpTable object in order to form a more complete understanding of existing
     * ringos in the network
     * @param newtable table to merge with the current table
     * @return whether or not the current table is now full as a result of the merge
     */
    public boolean merge(IpTable newtable) {
        if (newtable == null) {
            System.out.println("error in merge");
            return false;
        }
        boolean retval = false;
        for (Map.Entry<String, IpTableEntry> entry : newtable.getTable().entrySet()) {
            try {
                if (!table.containsKey(entry.getKey())) {
                    retval = addEntry(entry.getValue().getAddress(), entry.getValue().getPort());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return retval;
    }

    /**
     * pretty print the Ip Table.
     */
    public void printTable() {
        System.out.println("IP Table:");
        for (Map.Entry<String, IpTableEntry> entry : table.entrySet()) {
            System.out.println(entry.getValue().getAddress() + ":" + entry.getValue().getPort());
        }
    }

}
