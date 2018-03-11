package com.company;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class IpTable implements Serializable {
    private HashMap<String, IpTableEntry> table;

    private int numRingos;

    public IpTable(int numRingos, int local_port) {
        this.numRingos = numRingos;
        this.table = new HashMap<>();
        try {
            addEntry(InetAddress.getLocalHost(), local_port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean addEntry(InetAddress address, int port) {
        table.put(address.toString() + port, new IpTableEntry(address, port, true));
        boolean ret = false;
        if (table.size() == numRingos) {
            ret = true;
        }
        return ret;
    }

    public int getNumRingos() {
        return numRingos;
    }

    public ArrayList<IpTableEntry> getTargetsExcludingOne(InetAddress address_to_exclude, int exclude_port) {
        ArrayList<IpTableEntry> arrayList = new ArrayList<>();
        for (Map.Entry<String, IpTableEntry> entry: table.entrySet()) {
            if ( (!entry.getValue().getAddress().equals(address_to_exclude)) || (entry.getValue().getPort() != exclude_port)) {
                arrayList.add(entry.getValue());
            }
        }
        return arrayList;
    }

    public HashMap<String, IpTableEntry> getTable() {
        return table;
    }

    public boolean merge(IpTable newtable) {
        System.out.println("merging");
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

    public void printTable() {
        System.out.println("IP Table:");
        for (Map.Entry<String, IpTableEntry> entry : table.entrySet()) {
            System.out.println(entry.getValue().getAddress() + ":" + entry.getValue().getPort());
        }
    }

}
