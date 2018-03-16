package com.company;

import java.io.Serializable;
import java.net.InetAddress;

public class IpTableEntry implements Serializable {

    private InetAddress address;
    private int port;
    private boolean alive;

    /**
     * Constructor to build an entry for the Ip Table
     * @param address Ip address of the entry's ringo
     * @param port associated port
     * @param alive whether or not that ringo is alive (used for keep-alive later in project)
     */
    public IpTableEntry(InetAddress address, int port, boolean alive) {
        this.address = address;
        this.port = port;
        this.alive = alive;
    }

    /**
     * getter for the port
     * @return port number
     */
    public int getPort() {
        return port;
    }

    /**
     * getter for the ip address
     * @return ip address
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * getter for whether this entry is known to be alive
     * @return alive boolean
     */
    public boolean getAlive() {
        return alive;
    }

    /**
     * setter for the ip address of the entry
     * @param address ip address
     */
    public void setAddress(InetAddress address) {
        this.address = address;
    }

    /**
     * setter for the alive boolean
     * @param alive what to set it to
     */
    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    /**
     * setter for the port number
     * @param port desired port number
     */
    public void setPort(int port) {
        this.port = port;
    }
}
