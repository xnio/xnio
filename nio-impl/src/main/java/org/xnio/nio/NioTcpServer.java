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
            .add(Options.READ_TIMEOUT)
            .add(Options.WRITE_TIMEOUT)
            .create();

    @SuppressWarnings( { "unused" })
    private volatile int keepAlive;
    @SuppressWarnings( { "unused" })
    private volatile int oobInline;
    @SuppressWarnings( { "unused" })
    private volatile int tcpNoDelay;
    @SuppressWarnings( { "unused" })
    private volatile int sendBuffer = -1;
    @SuppressWarnings("unused")
    private volatile int readTimeout = 0;
    @SuppressWarnings("unused")
    private volatile int writeTimeout = 0;

    private static final AtomicIntegerFieldUpdater<NioTcpServer> keepAliveUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "keepAlive");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> oobInlineUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "oobInline");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> tcpNoDelayUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "tcpNoDelay");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> sendBufferUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "sendBuffer");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> readTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "readTimeout");
    private static final AtomicIntegerFieldUpdater<NioTcpServer> writeTimeoutUpdater = AtomicIntegerFieldUpdater.newUpdater(NioTcpServer.class, "writeTimeout");

    NioTcpServer(final NioXnioWorker worker, final ServerSocketChannel channel, final OptionMap optionMap) throws IOException {
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
        if (optionMap.contains(Options.REUSE_ADDRESSES)) {
            socket.setReuseAddress(optionMap.get(Options.REUSE_ADDRESSES, false));
        }
        if (optionMap.contains(Options.RECEIVE_BUFFER)) {
            socket.setReceiveBufferSize(optionMap.get(Options.RECEIVE_BUFFER, 0));
        }
        if (optionMap.contains(Options.SEND_BUFFER)) {
            sendBufferUpdater.set(this, optionMap.get(Options.SEND_BUFFER, 0));
        }
        if (optionMap.contains(Options.KEEP_ALIVE)) {
            keepAliveUpdater.set(this, optionMap.get(Options.KEEP_ALIVE, false) ? 1 : 0);
        }
        if (optionMap.contains(Options.TCP_OOB_INLINE)) {
            oobInlineUpdater.set(this, optionMap.get(Options.TCP_OOB_INLINE, false) ? 1 : 0);
        }
        if (optionMap.contains(Options.TCP_NODELAY)) {
            tcpNoDelayUpdater.set(this, optionMap.get(Options.TCP_NODELAY, false) ? 1 : 0);
        }
        if (optionMap.contains(Options.READ_TIMEOUT)) {
            readTimeoutUpdater.set(this, optionMap.get(Options.READ_TIMEOUT, 0));
        }
        if (optionMap.contains(Options.WRITE_TIMEOUT)) {
            writeTimeoutUpdater.set(this, optionMap.get(Options.WRITE_TIMEOUT, 0));
        }
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
        } else if (option == Options.READ_TIMEOUT) {
            return option.cast(Integer.valueOf(readTimeout));
        } else if (option == Options.WRITE_TIMEOUT) {
            return option.cast(Integer.valueOf(writeTimeout));
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
        } else if (option == Options.READ_TIMEOUT) {
            old = Integer.valueOf(readTimeoutUpdater.getAndSet(this, Options.READ_TIMEOUT.cast(value).intValue()));
        } else if (option == Options.WRITE_TIMEOUT) {
            old = Integer.valueOf(writeTimeoutUpdater.getAndSet(this, Options.WRITE_TIMEOUT.cast(value).intValue()));
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
            newChannel.setOption(Options.READ_TIMEOUT, Integer.valueOf(readTimeout));
            newChannel.setOption(Options.WRITE_TIMEOUT, Integer.valueOf(writeTimeout));
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
        resumeAccepts();
        final List<NioHandle<NioTcpServer>> handles = acceptHandles;
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
