package com.murielkamgang.network;

/**
 * Created by kamga on 3/11/2017.
 */

/**
 * Socket configuration class holder
 *
 * <p>This class will hold basic socket config like port and time out of a socket client/server</p>
 */
public class SocketConfig {

    /**
     * Port of the socket, usually socket server
     */
    public int port;

    /**
     * Socket time out
     */
    public int timeOut;

    public SocketConfig(int port, int timeOut) {
        this.port = port;
        this.timeOut = timeOut;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SocketConfig that = (SocketConfig) o;

        if (port != that.port) return false;
        return timeOut == that.timeOut;

    }

    @Override
    public int hashCode() {
        int result = port;
        result = 31 * result + timeOut;
        return result;
    }
}
