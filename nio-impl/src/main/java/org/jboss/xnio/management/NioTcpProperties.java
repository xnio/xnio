/**
 *
 */
package org.jboss.xnio.management;

import java.net.Socket;
import java.net.SocketAddress;

import org.jboss.xnio.nio.NioSocketChannelImpl;

/**
 * 
 */
public class NioTcpProperties extends BasicCounters implements NioTcpPropertiesMBean {

    final Socket socket;

    /**
     * @param nioSocketChannelImpl
     * @param socket
     * @param string
     */
    public NioTcpProperties(final NioSocketChannelImpl nioSocketChannelImpl,
            final Socket socket, final String string) {
        super(nioSocketChannelImpl, string);
        this.socket = socket;
    }

    public String getLocalAddress() {
        SocketAddress sa = socket.getLocalSocketAddress();
        if (sa==null) {
            return null;
        } else {
            return sa.toString();
        }
    }

    public int getLocalPort() {
        return socket.getLocalPort();
    }

    public String getRemoteAddress() {
        SocketAddress sa = socket.getRemoteSocketAddress();
        if (sa==null) {
            return null;
        } else {
            return sa.toString();
        }
    }

    public int getRemotePort() {
        return socket.getPort();
    }

}
