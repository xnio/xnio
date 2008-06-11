package org.jboss.xnio.channels;

/**
 *
 */
public final class SocketOption {
    private SocketOption() {}

    public static final String IP_TOS = "IPPROTO_IP.IP_TOS";

    public static final String SO_BROADCAST = "SOL_SOCKET.SO_BROADCAST";

    public static final String SO_LINGER = "SOL_SOCKET.SO_LINGER";

    public static final String SO_RCVBUF = "SOL_SOCKET.SO_RCVBUF";

    public static final String SO_REUSEADDR = "SOL_SOCKET.SO_REUSEADDR";

    public static final String SO_SNDBUF = "SOL_SOCKET.SO_SNDBUF";

    public static final String TCP_NODELAY = "IPPROTO_TCP.TCP_NODELAY";
}
