package com.company;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Set;

/**
 * Created by David on 3/10/2018.
 */
public class RttVector implements Serializable {
    private HashMap<String, Integer> RTTs = new HashMap<>();
    private String srcIp;

    /**
     * Constructor for the Rtt vector
     * @param numRingos number of ringos in the network
     * @param srcIp ip address of the source
     */
    public RttVector(int numRingos, String srcIp) {

        this.srcIp = srcIp;
    }

    /**
     * adds a new RTT value to the vector
     * @param dst associated destination address
     * @param rtt return time
     */
    public void pushRTT(String dst, int rtt) {
        RTTs.put(dst, rtt);
    }

    /**
     * getter for the destination ips
     * @return set of destination ips
     */
    public Set<String> getIps(){
        return RTTs.keySet();
    }

    /**
     * getter for the Rtt of a destination address
     * @param dst destination address
     * @return rtt value
     */
    public int getRTT(String dst) {
        return RTTs.get(dst);
    }

    /**
     * gets this vectors source ip address
     * @return source ip
     */
    public String getSrcIp() {
        return srcIp;
    }

    /**
     * gets a pretty print of the vector
     * @return the vector as a string
     */
    public String printVector() {
        String z = "";
        z+="|VsrcIp=" + srcIp +"|\n";
        for (String entry: RTTs.keySet()) {
            z+=entry+": " + RTTs.get(entry)+"\n";
        }
        return z;
    }

    public void removeEntry(String ip) {
        RTTs.remove(ip);
    }
}
