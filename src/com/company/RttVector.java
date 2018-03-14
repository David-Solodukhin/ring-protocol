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
    private int numRingos;
    public RttVector(int numRingos, String srcIp) {
        this.numRingos = numRingos;
        this.srcIp = srcIp;
    }

    public void pushRTT(String dst, int rtt) {
        RTTs.put(dst, rtt);
    }
    public Set<String> getIps(){
        return RTTs.keySet();
    }
    public int getRTT(String dst) {
        return RTTs.get(dst);
    }
    public String getSrcIp() {
        return srcIp;
    }
    public String printVector() {
        String z = "";
        z+="|VsrcIp=" + srcIp +"|\n";
        for (String entry: RTTs.keySet()) {
            z+=entry+": " + RTTs.get(entry)+"\n";
        }
        return z;
    }
}
