/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import org.xnio.OptionMap;
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
    }

    void configureFrom(final OptionMap optionMap) throws IOException {
        for (Option<?> option : optionMap) {
            if (supportsOption(option)) {
                doSetOption(option, optionMap);
            }
        }
    }

    private <T> void doSetOption(Option<T> option, OptionMap map) throws IOException {
        setOption(option, option.cast(map.get(option)));
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
                try { cancelReadKey(); } catch (Throwable ignored) {}
                try { cancelWriteKey(); } catch (Throwable ignored) {}
                socketChannel.close();
            } finally {
                if (server != null) server.channelClosed();
                invokeCloseHandler();
            }
        }
    }

    public void shutdownReads() throws IOException {
        final int old = setBits(this, 0x02);
        if ((old & 0x02) == 0) {
            log.tracef("Shutting down reads on %s", this);
            try {
                try { cancelReadKey(); } catch (Throwable ignored) {}
                try { socket.shutdownInput(); } catch (IOException ignored) {}
            } finally {
                if (old == 0x01) {
                    try {
                        socketChannel.close();
                    } finally {
                        invokeCloseHandler();
                    }
                }
            }
        }
    }

    public void shutdownWrites() throws IOException {
        final int old = setBits(this, 0x01);
        if ((old & 0x01) == 0) {
            log.tracef("Shutting down writes on %s", this);
            try {
                try { cancelWriteKey(); } catch (Throwable ignored) {}
                try { socket.shutdownOutput(); } catch (Throwable ignored) {}
            } finally {
                if (old == 0x02) {
                    try {
                        socketChannel.close();
                    } finally {
                        invokeCloseHandler();
                    }
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
        return OPTIONS.contains(option) || super.supportsOption(option);
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
            old = Boolean.valueOf(socket.getSoLinger() != -1);
            socket.setSoLinger(Options.CLOSE_ABORT.cast(value, false).booleanValue(), 0);
        } else if (option == Options.KEEP_ALIVE) {
            old = Boolean.valueOf(socket.getKeepAlive());
            socket.setKeepAlive(Options.KEEP_ALIVE.cast(value, false).booleanValue());
        } else if (option == Options.TCP_OOB_INLINE) {
            old = Boolean.valueOf(socket.getOOBInline());
            socket.setOOBInline(Options.TCP_OOB_INLINE.cast(value, false).booleanValue());
        } else if (option == Options.SEND_BUFFER) {
            old = Integer.valueOf(socket.getSendBufferSize());
            final int newValue = Options.SEND_BUFFER.cast(value, DEFAULT_BUFFER_SIZE).intValue();
            if (newValue < 1) {
                throw new IllegalArgumentException("Buffer size must be larger than 1");
            }
            socket.setSendBufferSize(Options.SEND_BUFFER.cast(value, DEFAULT_BUFFER_SIZE).intValue());
        } else if (option == Options.TCP_NODELAY) {
            old = Boolean.valueOf(socket.getTcpNoDelay());
            socket.setTcpNoDelay(Options.TCP_NODELAY.cast(value, false).booleanValue());
        } else if (option == Options.IP_TRAFFIC_CLASS) {
            old = Integer.valueOf(socket.getTrafficClass());
            socket.setTrafficClass(Options.IP_TRAFFIC_CLASS.cast(value, 0).intValue());
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
