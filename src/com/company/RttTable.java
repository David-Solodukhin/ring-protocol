package com.company;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;


public class RttTable implements Serializable {
    //private ArrayList<RttVector> test = new ArrayList<>();
    private HashMap<String, RttVector> table = new HashMap<>();
    private HashMap<String, Integer> map = new HashMap<>();

    private HashMap<Integer, String> inverseMap = new HashMap<>(); //for ease of use
    int numRingos = 0;


    public RttTable(int numRingos) {
        this.numRingos = numRingos;
        //technically nothing needs to be done here for now
       /* while (i != 0) {
            table.put("UNUSED"+i, new RttVector(numRingos));
        }
       */
    }
    public RttVector getVector(String ip) {
        return table.get(ip);
    }
    public void pushVector(String ip, RttVector vec) {
        table.put(ip, vec);
    }
    public void pushEntry(String src, String dst, int RTT) {
        RttVector vec = table.get(src);
        vec.pushRTT(dst, RTT);
        vec = table.get(dst); //just in case this makes it easier to iterate through later on
        vec.pushRTT(src, RTT);
    }
    public Set<String> getIps() {
        return table.keySet();
    }
    public void merge(RttTable t) {
        for (String ip: t.getIps()) {
            RttVector tvec = t.getVector(ip);
            RttVector svec;
            if ((svec = this.getVector(ip)) == null) { //if we don't have any rtt's associated with this src ip, just copy the whole thing

                pushVector(ip, tvec);
            }


            /*else { //if we have more information than sender, we don't want to lose our information so we must selectively copy(JIC because of threading)
                System.out.println(svec.printVector());
                for (String ip2: tvec.getIps()) { //loop through dst ips of vector from received table
                    pushEntry(ip,ip2, tvec.getRTT(ip2)); //this way we only add whats in tvec and don't remove our entries. this is overkill but jic
                }
            }*/
        }
    }

    public boolean isComplete() {
        if (getIps().size() != numRingos) {
            return false;
        }
        /*for (String ip : getIps()) {
            if (getVector(ip).getIps().size() != numRingos - 1) {
                return false;
            }
        }*/
        return true;
    }
    public HashMap<Integer, String> getInverseMap() {
        return inverseMap;
    }
    public void formMap() {
        int i = 0;
        for (String ip: Ringo.rtt_table.getIps()) {
            map.put(ip, i); //simple map to make this easier
            inverseMap.put(i, ip);
            i++;
        }
    }
    public String[][] test() {
        Ringo.rtt_converted = convert();
        String[][] res = new String[getIps().size()][getIps().size()];
        for (int i = 0; i < getIps().size(); i++) {
            for (int x = 0; x < getIps().size(); x++) {
                res[i][x] = "src: " + inverseMap.get(i)+" <-> "+"dst: "+ inverseMap.get(x)+" = "+ Ringo.rtt_converted[i][x]+"ms";
            }
        }
        return res;
    }
    public int[][] convert() {
        int[][] result = new int[numRingos][numRingos];
        //hashmap[ip] = hashmap of ips and ints
        formMap();

        for (String ipsrc: Ringo.rtt_table.getIps()) {
            RttVector vec = Ringo.rtt_table.getVector(ipsrc);
            for (String ipdst: vec.getIps()) {

                //System.out.println(map.get(ipsrc) + " " + map.get(ipdst));

                result[map.get(ipsrc)][map.get(ipdst)] = vec.getRTT(ipdst);
            }
        }
        //NOTE: arr[i][i] = 0 doesn't have to be explicitly stored anywhere since the array is default initialized all to zeros and any unfilled entries will remain zero


        //remove any two way standard error
        //TODO: actually update rtt_table too
        for (int i = 0; i < result.length; i++) {
            for (int x = 0; x < result.length; x++) {
                result[i][x] = Math.min(result[x][i], result[i][x]);
            }
        }

        return result;
    }
}
