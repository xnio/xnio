/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.xnio.nio;

import java.io.Closeable;
import java.io.IOException;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.jboss.xnio.FailedIoFuture;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.UdpServer;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Option;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.channels.CommonOptions;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.channels.UdpChannel;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.management.UdpServerMBean;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

/**
 *
 */
final class BioUdpServer implements UdpServer {
    private static final Logger log = Logger.getLogger("org.jboss.xnio.nio.udp.bio-server");

    private volatile ChannelListener<? super UdpChannel> bindListener = null;
    private volatile ChannelListener<? super UdpServer> closeListener = null;

    private static final AtomicReferenceFieldUpdater<BioUdpServer, ChannelListener> bindListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(BioUdpServer.class, ChannelListener.class, "bindListener");
    private static final AtomicReferenceFieldUpdater<BioUdpServer, ChannelListener> closeListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(BioUdpServer.class, ChannelListener.class, "closeListener");

    private final ChannelListener.Setter<UdpChannel> bindSetter = IoUtils.getSetter(this, bindListenerUpdater);
    private final ChannelListener.Setter<UdpServer> closeSetter = IoUtils.getSetter(this, closeListenerUpdater);

    private final Executor executor;

    private final Object lock = new Object();
    private final Set<BioMulticastUdpChannel> boundChannels = new LinkedHashSet<BioMulticastUdpChannel>();

    private boolean closed;

    private Boolean reuseAddress;
    private Integer receiveBufferSize;
    private Integer sendBufferSize;
    private Integer trafficClass;
    private Boolean broadcast;
    private final Closeable mbeanHandle;

    private final AtomicLong globalBytesRead = new AtomicLong();
    private final AtomicLong globalBytesWritten = new AtomicLong();
    private final AtomicLong globalMessagesRead = new AtomicLong();
    private final AtomicLong globalMessagesWritten = new AtomicLong();

    BioUdpServer(final NioXnio nioXnio, final Executor executor, final ChannelListener<? super UdpChannel> bindListener, final OptionMap config) {
        synchronized (lock) {
            this.bindListener = bindListener;
            this.executor = executor;
            reuseAddress = config.get(CommonOptions.REUSE_ADDRESSES);
            receiveBufferSize = config.get(CommonOptions.RECEIVE_BUFFER);
            sendBufferSize = config.get(CommonOptions.SEND_BUFFER);
            trafficClass = config.get(CommonOptions.IP_TRAFFIC_CLASS);
            broadcast = config.get(CommonOptions.BROADCAST);
            Closeable closeable = IoUtils.nullCloseable();
            try {
                closeable = nioXnio.registerMBean(new MBean());
            } catch (NotCompliantMBeanException e) {
                log.trace(e, "Failed to register MBean");
            }
            mbeanHandle = closeable;
        }
    }

    protected static final Set<Option<?>> OPTIONS = Option.setBuilder()
            .add(CommonOptions.RECEIVE_BUFFER)
            .add(CommonOptions.REUSE_ADDRESSES)
            .add(CommonOptions.SEND_BUFFER)
            .add(CommonOptions.IP_TRAFFIC_CLASS)
            .add(CommonOptions.BROADCAST)
            .create();

    public ChannelListener.Setter<UdpChannel> getBindSetter() {
        return bindSetter;
    }

    public ChannelListener.Setter<UdpServer> getCloseSetter() {
        return closeSetter;
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        if (CommonOptions.RECEIVE_BUFFER.equals(option)) {
            return option.cast(receiveBufferSize);
        } else if (CommonOptions.REUSE_ADDRESSES.equals(option)) {
            return option.cast(reuseAddress);
        } else if (CommonOptions.SEND_BUFFER.equals(option)) {
            return option.cast(sendBufferSize);
        } else if (CommonOptions.IP_TRAFFIC_CLASS.equals(option)) {
            return option.cast(trafficClass);
        } else if (CommonOptions.BROADCAST.equals(option)) {
            return option.cast(broadcast);
        } else {
            return null;
        }
    }

    public Set<Option<?>> getOptions() {
        return OPTIONS;
    }

    public <T> Configurable setOption(final Option<T> option, final T value) throws IOException {
        if (CommonOptions.RECEIVE_BUFFER.equals(option)) {
            receiveBufferSize = CommonOptions.RECEIVE_BUFFER.cast(value);
        } else if (CommonOptions.REUSE_ADDRESSES.equals(option)) {
            reuseAddress = CommonOptions.REUSE_ADDRESSES.cast(value);
        } else if (CommonOptions.SEND_BUFFER.equals(option)) {
            sendBufferSize = CommonOptions.SEND_BUFFER.cast(value);
        } else if (CommonOptions.IP_TRAFFIC_CLASS.equals(option)) {
            trafficClass = CommonOptions.IP_TRAFFIC_CLASS.cast(value);
        } else if (CommonOptions.BROADCAST.equals(option)) {
            broadcast = CommonOptions.BROADCAST.cast(value);
        }
        return this;
    }

