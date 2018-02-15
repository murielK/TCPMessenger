package com.murielkamgang.network;

/**
 * Created by kamga on 3/11/2017.
 */

/**
 * Constant holder for default constant value
 */
public class Constant {

    /**
     * Flag to enable logs, I know this is bad as it will require useless dependency of this Class.
     */
    static final boolean ENABLE_LOGS = false; //This is maybe not so good
    /**
     * Default tcp socket server port
     */
    static final int TCP_PORT = 49152;

    /**
     * Default client socket time out, for now it is set to 3000ms because i believe it is best for this
     * project, although it can be override anytime to fit your need
     */
    static final int DEFAULT_TIME_OUT = 3000;

}
