package com.company;

public class Main {

    /**
     * prints out the usage for the program when a user does not input correct command line parameters
     */
    private static void usage() {
        System.out.println("Usage: ringo <flag> <local-port> <PoC-name> <PoC-port> <N>");
    }

    /**
     * starting function for the app. Constructs ringo and calls its startup function.
     * @param args command line arguments
     */
    public static void main(String[] args) {
        String flag;
        int local_port;
        String poc_name;
        int poc_port;
        int num_ringos;
        if (args.length != 5) {
            usage();
            System.exit(-1);
        }
        flag = args[0];
        local_port = Integer.parseInt(args[1]);
        poc_name = args[2];
        poc_port = Integer.parseInt(args[3]);
        num_ringos = Integer.parseInt(args[4]);
        Ringo ringo = new Ringo(flag, local_port, num_ringos);
        ringo.startup(poc_name, poc_port);

    }

    /**public static int getShortestHamiltonianCycle(int[][] dist) {
        int n = dist.length;
        int[][] dp = new int[1 << n][n]; //2^n cells containing n entries. Literal magic. Donald Knuth would be proud.
        for (int[] d : dp)
            Arrays.fill(d, Integer.MAX_VALUE / 2);
        dp[1][0] = 0;
        for (int mask = 1; mask < 1 << n; mask += 2) {
            for (int i = 1; i < n; i++) {
                if ((mask & 1 << i) != 0) {
                    for (int j = 0; j < n; j++) {
                        if ((mask & 1 << j) != 0) {
                            dp[mask][i] = Math.min(dp[mask][i], dp[mask ^ (1 << i)][j] + dist[j][i]);
                        }
                    }
                }
            }
        }
        int res = Integer.MAX_VALUE;
        for (int i = 1; i < n; i++) {
            res = Math.min(res, dp[(1 << n) - 1][i] + dist[i][0]);
        }

        // reconstruct path
        int cur = (1 << n) - 1;
        int[] order = new int[n];
        int last = 0;
        for (int i = n - 1; i >= 1; i--) {
            int bj = -1;
            for (int j = 1; j < n; j++) {
                if ((cur & 1 << j) != 0 && (bj == -1 || dp[cur][bj] + dist[bj][last] > dp[cur][j] + dist[j][last])) {
                    bj = j;
                }
            }
            order[i] = bj;
            cur ^= 1 << bj;
            last = bj;
        }
        System.out.println(Arrays.toString(order));
        return res;
    }

    // Usage example
    public static void main(String[] args) {
        int[][] dist = { { 0, 1, 10, 1, 10 }, { 1, 0, 10, 10, 1 }, { 10, 10, 0, 1, 1 }, { 1, 10, 1, 0, 10 },
                { 10, 1, 1, 10, 0 } }; //RTT is of the form arr[0] = RTT vector from 0 to all other nodes, arr[1] = RTT vector from 1 to all other nodes, etc
        System.out.println(5 == getShortestHamiltonianCycle(dist));
    }**/
}