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

package org.xnio.nio;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.jboss.logging.Logger;
import org.xnio.Option;
import org.xnio.ChannelListener;
import org.xnio.Options;
import org.xnio.XnioWorker;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.UnsupportedOptionException;
import org.xnio.channels.BoundChannel;

/**
 *
 */
final class NioTcpChannel extends AbstractNioStreamChannel<NioTcpChannel> implements ConnectedStreamChannel {

    private static final Logger log = Logger.getLogger("org.xnio.nio.tcp.channel");

    private final SocketChannel socketChannel;
    private final Socket socket;
    private final NioTcpServer server;

    private volatile int closeBits = 0;

    private static final AtomicIntegerFieldUpdater<NioTcpChannel> closeBitsUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpChannel.class, "closeBits");

    private static final Set<Option<?>> OPTIONS = Option.setBuilder()
            .add(Options.CLOSE_ABORT)
            .add(Options.KEEP_ALIVE)
            .add(Options.TCP_OOB_INLINE)
            .add(Options.RECEIVE_BUFFER)
            .add(Options.SEND_BUFFER)
            .add(Options.TCP_NODELAY)
            .add(Options.IP_TRAFFIC_CLASS)
            .create();

    NioTcpChannel(final NioXnioWorker worker, final NioTcpServer server, final SocketChannel socketChannel) throws ClosedChannelException {
        super(worker);
        this.socketChannel = socketChannel;
        this.server = server;
        socket = socketChannel.socket();
        start();
    }

    BoundChannel getBoundChannel() {
        return new BoundChannel() {
            public SocketAddress getLocalAddress() {
                return NioTcpChannel.this.getLocalAddress();
            }

            public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
                final SocketAddress address = getLocalAddress();
                return type.isInstance(address) ? type.cast(address) : null;
            }

            public ChannelListener.Setter<? extends BoundChannel> getCloseSetter() {
                return NioTcpChannel.this.getCloseSetter();
            }

            public boolean isOpen() {
                return NioTcpChannel.this.isOpen();
            }

            public void close() throws IOException {
                NioTcpChannel.this.close();
            }

            public boolean supportsOption(final Option<?> option) {
                return NioTcpChannel.this.supportsOption(option);
            }

            public <T> T getOption(final Option<T> option) throws IOException {
                return NioTcpChannel.this.getOption(option);
            }

            public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
                return NioTcpChannel.this.setOption(option, value);
            }

            public XnioWorker getWorker() {
                return NioTcpChannel.this.getWorker();
            }
        };
    }

    public boolean isOpen() {
        return socketChannel.isOpen();
    }

    protected SocketChannel getReadChannel() {
        return socketChannel;
    }

    protected SocketChannel getWriteChannel() {
        return socketChannel;
    }

    private static int setBits(NioTcpChannel instance, int bits) {
        int old;
        int updated;
        do {
            old = instance.closeBits;
            updated = old | bits;
            if (updated == old) {
                break;
            }
        } while (! closeBitsUpdater.compareAndSet(instance, old, updated));
        return old;
    }

    public void close() throws IOException {
        if (setBits(this, 0x03) != 0x03) {
            log.tracef("Closing %s", this);
            try {
                socketChannel.close();
                if (server != null) server.channelClosed();
            } finally {
                cancelReadKey();
                cancelWriteKey();
                invokeCloseHandler();
            }
        }
    }

    public void shutdownReads() throws IOException {
        final int old = setBits(this, 0x02);
        if ((old & 0x02) == 0) {
            try {
                log.tracef("Shutting down reads on %s", this);
                socket.shutdownInput();
            } catch (IOException ignored) {
            } finally {
                cancelReadKey();
                if (old == 0x01) {
                    invokeCloseHandler();
                }
            }
        }
    }

    public void shutdownWrites() throws IOException {
        final int old = setBits(this, 0x01);
        if ((old & 0x01) == 0) {
            try {
                log.tracef("Shutting down writes on %s", this);
                socket.shutdownOutput();
            } catch (IOException ignored) {
            } finally {
                cancelWriteKey();
                if (old == 0x02) {
                    invokeCloseHandler();
                }
            }
        }
    }

    public SocketAddress getPeerAddress() {
        return socket.getRemoteSocketAddress();
    }

    public <A extends SocketAddress> A getPeerAddress(final Class<A> type) {
        final SocketAddress address = getPeerAddress();
        return type.isInstance(address) ? type.cast(address) : null;
    }

    public SocketAddress getLocalAddress() {
        return socket.getLocalSocketAddress();
    }

    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        final SocketAddress address = getLocalAddress();
        return type.isInstance(address) ? type.cast(address) : null;
    }

    public boolean supportsOption(final Option<?> option) {
        return OPTIONS.contains(option);
    }

    public <T> T getOption(final Option<T> option) throws UnsupportedOptionException, IOException {
        if (option == Options.CLOSE_ABORT) {
            return option.cast(Boolean.valueOf(socket.getSoLinger() != -1));
        } else if (option == Options.KEEP_ALIVE) {
            return option.cast(Boolean.valueOf(socket.getKeepAlive()));
        } else if (option == Options.TCP_OOB_INLINE) {
            return option.cast(Boolean.valueOf(socket.getOOBInline()));
        } else if (option == Options.RECEIVE_BUFFER) {
            return option.cast(Integer.valueOf(socket.getReceiveBufferSize()));
        } else if (option == Options.SEND_BUFFER) {
            return option.cast(Integer.valueOf(socket.getSendBufferSize()));
        } else if (option == Options.TCP_NODELAY) {
            return option.cast(Boolean.valueOf(socket.getTcpNoDelay()));
        } else if (option == Options.IP_TRAFFIC_CLASS) {
            return option.cast(Integer.valueOf(socket.getTrafficClass()));
        } else {
            return super.getOption(option);
        }
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        final Object old;
        if (option == Options.CLOSE_ABORT) {
            old = Boolean.valueOf(socket.getSoLinger() != 0);
            socket.setSoLinger(((Boolean) value).booleanValue(), 0);
        } else if (option == Options.KEEP_ALIVE) {
            old = Boolean.valueOf(socket.getKeepAlive());
            socket.setKeepAlive(((Boolean) value).booleanValue());
        } else if (option == Options.TCP_OOB_INLINE) {
            old = Boolean.valueOf(socket.getOOBInline());
            socket.setOOBInline(((Boolean) value).booleanValue());
        } else if (option == Options.RECEIVE_BUFFER) {
            old = Integer.valueOf(socket.getReceiveBufferSize());
            socket.setReceiveBufferSize(((Integer) value).intValue());
        } else if (option == Options.SEND_BUFFER) {
            old = Integer.valueOf(socket.getSendBufferSize());
            socket.setSendBufferSize(((Integer) value).intValue());
        } else if (option == Options.TCP_NODELAY) {
            old = Boolean.valueOf(socket.getTcpNoDelay());
            socket.setTcpNoDelay(((Boolean) value).booleanValue());
        } else if (option == Options.IP_TRAFFIC_CLASS) {
            old = Integer.valueOf(socket.getTrafficClass());
            socket.setTrafficClass(((Integer) value).intValue());
        } else {
            return super.setOption(option, value);
        }
        return option.cast(old);
    }

    @Override
    public String toString() {
        return String.format("TCP socket channel (NIO) <%h>", this);
    }
}