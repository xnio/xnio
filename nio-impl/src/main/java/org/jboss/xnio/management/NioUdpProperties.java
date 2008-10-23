/**
 *
 */
package org.jboss.xnio.management;

import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Set;

import org.jboss.xnio.nio.NioUdpSocketChannelImpl;

/**
 * 
 */
public class NioUdpProperties extends BasicCounters implements NioUdpPropertiesMBean {

    final DatagramSocket socket;
    final Set<SocketAddress> remoteAddresses = new HashSet<SocketAddress>();

    /**
     * @param nioUdpSocketChannelImpl
     * @param socket
     * @param string
     */
    public NioUdpProperties(final NioUdpSocketChannelImpl nioUdpSocketChannelImpl,
            final DatagramSocket socket, final String string) {
        super(nioUdpSocketChannelImpl, string);
        this.socket = socket;
    }

    public void bytesRead(final SocketAddress sourceAddress, final long byteCount) {
        super.bytesRead(byteCount);
        // Hash computation on every read - performance killer?
        remoteAddresses.add(sourceAddress);
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

    public String[] getRemoteAddresses() {
        SocketAddress[] sa = remoteAddresses.toArray(new SocketAddress[0]);
        String[] sas = new String[sa.length];
        for (int i = 0; i < sa.length; i++) {
            sas[i] = sa[i].toString();
        }
        return sas;
    }
}