    public Collection<UdpChannel> getChannels() {
        synchronized (lock) {
            return new ArrayList<UdpChannel>(boundChannels);
        }
    }

    public IoFuture<UdpChannel> bind(final SocketAddress address) {
        synchronized (lock) {
            try {
                if (closed) {
                    throw new ClosedChannelException();
                }
                final MulticastSocket socket = new MulticastSocket(null);
                if (broadcast != null) socket.setBroadcast(broadcast.booleanValue());
                if (receiveBufferSize != null) socket.setReceiveBufferSize(receiveBufferSize.intValue());
                if (sendBufferSize != null) socket.setSendBufferSize(sendBufferSize.intValue());
                if (reuseAddress != null) socket.setReuseAddress(reuseAddress.booleanValue());
                if (trafficClass != null) socket.setTrafficClass(trafficClass.intValue());
                socket.bind(address);
                final BioMulticastUdpChannel channel = new BioMulticastUdpChannel(socket.getSendBufferSize(), socket.getReceiveBufferSize(), executor, socket, globalBytesRead, globalBytesWritten, globalMessagesRead, globalMessagesWritten);
                final FutureUdpChannel futureUdpChannel = new FutureUdpChannel(channel, new Closeable() {
                    public void close() throws IOException {
                        socket.close();
                    }
                });
                boundChannels.add(channel);
                executor.execute(new Runnable() {
                    public void run() {
                        try {
                            bindListener.handleEvent(channel);
                            if (! futureUdpChannel.done()) {
                                IoUtils.safeClose(channel);
                            }
                            channel.open();
                            log.trace("Successfully bound to %s on %s", address, BioUdpServer.this);
                        } catch (Throwable t) {
                            IoUtils.safeClose(socket);
                            synchronized (lock) {
                                boundChannels.remove(channel);
                            }
                            final IOException ioe = new IOException("Failed to open UDP channel: " + t.toString());
                            ioe.initCause(t);
                            if (! futureUdpChannel.setException(ioe)) {
                                // if the operation is cancelled before this point, the exception will be lost
                                log.trace(ioe, "UDP channel open failed, but the operation was cancelled before the exception could be relayed");
                            }
                        }
                    }
                });
                return futureUdpChannel;
            } catch (IOException e) {
                return new FailedIoFuture<UdpChannel>(e);
            }
        }
    }

    public void close() throws IOException {
        synchronized (lock) {
            if (! closed) {
                log.trace("Closing %s", this);
                closed = true;
                IoUtils.safeClose(mbeanHandle);
                final Iterator<BioMulticastUdpChannel> it = boundChannels.iterator();
                while (it.hasNext()) {
                    IoUtils.safeClose(it.next());
                    it.remove();
                }
                IoUtils.<UdpServer>invokeChannelListener(this, closeListener);
            }
        }
    }

    public boolean isOpen() {
        synchronized (lock) {
            return ! closed;
        }
    }

    private final class MBean extends StandardMBean implements UdpServerMBean {

        private MBean() throws NotCompliantMBeanException {
            super(UdpServerMBean.class);
        }

        public Channel[] getBoundChannels() {
            synchronized (lock) {
                final Channel[] channels = new Channel[boundChannels.size()];
                int i = 0;
                for (final BioMulticastUdpChannel channel : boundChannels) {
                    channels[i++] = new Channel() {
                        public long getBytesRead() {
                            return channel.bytesRead.get();
                        }

                        public long getBytesWritten() {
                            return channel.bytesWritten.get();
                        }

                        public long getMessagesRead() {
                            return channel.messagesRead.get();
                        }

                        public long getMessagesWritten() {
                            return channel.messagesWritten.get();
                        }

                        public InetSocketAddress getBindAddress() {
                            return (InetSocketAddress) channel.getLocalAddress();
                        }

                        public void close() {
                            IoUtils.safeClose(channel);
                        }
                    };
                }
                return channels;
            }
        }

        public long getBytesRead() {
            return globalBytesRead.get();
        }

        public long getBytesWritten() {
            return globalBytesWritten.get();
        }

        public long getMessagesRead() {
            return globalMessagesRead.get();
        }

        public long getMessagesWritten() {
            return globalMessagesWritten.get();
        }

        public void close() {
            IoUtils.safeClose(BioUdpServer.this);
        }
    }
}
