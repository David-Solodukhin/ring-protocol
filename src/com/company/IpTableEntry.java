package com.company;

import java.io.Serializable;
import java.net.InetAddress;

public class IpTableEntry implements Serializable {

    private InetAddress address;
    private int port;
    private boolean alive;

    public IpTableEntry(InetAddress address, int port, boolean alive) {
        this.address = address;
        this.port = port;
        this.alive = alive;
    }

    public int getPort() {
        return port;
    }

    public InetAddress getAddress() {
        return address;
    }
    public boolean getAlive() {
        return alive;
    }

    public void setAddress(InetAddress address) {
        this.address = address;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
