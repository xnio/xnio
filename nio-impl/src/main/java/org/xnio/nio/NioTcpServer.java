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
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.jboss.logging.Logger;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.UnsupportedOptionException;

import static org.xnio.nio.Log.log;

final class NioTcpServer implements AcceptingChannel<NioTcpChannel> {
    private static final Logger log = Logger.getLogger("org.xnio.nio.tcp.server");
    private static final String FQCN = NioTcpServer.class.getName();

    private final NioXnioWorker worker;

    private final ChannelListener.SimpleSetter<NioTcpServer> acceptSetter = new ChannelListener.SimpleSetter<NioTcpServer>();
    private final ChannelListener.SimpleSetter<NioTcpServer> closeSetter = new ChannelListener.SimpleSetter<NioTcpServer>();

    private final List<NioHandle<NioTcpServer>> acceptHandles;

    private final ServerSocketChannel channel;
    private final ServerSocket socket;

    private static final Set<Option<?>> options = Option.setBuilder()
            .add(Options.REUSE_ADDRESSES)
            .add(Options.RECEIVE_BUFFER)
            .add(Options.SEND_BUFFER)
            .add(Options.KEEP_ALIVE)
            .add(Options.TCP_OOB_INLINE)
            .add(Options.TCP_NODELAY)
            .create();

    @SuppressWarnings( { "unused" })
    private volatile int keepAlive;
    @SuppressWarnings( { "unused" })
    private volatile int oobInline;
    @SuppressWarnings( { "unused" })
    private volatile int tcpNoDelay;
    @SuppressWarnings( { "unused" })
    private volatile int sendBuffer = -1;

