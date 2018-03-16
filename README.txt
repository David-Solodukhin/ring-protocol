Ringo milestone 2 - March 16, 2018
Daniel Pagan: dpagan6@gatech.edu
David Solodukhin: dsolodukhin3@gatech.edu

Compiling:
    make
    java -jar ringo.jar <flag> <local-port> <PoC-name> <PoC-port> <N>

Running:
    Once UI is started you will have access to:
        -> show-matrix
        -> show-ring
        -> disconnect

Files Submitted:
.
├── Makefile - gnu make file that builds a runnable jar program
├── README.md - readme for the github
├── README.txt - this readme for usage of the software
├── sample.txt - sample output for a running Ringo
└── src
    └── com
        └── company
            ├── IpTableEntry.java - Class to provide abstraction for the entries in the Ip Table
            ├── IpTable.java - Class that represents the IpTable
            ├── Listener.java - Thread class that listens for the incoming packets and reacts to them based on their header
            ├── Main.java - Command Line program wrapper around the Ringo
            ├── Ringo.java - Ringo class that handles initialization of listener thread and contacting the Point of Contact
            ├── RingoProtocol.java - Static functions that provide a java interface to the Ringo internal packet headers and sending conventions
            ├── RttTable.java - Class that represents the Rount Trip Time measurements stored for the Ringo
            └── RttVector.java - Class that represents a single vector for the RttTable in order to abstract the internals

Design Documentation:
    See ./DesignDoc.pdf
