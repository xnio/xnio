package org.jboss.xnio.core.nio;

import org.jboss.xnio.channels.MulticastDatagramChannel;
import org.jboss.xnio.channels.MultipointDatagramChannel;
import org.jboss.xnio.IoHandler;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.MulticastSocket;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
public final class BioMulticastChannelImpl extends BioDatagramChannelImpl implements MulticastDatagramChannel {
    private final MulticastSocket multicastSocket;

    @SuppressWarnings({"unchecked"})
    protected BioMulticastChannelImpl(final int sendBufSize, final int recvBufSize, final Executor handlerExecutor, final IoHandler<? super MulticastDatagramChannel> handler, final MulticastSocket multicastSocket) {
        super(sendBufSize, recvBufSize, handlerExecutor, (IoHandler<? super MultipointDatagramChannel<SocketAddress>>) handler, multicastSocket);
        this.multicastSocket = multicastSocket;
    }

    public Key join(final InetAddress group, final NetworkInterface iface) throws IOException {
        return new BioKey(iface, group);
    }

    public Key join(final InetAddress group, final NetworkInterface iface, final InetAddress source) throws IOException {
        throw new UnsupportedOperationException("source filtering not supported");
    }

    private final class BioKey implements Key {

        private final AtomicBoolean openFlag = new AtomicBoolean(true);
        private final NetworkInterface networkInterface;
        private final InetAddress group;

        private BioKey(final NetworkInterface networkInterface, final InetAddress group) throws IOException {
            this.networkInterface = networkInterface;
            this.group = group;
            multicastSocket.joinGroup(new InetSocketAddress(group, 0), networkInterface);
        }

        public Key block(final InetAddress source) throws IOException {
            throw new UnsupportedOperationException("source filtering not supported");
        }

        public Key unblock(final InetAddress source) throws IOException {
            // no operation
            return this;
        }

        public MulticastDatagramChannel getChannel() {
            return BioMulticastChannelImpl.this;
        }

        public InetAddress getGroup() {
            return group;
        }

        public NetworkInterface getNetworkInterface() {
            return networkInterface;
        }

        public InetAddress getSourceAddress() {
            return null;
        }

        public boolean isOpen() {
            return openFlag.get();
        }

        public void close() throws IOException {
            if (openFlag.getAndSet(false)) {
                multicastSocket.leaveGroup(new InetSocketAddress(group, 0), networkInterface);
            }
        }
    }
}
