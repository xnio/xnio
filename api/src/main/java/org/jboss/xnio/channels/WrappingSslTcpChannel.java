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
import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.Set;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.net.InetSocketAddress;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;

import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.Option;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Sequence;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.log.Logger;

final class WrappingSslTcpChannel implements SslTcpChannel {

    private static final Logger log = Logger.getLogger("org.jboss.xnio.ssl");

    private final TcpChannel tcpChannel;
    private final SSLEngine sslEngine;
    private final Executor executor;

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

    private final Runnable readTriggeredTask = new Runnable() {
        public void run() {
            runReadListener();
        }
    };

    private final ChannelListener<TcpChannel> tcpReadListener = new ChannelListener<TcpChannel>() {
        public void handleEvent(final TcpChannel channel) {
            final Lock mainLock = WrappingSslTcpChannel.this.mainLock;
            mainLock.lock();
            try {
                readAwaiters.signalAll();
                if (userReads) {
                    userReads = false;
                    runReadListener();
                }
            } finally {
                mainLock.unlock();
            }
        }
    };

    private final ChannelListener<TcpChannel> tcpWriteListener = new ChannelListener<TcpChannel>() {
        public void handleEvent(final TcpChannel channel) {
            runWriteListener();
        }
    };

    private void runReadListener() {
        IoUtils.<SslTcpChannel>invokeChannelListener(this, readListener);
    }

    private void runWriteListener() {
        IoUtils.<SslTcpChannel>invokeChannelListener(this, writeListener);
    }

    private final Lock mainLock = new ReentrantLock();
    private final Condition readAwaiters = mainLock.newCondition();

    private boolean userReads;
    private boolean userWrites;
    private boolean needWrites;
    private boolean writeShutdownRequested;

    private ReadState readState = ReadState.UP;

    private enum ReadState {
        UP,
        SOCK_EOF,
        SSL_EOF
    }

    /**
     * The application data read buffer.  Filled if a read required more space than the user buffer had available.  Reads
     * pull data from this buffer first, and additional data from unwrap() if needed.  This buffer should remain either
     * empty or flipped for emptying when the lock is not held.
     */
    private ByteBuffer readBuffer;

    /**
     * The socket receive buffer.  Staging area for unwrap operations.  This buffer should remain either empty or unflipped
     * for filling when the lock is not held.
     */
    private ByteBuffer receiveBuffer;

    /**
     * The socket send buffer.  Target area for wrap operations.  Wrap operations have no source buffer, as there
     * is generally no minimum size for outbound data (thankfully).  This buffer should remain either empty or flipped
     * for emptying when the lock is not held.
     */
    private ByteBuffer sendBuffer;

