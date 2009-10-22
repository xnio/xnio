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
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
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
import org.jboss.xnio.Option;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.Options;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.channels.UdpChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.management.UdpServerMBean;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

/**
 *
 */
class NioUdpServer implements UdpServer {

    private static final Logger log = Logger.getLogger("org.jboss.xnio.nio.udp.server");

    private final NioXnio nioXnio;
    private final Executor executor;

    private final Object lock = new Object();
    private final Set<NioUdpChannel> boundChannels = new LinkedHashSet<NioUdpChannel>();

    private volatile ChannelListener<? super UdpChannel> bindListener = null;
    private volatile ChannelListener<? super UdpServer> closeListener = null;

    private static final AtomicReferenceFieldUpdater<NioUdpServer, ChannelListener> bindListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(NioUdpServer.class, ChannelListener.class, "bindListener");
    private static final AtomicReferenceFieldUpdater<NioUdpServer, ChannelListener> closeListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(NioUdpServer.class, ChannelListener.class, "closeListener");

    private final ChannelListener.Setter<UdpChannel> bindSetter = IoUtils.getSetter(this, bindListenerUpdater);
    private final ChannelListener.Setter<UdpServer> closeSetter = IoUtils.getSetter(this, closeListenerUpdater);

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

    NioUdpServer(final NioXnio nioXnio, final Executor executor, final ChannelListener<? super UdpChannel> bindListener, final OptionMap optionMap) {
        synchronized (lock) {
            this.nioXnio = nioXnio;
            this.executor = executor;
            this.bindListener = bindListener;
            reuseAddress = optionMap.get(Options.REUSE_ADDRESSES);
            receiveBufferSize = optionMap.get(Options.RECEIVE_BUFFER);
            receiveBufferSize = optionMap.get(Options.RECEIVE_BUFFER);
            sendBufferSize = optionMap.get(Options.SEND_BUFFER);
            trafficClass = optionMap.get(Options.IP_TRAFFIC_CLASS);
            broadcast = optionMap.get(Options.BROADCAST);
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
            .add(Options.RECEIVE_BUFFER)
            .add(Options.REUSE_ADDRESSES)
            .add(Options.SEND_BUFFER)
            .add(Options.IP_TRAFFIC_CLASS)
            .add(Options.BROADCAST)
            .create();

    public boolean supportsOption(final Option<?> option) {
        return OPTIONS.contains(option);
    }

    public <T> T getOption(final Option<T> option) throws UnsupportedOptionException, IOException {
        if (Options.RECEIVE_BUFFER.equals(option)) {
            return option.cast(receiveBufferSize);
        } else if (Options.REUSE_ADDRESSES.equals(option)) {
            return option.cast(reuseAddress);
        } else if (Options.SEND_BUFFER.equals(option)) {
            return option.cast(sendBufferSize);
        } else if (Options.IP_TRAFFIC_CLASS.equals(option)) {
            return option.cast(trafficClass);
        } else if (Options.BROADCAST.equals(option)) {
            return option.cast(broadcast);
        } else {
            return null;
        }
    }

    public <T> Configurable setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        if (Options.RECEIVE_BUFFER.equals(option)) {
            receiveBufferSize = Options.RECEIVE_BUFFER.cast(value);
        } else if (Options.REUSE_ADDRESSES.equals(option)) {
            reuseAddress = Options.REUSE_ADDRESSES.cast(value);
        } else if (Options.SEND_BUFFER.equals(option)) {
            sendBufferSize = Options.SEND_BUFFER.cast(value);
        } else if (Options.IP_TRAFFIC_CLASS.equals(option)) {
            trafficClass = Options.IP_TRAFFIC_CLASS.cast(value);
        } else if (Options.BROADCAST.equals(option)) {
            broadcast = Options.BROADCAST.cast(value);
        }
        return this;
    }

    public ChannelListener.Setter<UdpChannel> getBindSetter() {
        return bindSetter;
    }

    public ChannelListener.Setter<UdpServer> getCloseSetter() {
        return closeSetter;
    }

    public String toString() {
        return String.format("UDP server (NIO) <%s>", Integer.toHexString(hashCode()));
    }

    public Collection<UdpChannel> getChannels() {
        synchronized (lock) {
            return new ArrayList<UdpChannel>(boundChannels);
        }
    }

    public IoFuture<UdpChannel> bind(final InetSocketAddress address) {
        synchronized (lock) {
            try {
                if (closed) {
                    throw new ClosedChannelException();
                }
                final DatagramChannel datagramChannel = DatagramChannel.open();
                datagramChannel.configureBlocking(false);
                final DatagramSocket socket = datagramChannel.socket();
                if (broadcast != null) socket.setBroadcast(broadcast.booleanValue());
                if (receiveBufferSize != null) socket.setReceiveBufferSize(receiveBufferSize.intValue());
                if (sendBufferSize != null) socket.setSendBufferSize(sendBufferSize.intValue());
                if (reuseAddress != null) socket.setReuseAddress(reuseAddress.booleanValue());
                if (trafficClass != null) socket.setTrafficClass(trafficClass.intValue());
                socket.bind(address);
                final NioUdpChannel udpSocketChannel = createChannel(datagramChannel);
                final FutureUdpChannel futureUdpChannel = new FutureUdpChannel(udpSocketChannel, datagramChannel);
                boundChannels.add(udpSocketChannel);
                executor.execute(new Runnable() {
                    public void run() {
                        try {
                            bindListener.handleEvent(udpSocketChannel);
                            if (! futureUdpChannel.done()) {
                                IoUtils.safeClose(udpSocketChannel);
                            }
                            log.trace("Successfully bound to %s on %s", address, NioUdpServer.this);
                        } catch (Throwable t) {
                            IoUtils.safeClose(datagramChannel);
                            synchronized (lock) {
                                boundChannels.remove(udpSocketChannel);
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

    NioUdpChannel createChannel(final DatagramChannel datagramChannel) throws IOException {
        return new NioUdpChannel(nioXnio, datagramChannel, executor, globalBytesRead, globalBytesWritten, globalMessagesRead, globalMessagesWritten);
    }

    public boolean isOpen() {
        synchronized (lock) {
            return ! closed;
        }
    }

    public void close() throws IOException {
        synchronized (lock) {
            if (! closed) {
                log.trace("Closing %s", this);
                closed = true;
                final Iterator<NioUdpChannel> it = boundChannels.iterator();
                while (it.hasNext()) {
                    IoUtils.safeClose(it.next());
                    it.remove();
                }
                IoUtils.safeClose(mbeanHandle);
                IoUtils.<UdpServer>invokeChannelListener(this, closeListener);
            }
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
                for (final NioUdpChannel channel : boundChannels) {
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
                            return channel.getLocalAddress();
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
            IoUtils.safeClose(NioUdpServer.this);
        }
    }
}

