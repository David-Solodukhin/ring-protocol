package com.company;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;

public class IpTable implements Serializable {
    private HashMap<String, IpTableEntry> table;

    private int numRingos;

    public IpTable(int numRingos) {
        this.numRingos = numRingos;
        this.table = new HashMap<>();
    }

    public void addEntry(InetAddress address, int port) {
        table.put(address.toString() + port, new IpTableEntry(address, port, true));
    }

    public ArrayList<IpTableEntry> get
    }
}