    WrappingSslTcpChannel(final TcpChannel tcpChannel, final SSLEngine sslEngine, final Executor executor) {
        this.tcpChannel = tcpChannel;
        this.sslEngine = sslEngine;
        this.executor = executor;
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

    public boolean flush() throws IOException {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (writeShutdownRequested) {
                throw new ClosedChannelException();
            }
            return doFlush();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Actually do the flush.  Call with the (write) lock held.
     *
     * @return {@code true} if the buffers were flushed completely, or {@code false} if some data remains in the buffer
     * @throws IOException if an I/O error occurs
     */
    private boolean doFlush() throws IOException {
        final ByteBuffer sendBuffer = this.sendBuffer;
        final TcpChannel tcpChannel = this.tcpChannel;
        while (sendBuffer.hasRemaining()) {
            if (tcpChannel.write(sendBuffer) == 0) {
                return false;
            }
        }
        sendBuffer.clear();
        return true;
    }

    /**
     * Do unwrap.  Returns true if unwrapping occurred (even if no output was produced).  Call with (read) lock held.
     *
     * @return {@code true} if unwrapping occurred
     * @throws IOException
     */
    private boolean doUnwrap() throws IOException {
        final ByteBuffer readBuffer = this.readBuffer;
        final SSLEngine sslEngine = this.sslEngine;
        final ByteBuffer receiveBuffer = this.receiveBuffer;
        final TcpChannel tcpChannel = this.tcpChannel;
        if (readState == ReadState.SSL_EOF) {
            return false;
        }
        if (readState == ReadState.UP && receiveBuffer.position() == 0) {
            // Receive buffer empty, fill it
            int res = tcpChannel.read(receiveBuffer);
            if (res == 0) {
                return false;
            }
            if (res == -1) {
                readState = ReadState.SOCK_EOF;
                return false;
            }
            do {
                if (! receiveBuffer.hasRemaining()) {
                    break;
                }
                res = tcpChannel.read(receiveBuffer);
                if (res == -1) {
                    readState = ReadState.SOCK_EOF;
                }
            }
            while (res > 0);
            receiveBuffer.flip();
        }
        while (receiveBuffer.hasRemaining()) {
            final SSLEngineResult result = sslEngine.unwrap(receiveBuffer, readBuffer);
            final int consumed = result.bytesConsumed();
            final int produced = result.bytesProduced();
            final SSLEngineResult.Status status = result.getStatus();
            switch (status) {
                case BUFFER_OVERFLOW: {
                    receiveBuffer.compact();
                    return readBuffer.position() != 0;
                }
                case BUFFER_UNDERFLOW: {
                    receiveBuffer.compact();
                    boolean proceed = false;
                    while (receiveBuffer.hasRemaining()) {
                        final int cnt = tcpChannel.read(receiveBuffer);
                        if (cnt <= 0) {
                            if (proceed) {
                                break;
                            } else {
                                return readBuffer.position() != 0;
                            }
                        }
                        proceed = true;
                    }
                    receiveBuffer.flip();
                    break;
                }
                case CLOSED: {
                    readState = ReadState.SSL_EOF;
                    tcpChannel.shutdownReads();
                    return produced > 0 || consumed > 0;
                }
                case OK: {
                    receiveBuffer.
                }
            }
        }
    }

    /**
     * Fill the read buffer.  Returns true if there is data to read (unwrap is called as needed).
     *
     * @return true if there is data to read
     * @throws IOException
     */
    private boolean doFill() throws IOException {
        final ByteBuffer readBuffer = this.readBuffer;
        if (readBuffer.hasRemaining()) {
            // there's data to read
            return true;
        }
        // otherwise, unwrap data
        while (doUnwrap()) {
            if (readBuffer.position() > 0) {
                // return true if there's data to read
                return true;
            }
        }
        return false;
    }

    public boolean isOpen() {
        return tcpChannel.isOpen();
    }

    public void close() throws IOException {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            writeShutdownRequested = true;
            // todo - proper semantics of SSL shutdown?
            tcpChannel.close();
        } finally {
            mainLock.unlock();
        }
    }

    private static final Set<Option<?>> OPTIONS = Option.setBuilder()
            .add(CommonOptions.SSL_ENABLED_CIPHER_SUITES)
            .add(CommonOptions.SSL_ENABLED_PROTOCOLS)
            .add(CommonOptions.SSL_SUPPORTED_CIPHER_SUITES)
            .add(CommonOptions.SSL_SUPPORTED_PROTOCOLS)
            .create();

    public boolean supportsOption(final Option<?> option) {
        return OPTIONS.contains(option) || tcpChannel.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        if (option == CommonOptions.SSL_ENABLED_CIPHER_SUITES) {
            return option.cast(Sequence.of(sslEngine.getEnabledCipherSuites()));
        } else if (option == CommonOptions.SSL_SUPPORTED_CIPHER_SUITES) {
            return option.cast(Sequence.of(sslEngine.getSupportedCipherSuites()));
        } else if (option == CommonOptions.SSL_ENABLED_PROTOCOLS) {
            return option.cast(Sequence.of(sslEngine.getEnabledProtocols()));
        } else if (option == CommonOptions.SSL_SUPPORTED_PROTOCOLS) {
            return option.cast(Sequence.of(sslEngine.getSupportedProtocols()));
        } else {
            return tcpChannel.getOption(option);
        }
    }

    public <T> Configurable setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        if (option == CommonOptions.SSL_ENABLED_CIPHER_SUITES) {
            final Sequence<String> strings = CommonOptions.SSL_ENABLED_CIPHER_SUITES.cast(value);
            sslEngine.setEnabledCipherSuites(strings.toArray(new String[strings.size()]));
        } else if (option == CommonOptions.SSL_ENABLED_PROTOCOLS) {
            final Sequence<String> strings = CommonOptions.SSL_ENABLED_PROTOCOLS.cast(value);
            sslEngine.setEnabledProtocols(strings.toArray(new String[strings.size()]));
        } else {
            tcpChannel.setOption(option, value);
        }
        return this;
    }

