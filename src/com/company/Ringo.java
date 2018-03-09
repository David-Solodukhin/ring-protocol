package com.company;

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
        contactPoC();
        waitForOptimumRing();
        startUI();
    }

    private void startListener() {

    }

    private void contactPoC() {

    }

    private void waitForOptimumRing() {

    }

    private void startUI() {

    }
}