    private static final AtomicIntegerFieldUpdater<NioTcpServer> keepAliveUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "keepAlive");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> oobInlineUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "oobInline");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> tcpNoDelayUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "tcpNoDelay");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> sendBufferUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "sendBuffer");

    NioTcpServer(final NioXnioWorker worker, final ServerSocketChannel channel, final OptionMap optionMap) throws ClosedChannelException {
        this.worker = worker;
        this.channel = channel;
        final boolean write = optionMap.get(Options.WORKER_ESTABLISH_WRITING, false);
        final int count = optionMap.get(Options.WORKER_ACCEPT_THREADS, 1);
        final WorkerThread[] threads = worker.choose(count, write);
        @SuppressWarnings("unchecked")
        final NioHandle<NioTcpServer>[] handles = new NioHandle[threads.length];
        for (int i = 0, length = threads.length; i < length; i++) {
            handles[i] = threads[i].addChannel(channel, this, 0, acceptSetter);
        }
        acceptHandles = Arrays.asList(handles);
        socket = channel.socket();
    }

    public void close() throws IOException {
        try {
            channel.close();
        } finally {
            for (NioHandle<NioTcpServer> handle : acceptHandles) {
                handle.cancelKey();
            }
        }
    }

    public boolean supportsOption(final Option<?> option) {
        return options.contains(option);
    }

    public <T> T getOption(final Option<T> option) throws UnsupportedOptionException, IOException {
        if (option == Options.REUSE_ADDRESSES) {
            return option.cast(Boolean.valueOf(socket.getReuseAddress()));
        } else if (option == Options.RECEIVE_BUFFER) {
            return option.cast(Integer.valueOf(socket.getReceiveBufferSize()));
        } else if (option == Options.SEND_BUFFER) {
            final int value = sendBuffer;
            return value == -1 ? null : option.cast(Integer.valueOf(value));
        } else if (option == Options.KEEP_ALIVE) {
            return option.cast(Boolean.valueOf(keepAlive != 0));
        } else if (option == Options.TCP_OOB_INLINE) {
            return option.cast(Boolean.valueOf(oobInline != 0));
        } else if (option == Options.TCP_NODELAY) {
            return option.cast(Boolean.valueOf(tcpNoDelay != 0));
        } else {
            return null;
        }
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        final Object old;
        if (option == Options.REUSE_ADDRESSES) {
            old = Boolean.valueOf(socket.getReuseAddress());
            socket.setReuseAddress(Options.REUSE_ADDRESSES.cast(value).booleanValue());
        } else if (option == Options.RECEIVE_BUFFER) {
            old = Integer.valueOf(socket.getReceiveBufferSize());
            socket.setReceiveBufferSize(Options.RECEIVE_BUFFER.cast(value).intValue());
        } else if (option == Options.SEND_BUFFER) {
            final int newValue = value == null ? -1 : Options.SEND_BUFFER.cast(value).intValue();
            if (value != null && newValue < 1) {
                throw new IllegalArgumentException("Bad send buffer size specified");
            }
            final int oldValue = sendBufferUpdater.getAndSet(this, newValue);
            old = oldValue == -1 ? null : Integer.valueOf(oldValue);
        } else if (option == Options.KEEP_ALIVE) {
            old = Boolean.valueOf(keepAliveUpdater.getAndSet(this, Options.KEEP_ALIVE.cast(value).booleanValue() ? 1 : 0) != 0);
        } else if (option == Options.TCP_OOB_INLINE) {
            old = Boolean.valueOf(oobInlineUpdater.getAndSet(this, Options.TCP_OOB_INLINE.cast(value).booleanValue() ? 1 : 0) != 0);
        } else if (option == Options.TCP_NODELAY) {
            old = Boolean.valueOf(tcpNoDelayUpdater.getAndSet(this, Options.TCP_NODELAY.cast(value).booleanValue() ? 1 : 0) != 0);
        } else {
            return null;
        }
        return option.cast(old);
    }

    public NioTcpChannel accept() throws IOException {
        final SocketChannel accepted = channel.accept();
        if (accepted == null) {
            return null;
        }
        accepted.configureBlocking(false);
        final Socket socket = accepted.socket();
        socket.setKeepAlive(keepAlive != 0);
        socket.setOOBInline(oobInline != 0);
        socket.setTcpNoDelay(tcpNoDelay != 0);
        final int sendBuffer = this.sendBuffer;
        if (sendBuffer > 0) socket.setSendBufferSize(sendBuffer);
        final NioTcpChannel newChannel;
        boolean ok = false;
        try {
            newChannel = new NioTcpChannel(worker, accepted);
            ok = true;
        } finally {
            if (! ok) {
                IoUtils.safeClose(accepted);
            }
        }
        log.trace("TCP server accepted connection");
        return newChannel;
    }

    public String toString() {
        return String.format("TCP server (NIO) <%s>", Integer.toHexString(hashCode()));
    }

    public ChannelListener.SimpleSetter<NioTcpServer> getAcceptSetter() {
        return acceptSetter;
    }

    public ChannelListener.SimpleSetter<NioTcpServer> getCloseSetter() {
        return closeSetter;
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    public SocketAddress getLocalAddress() {
        return socket.getLocalSocketAddress();
    }

    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        final SocketAddress address = getLocalAddress();
        return type.isInstance(address) ? type.cast(address) : null;
    }

    public void suspendAccepts() {
        for (NioHandle<NioTcpServer> handle : acceptHandles) {
            handle.resume(0);
        }
    }

    public void resumeAccepts() {
        for (NioHandle<NioTcpServer> handle : acceptHandles) {
            handle.resume(SelectionKey.OP_ACCEPT);
        }
    }

    public void wakeupAccepts() {
        log.logf(FQCN, Logger.Level.TRACE, null, "Wake up accepts on %s", this);
        final List<NioHandle<NioTcpServer>> handles = this.acceptHandles;
        final int len = handles.size();
        if (len == 0) {
            throw new IllegalArgumentException("No thread configured");
        }
        final int idx = IoUtils.getThreadLocalRandom().nextInt(len);
        acceptHandles.get(idx).execute();
    }

    public void awaitAcceptable() throws IOException {
        SelectorUtils.await(worker.getXnio(), channel, SelectionKey.OP_ACCEPT);
    }

    public void awaitAcceptable(final long time, final TimeUnit timeUnit) throws IOException {
        SelectorUtils.await(worker.getXnio(), channel, SelectionKey.OP_ACCEPT, time, timeUnit);
    }

    public XnioWorker getWorker() {
        return worker;
    }
}