    public void suspendReads() {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            userReads = false;
        } finally {
            mainLock.unlock();
        }
    }

    public void resumeReads() {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (readBuffer.hasRemaining()) {
                executor.execute(readTriggeredTask);
            } else {
                tcpChannel.resumeReads();
            }
        } finally {
            mainLock.unlock();
        }
    }

    public void shutdownReads() throws IOException {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            tcpChannel.shutdownReads();
            sslEngine.closeInbound();
        } finally {
            mainLock.unlock();
        }
    }

    public void awaitReadable() throws IOException {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            while (!readBuffer.hasRemaining() && !doFill()) {
                try {
                    tcpChannel.resumeReads();
                    readAwaiters.await();
                } catch (InterruptedException e) {
                    throw new InterruptedIOException();
                }
            }
        } finally {
            mainLock.unlock();
        }
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
    }

    public void suspendWrites() {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            userWrites = false;
        } finally {
            mainLock.unlock();
        }
    }

    public void resumeWrites() {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (!userWrites && !needWrites) {
                tcpChannel.resumeWrites();
            }
            userWrites = true;
        } finally {
            mainLock.unlock();
        }
    }

    public void shutdownWrites() throws IOException {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (!writeShutdownRequested) {
                writeShutdownRequested = true;
                sslEngine.closeOutbound();
                // todo - call wrap/write to flush any remaining data!
                tcpChannel.shutdownWrites();
            }
        } finally {
            mainLock.unlock();
        }
    }

    public void awaitWritable() throws IOException {
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
    }

    public int write(final ByteBuffer src) throws IOException {
        return (int) write(new ByteBuffer[] { src }, 0, 1);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        long cnt = 0L;
        final SSLEngine sslEngine = this.sslEngine;
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (writeShutdownRequested) {
                throw new ClosedChannelException();
            }
            ByteBuffer sendBuffer = this.sendBuffer;
            for (; ;) {
                if (sendBuffer.hasRemaining() && !doFlush()) {
                    return cnt;
                }
                final SSLEngineResult result = sslEngine.wrap(srcs, offset, length, sendBuffer);
                final int produced = result.bytesProduced();
                final int consumed = result.bytesConsumed();
                cnt += consumed;
                final SSLEngineResult.Status status = result.getStatus();
                switch (status) {
                    case BUFFER_OVERFLOW: {
                        if (sendBuffer.position() == 0) {
                            // send buffer is too small, grow it
                            final int oldCap = sendBuffer.capacity();
                            final int reqCap = sslEngine.getSession().getPacketBufferSize();
                            if (reqCap <= oldCap) {
                                // ...but the send buffer should have had plenty of room?
                                throw new IOException("SSLEngine required a bigger send buffer but our buffer was already big enough");
                            }
                            sendBuffer = this.sendBuffer = ByteBuffer.allocate(reqCap);
                        } else {
                            // there's some data in there, so send it first
                            sendBuffer.flip();
                        }
                        break;
                    }
                    case BUFFER_UNDERFLOW: {
                        // the source buffer must be empty, since there's no minimum?
                        // todo: verify that...
                        return cnt;
                    }
                    case CLOSED: {
                        // attempted write after shutdown
                        throw new ClosedChannelException();
                    }
                    case OK: {
                        if (produced == 0) {
                            // must be in handshake?
                            switch (result.getHandshakeStatus()) {
                                case NEED_TASK: {
                                    executor.execute(sslEngine.getDelegatedTask());
                                    break;
                                }
                                case NEED_UNWRAP: {
                                    doFill();
                                    break;
                                }
                            }
                            if (consumed == 0) {
                                // nothing left?
                                return cnt;
                            }
                        }
                        sendBuffer.flip();
                        break;
                    }
                }
            }
        } finally {
            mainLock.unlock();
        }
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
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
