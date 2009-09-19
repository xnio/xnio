/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

package org.jboss.xnio.channels;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.nio.ByteBuffer;
import java.net.InetSocketAddress;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLEngine;

import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.Option;
import org.jboss.xnio.IoUtils;

final class WrappingSslTcpChannel implements SslTcpChannel {

    private final TcpChannel tcpChannel;
    private final SSLEngine sslEngine;

    private volatile ChannelListener<? super SslTcpChannel> readListener = null;
    private volatile ChannelListener<? super SslTcpChannel> writeListener = null;
    private volatile ChannelListener<? super SslTcpChannel> closeListener = null;

    private static final AtomicReferenceFieldUpdater<WrappingSslTcpChannel, ChannelListener> readListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(WrappingSslTcpChannel.class, ChannelListener.class, "readListener");
    private static final AtomicReferenceFieldUpdater<WrappingSslTcpChannel, ChannelListener> writeListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(WrappingSslTcpChannel.class, ChannelListener.class, "writeListener");
    private static final AtomicReferenceFieldUpdater<WrappingSslTcpChannel, ChannelListener> closeListenerUpdater = AtomicReferenceFieldUpdater.newUpdater(WrappingSslTcpChannel.class, ChannelListener.class, "closeListener");

    private final ChannelListener.Setter<SslTcpChannel> readSetter = IoUtils.getSetter(this, readListenerUpdater);
    private final ChannelListener.Setter<SslTcpChannel> writeSetter = IoUtils.getSetter(this, writeListenerUpdater);
    private final ChannelListener.Setter<SslTcpChannel> closeSetter = IoUtils.getSetter(this, closeListenerUpdater);

    private final ChannelListener<TcpChannel> tcpCloseListener = new ChannelListener<TcpChannel>() {
        public void handleEvent(final TcpChannel channel) {
            IoUtils.safeClose(WrappingSslTcpChannel.this);
            IoUtils.<SslTcpChannel>invokeChannelListener(WrappingSslTcpChannel.this, closeListener);
        }
    };

    private final ChannelListener<TcpChannel> tcpReadListener = new ChannelListener<TcpChannel>() {
        public void handleEvent(final TcpChannel channel) {
            // peform our read stuff
            // then...
            final boolean wantsReads;
            synchronized (lock) {
                wantsReads = WrappingSslTcpChannel.this.wantsReads;
                WrappingSslTcpChannel.this.wantsReads = false;
            }
            if (wantsReads) {
                IoUtils.<SslTcpChannel>invokeChannelListener(WrappingSslTcpChannel.this, readListener);
            }
        }
    };

    private final ChannelListener<TcpChannel> tcpWriteListener = new ChannelListener<TcpChannel>() {
        public void handleEvent(final TcpChannel channel) {
            // peform our write stuff
            // then...
            final boolean wantsWrites;
            synchronized (lock) {
                wantsWrites = WrappingSslTcpChannel.this.wantsWrites;
                WrappingSslTcpChannel.this.wantsWrites = false;
            }
            if (wantsWrites) {
                IoUtils.<SslTcpChannel>invokeChannelListener(WrappingSslTcpChannel.this, writeListener);
            }
        }
    };

    private final Object lock = new Object();
    private boolean wantsReads;
    private boolean wantsWrites;

    WrappingSslTcpChannel(final TcpChannel tcpChannel, final SSLEngine sslEngine) {
        this.tcpChannel = tcpChannel;
        this.sslEngine = sslEngine;
        tcpChannel.getReadSetter().set(tcpReadListener);
        tcpChannel.getWriteSetter().set(tcpWriteListener);
        tcpChannel.getCloseSetter().set(tcpCloseListener);
    }

    public InetSocketAddress getPeerAddress() {
        return tcpChannel.getPeerAddress();
    }

    public InetSocketAddress getLocalAddress() {
        return tcpChannel.getLocalAddress();
    }

    public void startHandshake() throws IOException {
        sslEngine.beginHandshake();
    }

    public SSLSession getSslSession() {
        return sslEngine.getSession();
    }

    public ChannelListener.Setter<SslTcpChannel> getReadSetter() {
        return readSetter;
    }

    public ChannelListener.Setter<SslTcpChannel> getWriteSetter() {
        return writeSetter;
    }

    public ChannelListener.Setter<SslTcpChannel> getCloseSetter() {
        return closeSetter;
    }

    public boolean isOpen() {
        return false;
    }

    public void close() throws IOException {
        tcpChannel.close();
    }

    public boolean supportsOption(final Option<?> option) {
        // todo -check against our options too
        return tcpChannel.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return tcpChannel.getOption(option);
    }

    public <T> Configurable setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        tcpChannel.setOption(option, value);
        return this;
    }

    public void suspendReads() {
        synchronized (lock) {
            wantsReads = false;
        }
    }

    public void resumeReads() {
        synchronized (lock) {
            if (! wantsReads) {
                wantsReads = true;
                tcpChannel.resumeReads();
            }
        }
    }

    public void shutdownReads() throws IOException {
        synchronized (lock) {
            tcpChannel.shutdownReads();
            sslEngine.closeInbound();
        }
    }

    public void awaitReadable() throws IOException {
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
    }

    public void suspendWrites() {
        synchronized (lock) {
            wantsWrites = false;
        }
    }

    public void resumeWrites() {
        synchronized (lock) {
            if (! wantsWrites) {
                wantsWrites = true;
                tcpChannel.resumeWrites();
            }
        }
    }

    public void shutdownWrites() throws IOException {
        synchronized (lock) {
            sslEngine.closeOutbound();
            // todo - call wrap/write to flush any remaining data!
            tcpChannel.shutdownWrites();
        }
    }

    public void awaitWritable() throws IOException {
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
    }

    public int write(final ByteBuffer src) throws IOException {
        return 0;
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        return 0;
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return 0;
    }

    public int read(final ByteBuffer dst) throws IOException {
        return 0;
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        return 0;
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return 0;
    }
}
