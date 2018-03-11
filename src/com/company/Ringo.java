package com.company;

import java.io.PrintWriter;
import java.net.Socket;

public class Ringo {
    private int local_port;
    private String mode;
    private int num_ringos;

    public Ringo(String mode, int local_port, int num_ringos) {
        this.mode = mode;
        this.local_port = local_port;
        this.num_ringos = num_ringos;
    }

    public void startup(String poc_name, int poc_port) {
        startListener();
        contactPoC(poc_name, poc_port);
        waitForOptimumRing();
        startUI();
    }

    private void startListener() {


    }

    private void contactPoC(String poc_name, int poc_port) {
        if (poc_name.equals("0") || poc_port == 0) {
            return;
        }
        try {
            RingoProtocol.sendNewNode(poc_name, poc_port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void waitForOptimumRing() {

    }

    private void startUI() {

    }
}
