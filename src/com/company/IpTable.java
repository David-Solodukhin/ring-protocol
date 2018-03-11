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

    public IpTable(int numRingos) {
        this.numRingos = numRingos;
        this.table = new HashMap<>();
    }

    public boolean addEntry(InetAddress address, int port) {
        table.put(address.toString() + port, new IpTableEntry(address, port, true));
        boolean ret = false;
        if (table.size() == numRingos) {
            ret = true;
        }
        return ret;
    }

    public ArrayList<IpTableEntry> getTargetsExcludingOne(InetAddress address_to_exclude, int exclude_port) {
        ArrayList<IpTableEntry> arrayList = new ArrayList<>();
        for (Map.Entry<String, IpTableEntry> entry: table.entrySet()) {
            if ( (!entry.getValue().getAddress().equals(address_to_exclude)) && (entry.getValue().getPort() != exclude_port)) {
                arrayList.add(entry.getValue());
            }
        }
        return arrayList;
    }
}
