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
import java.nio.channels.FileChannel;
import java.net.InetSocketAddress;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;

import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.Option;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Sequence;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.Options;
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

    private final ChannelListener<TcpChannel> tcpReadListener = new ReadListener();

    private final ChannelListener<TcpChannel> tcpWriteListener = new WriteListener();

    private void runReadListener() {
        IoUtils.<SslTcpChannel>invokeChannelListener(this, readListener);
    }

    private void runWriteListener() {
        IoUtils.<SslTcpChannel>invokeChannelListener(this, writeListener);
    }

    private final Lock mainLock = new ReentrantLock();

    /**
     * Condition: threads waiting in awaitReadable(); signalAll whenever data is added to the read buffer, or whenever
     * the TCP channel becomes readable.
     */
    private final Condition readAwaiters = mainLock.newCondition();
    /**
     * Condition: threads waiting in awaitWritable(); signalAll whenever {@code needsUnwrap} is cleared
     */
    private final Condition writeAwaiters = mainLock.newCondition();

    private boolean userReads;
    private boolean userWrites;
    // readers need a wrap to proceed
    private boolean needsWrap;
    // writers need an unwrap to proceed
    private boolean needsUnwrap;

    /**
     * The application data read buffer.  Filled if a read required more space than the user buffer had available.  Reads
     * pull data from this buffer first, and additional data from unwrap() if needed.  This buffer should remain either
     * empty or flipped for reading when the lock is not held.
     */
    private ByteBuffer readBuffer = Buffers.EMPTY_BYTE_BUFFER;

    /**
     * The socket receive buffer.  Staging area for unwrap operations.  This buffer should remain either empty or flipped
     * for reading when the lock is not held.
     */
    private ByteBuffer receiveBuffer = Buffers.EMPTY_BYTE_BUFFER;

    /**
     * The socket send buffer.  Target area for wrap operations.  Wrap operations have no source buffer, as there
     * is generally no minimum size for outbound data (thankfully).  This buffer should remain either empty or unflipped
     * for appending when the lock is not held.
     */
    private ByteBuffer sendBuffer = Buffers.EMPTY_BYTE_BUFFER;

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

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return target.transferFrom(this, position, count);
    }

    public ChannelListener.Setter<SslTcpChannel> getReadSetter() {
        return readSetter;
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, this);
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
        final TcpChannel tcpChannel = this.tcpChannel;
        WRAP: for (;;) {
            final ByteBuffer sendBuffer = this.sendBuffer;
            sendBuffer.flip();
            try {
                while (sendBuffer.hasRemaining()) {
                    log.trace("Flushing send buffer %s", sendBuffer);
                    if (tcpChannel.write(sendBuffer) == 0) {
                        log.trace("Flush would block, return false");
                        return false;
                    }
                }
            } finally {
                sendBuffer.compact();
            }
            // now wrap until everything is flushed
            final SSLEngine sslEngine = this.sslEngine;
            log.trace("Wrapping empty buffer into send buffer %s", sendBuffer);
            final SSLEngineResult wrapResult = sslEngine.wrap(Buffers.EMPTY_BYTE_BUFFER, sendBuffer);
            log.trace("Wrap result is %s", wrapResult);
            final int produced = wrapResult.bytesProduced();
            switch (wrapResult.getStatus()) {
                case CLOSED: {
                    return true;
                }
                case BUFFER_UNDERFLOW:
                case OK: {
                    if (produced > 0) {
                        log.trace("Data produced, flush needed");
                        continue;
                    }
                    // make sure some handshake step is not needed to proceed
                    switch (wrapResult.getHandshakeStatus()) {
                        case NOT_HANDSHAKING:
                        case FINISHED: {
                            log.trace("Fully flushed, return true");
                            // fully flushed!
                            return true;
                        }
                        case NEED_TASK: {
                            final Runnable task = sslEngine.getDelegatedTask();
                            log.trace("Running delegated task %s", task);
                            task.run();
                            log.trace("Finished delegated task %s", task);
                            continue;
                        }
                        case NEED_UNWRAP: {
                            log.trace("Unwrap needed to proceed with flush");
                            // Ya gotta get input to get output...
                            UNWRAP: for (;;) {
                                final ByteBuffer receiveBuffer = this.receiveBuffer;
                                final ByteBuffer readBuffer = this.readBuffer;
                                log.trace("Unwrapping from receive buffer %s to read buffer %s", receiveBuffer, readBuffer);
                                final SSLEngineResult unwrapResult = sslEngine.unwrap(receiveBuffer, readBuffer);
                                readAwaiters.signalAll();
                                switch (unwrapResult.getStatus()) {
                                    case BUFFER_UNDERFLOW: {
                                        // not enough data.  First, see if there is room left in the receive buf - if not, grow it.
                                        if (receiveBuffer.position() == 0 && receiveBuffer.limit() == receiveBuffer.capacity()) {
                                            log.trace("Receive buffer is too small, growing from %s", receiveBuffer);
                                            // receive buffer is full but it's still not big enough, so grow it
                                            final int pktBufSize = sslEngine.getSession().getPacketBufferSize();
                                            if (receiveBuffer.capacity() >= pktBufSize) {
                                                // it's already the required size...
                                                throw new IOException("Unexpected/inexplicable buffer underflow from the SSL engine");
                                            }
                                            log.trace("Grew receive buffer to %s", this.receiveBuffer = Buffers.flip(ByteBuffer.allocate(pktBufSize).put(receiveBuffer)));
                                            continue UNWRAP;
                                        }
                                        // not enough data in receive buffer, fill it up
                                        receiveBuffer.compact();
                                        try {
                                            log.trace("Reading data into receive buffer %s");
                                            final int res = tcpChannel.read(receiveBuffer);
                                            if (res == -1) {
                                                log.trace("End of input stream reached");
                                                // bad news, end of stream...
                                                sslEngine.closeInbound();
                                                // but maybe that counts as unwrapping something :)
                                                continue WRAP;
                                            } else if (res == 0) {
                                                log.trace("Read would block, set needsUnwrap = true");
                                                needsUnwrap = true;
                                                return false;
                                            } else {
                                                // retry the unwrap!
                                                continue UNWRAP;
                                            }
                                        } finally {
                                            receiveBuffer.flip();
                                        }
                                    }
                                    case CLOSED: {
                                        log.trace("Engine is closed, everything must be flushed; return true");
                                        // I guess everything is flushed?
                                        return true;
                                    }
                                    case OK: {
                                        log.trace("Unwrap complete, proceeding with wrap");
                                        // great, now we shold be able to proceed with wrap
                                        continue WRAP;
                                    }
                                    default: {
                                        throw new IOException("Unexpected unwrap result status " + unwrapResult.getStatus());
                                    }
                                }
                                // not reached
                            }
                            // not reached
                        }
                        default: {
                            throw new IOException("Unexpected wrap result handshake status " + wrapResult.getStatus());
                        }
                    }
                }
                default: {
                    throw new IOException("Unexpected wrap result status " + wrapResult.getStatus());
                }
            }
        }
    }

    public boolean isOpen() {
        return tcpChannel.isOpen();
    }

    public void close() throws IOException {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            sslEngine.closeOutbound();
            IOException e1 = null;
            IOException e2 = null;
            try {
                sslEngine.closeInbound();
            } catch (IOException e) {
                e1 = e;
            }
            try {
                tcpChannel.close();
            } catch (IOException e) {
                e2 = e;
            }
            if (e1 != null && e2 != null) {
                final IOException t = new IOException("Multiple failures on close!  The second exception is: " + e2.toString());
                t.initCause(e1);
                throw t;
            }
            if (e1 != null) {
                throw e1;
            }
            if (e2 != null) {
                throw e2;
            }
        } finally {
            mainLock.unlock();
        }
    }

    private static final Set<Option<?>> OPTIONS = Option.setBuilder()
            .add(Options.SSL_ENABLED_CIPHER_SUITES)
            .add(Options.SSL_ENABLED_PROTOCOLS)
            .add(Options.SSL_SUPPORTED_CIPHER_SUITES)
            .add(Options.SSL_SUPPORTED_PROTOCOLS)
            .create();

    public boolean supportsOption(final Option<?> option) {
        return OPTIONS.contains(option) || tcpChannel.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        if (option == Options.SSL_ENABLED_CIPHER_SUITES) {
            return option.cast(Sequence.of(sslEngine.getEnabledCipherSuites()));
        } else if (option == Options.SSL_SUPPORTED_CIPHER_SUITES) {
            return option.cast(Sequence.of(sslEngine.getSupportedCipherSuites()));
        } else if (option == Options.SSL_ENABLED_PROTOCOLS) {
            return option.cast(Sequence.of(sslEngine.getEnabledProtocols()));
        } else if (option == Options.SSL_SUPPORTED_PROTOCOLS) {
            return option.cast(Sequence.of(sslEngine.getSupportedProtocols()));
        } else {
            return tcpChannel.getOption(option);
        }
    }

    public <T> Configurable setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        if (option == Options.SSL_ENABLED_CIPHER_SUITES) {
            final Sequence<String> strings = Options.SSL_ENABLED_CIPHER_SUITES.cast(value);
            sslEngine.setEnabledCipherSuites(strings.toArray(new String[strings.size()]));
        } else if (option == Options.SSL_ENABLED_PROTOCOLS) {
            final Sequence<String> strings = Options.SSL_ENABLED_PROTOCOLS.cast(value);
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

    public void suspendWrites() {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            userWrites = false;
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
                if (needsWrap) {
                    // read can't proceed until stuff is written, so wait for writability and then call the read listener
                    // during which the user will call read() which really writes... sigh
                    tcpChannel.resumeWrites();
                } else {
                    tcpChannel.resumeReads();
                }
                userReads = true;
            }
        } finally {
            mainLock.unlock();
        }
    }

    public void resumeWrites() {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (needsUnwrap) {
                tcpChannel.resumeReads();
            } else {
                tcpChannel.resumeWrites();
            }
            userWrites = true;
        } finally {
            mainLock.unlock();
        }
    }

    public void shutdownReads() throws IOException {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            log.trace("Shutting down writes");
            tcpChannel.shutdownReads();
            sslEngine.closeInbound();
        } finally {
            mainLock.unlock();
        }
    }

    public boolean shutdownWrites() throws IOException {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (flush()) {
                log.trace("Shutting down writes");
                sslEngine.closeOutbound();
                return flush() && sslEngine.isOutboundDone() && tcpChannel.shutdownWrites();
            } else {
                return false;
            }
        } finally {
            mainLock.unlock();
        }
    }

    public void awaitReadable() throws IOException {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // loop only once so that if the TCP channel becomes readable, control flow can resume
            // spurious wakeups are forgivable
            if (!readBuffer.hasRemaining()) {
                try {
                    if (needsWrap) {
                        // read can't proceed until stuff is written, so wait for writability
                        tcpChannel.resumeWrites();
                    } else {
                        tcpChannel.resumeReads();
                    }
                    readAwaiters.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException();
                }
            }
        } finally {
            mainLock.unlock();
        }
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // loop only once so that if the TCP channel becomes readable, control flow can resume
            // spurious wakeups are forgivable
            if (!readBuffer.hasRemaining()) {
                try {
                    if (needsWrap) {
                        // read can't proceed until stuff is written, so wait for writability
                        tcpChannel.resumeWrites();
                    } else {
                        tcpChannel.resumeReads();
                    }
                    readAwaiters.await(time, timeUnit);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException();
                }
            }
        } finally {
            mainLock.unlock();
        }
    }

    public void awaitWritable() throws IOException {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (needsUnwrap) {
                tcpChannel.resumeReads();
            } else {
                tcpChannel.resumeWrites();
            }
            writeAwaiters.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException();
        } finally {
            mainLock.unlock();
        }
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (needsUnwrap) {
                tcpChannel.resumeReads();
            } else {
                tcpChannel.resumeWrites();
            }
            writeAwaiters.await(time, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException();
        } finally {
            mainLock.unlock();
        }
    }

    public int write(final ByteBuffer src) throws IOException {
        return (int) write(new ByteBuffer[] { src }, 0, 1);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (length < 1) {
            return 0L;
        }
        final SSLEngine sslEngine = this.sslEngine;
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            ByteBuffer sendBuffer = this.sendBuffer;
            WRAP: for (; ;) {
                log.trace("Wrapping %s (and possibly more) into send buffer %s", srcs[0], sendBuffer);
                final SSLEngineResult wrapResult = sslEngine.wrap(srcs, offset, length, sendBuffer);
                log.trace("Wrap result is %s", wrapResult);
                final int produced = wrapResult.bytesProduced();
                final int consumed = wrapResult.bytesConsumed();
                final TcpChannel tcpChannel = this.tcpChannel;
                switch (wrapResult.getStatus()) {
                    case BUFFER_OVERFLOW: {
                        if (sendBuffer.position() == 0) {
                            log.trace("Send buffer is too small, growing from %s", sendBuffer);
                            // send buffer is too small, grow it
                            final int oldCap = sendBuffer.capacity();
                            final int reqCap = sslEngine.getSession().getPacketBufferSize();
                            if (reqCap <= oldCap) {
                                // ...but the send buffer should have had plenty of room?
                                throw new IOException("SSLEngine required a bigger send buffer but our buffer was already big enough");
                            }
                            log.trace("Grew send buffer to %s", sendBuffer = this.sendBuffer = ByteBuffer.allocate(reqCap));
                        } else {
                            log.trace("No room in send buffer, flushing");
                            // there's some data in there, so send it first
                            sendBuffer.flip();
                            try {
                                final int res = tcpChannel.write(sendBuffer);
                                if (res == 0) {
                                    return consumed;
                                }
                            } finally {
                                sendBuffer.compact();
                            }
                        }
                        // try again
                        continue;
                    }
                    case BUFFER_UNDERFLOW: {
                        log.trace("Source buffer must be empty, finished");
                        // the source buffer must be empty, since there's no minimum?
                        return consumed;
                    }
                    case CLOSED: {
                        log.trace("Attempted to write after the channel is shut down");
                        // attempted write after shutdown
                        throw new ClosedChannelException();
                    }
                    case OK: {
                        if (consumed == 0) {
                            if (produced > 0) {
                                // try again, since some data was produced
                                continue;
                            }
                            // must be in handshake?
                            switch (wrapResult.getHandshakeStatus()) {
                                case NEED_TASK: {
                                    // todo background
                                    final Runnable task = sslEngine.getDelegatedTask();
                                    log.trace("Running delegated task %s", task);
                                    task.run();
                                    log.trace("Finished delegated task %s", task);
                                    // try again
                                    continue;
                                }
                                case NEED_UNWRAP: {
                                    log.trace("Unwrap required before write can proceed");
                                    UNWRAP: for (;;) {
                                        final ByteBuffer receiveBuffer = this.receiveBuffer;
                                        final ByteBuffer readBuffer = this.readBuffer;
                                        final SSLEngineResult unwrapResult;
                                        readBuffer.compact();
                                        try {
                                            log.trace("Unwrapping from receive buffer %s to read buffer %s", receiveBuffer, readBuffer);
                                            unwrapResult = sslEngine.unwrap(receiveBuffer, readBuffer);
                                            log.trace("Unwrap result is %s", unwrapResult);
                                        } finally {
                                            readBuffer.flip();
                                        }
                                        if (! receiveBuffer.hasRemaining()) {
                                            receiveBuffer.clear().flip();
                                        }
                                        readAwaiters.signalAll();
                                        switch (unwrapResult.getStatus()) {
                                            case BUFFER_UNDERFLOW: {
                                                // not enough data.  First, see if there is room left in the receive buf - if not, grow it.
                                                if (receiveBuffer.position() == 0 && receiveBuffer.limit() == receiveBuffer.capacity()) {
                                                    log.trace("Receive buffer is not large enough to feed unwrap, growing from %s", receiveBuffer);
                                                    // receive buffer is full but it's still not big enough, so grow it
                                                    final int pktBufSize = sslEngine.getSession().getPacketBufferSize();
                                                    if (receiveBuffer.capacity() >= pktBufSize) {
                                                        // it's already the required size...
                                                        throw new IOException("Unexpected/inexplicable buffer underflow from the SSL engine");
                                                    }
                                                    log.trace("Grew receive buffer to %s", this.receiveBuffer = Buffers.flip(ByteBuffer.allocate(pktBufSize).put(receiveBuffer)));
                                                    continue UNWRAP;
                                                }
                                                // not enough data in receive buffer, fill it up
                                                receiveBuffer.compact();
                                                try {
                                                    log.trace("Filling receive buffer (read)");
                                                    final int res = tcpChannel.read(receiveBuffer);
                                                    if (res == -1) {
                                                        log.trace("End of inbound data");
                                                        // bad news, end of stream...
                                                        sslEngine.closeInbound();
                                                        // but maybe that counts as unwrapping something :)
                                                        continue WRAP;
                                                    } else if (res == 0) {
                                                        log.trace("Read would block, setting needsUnwrap = true");
                                                        needsUnwrap = true;
                                                        return consumed;
                                                    } else {
                                                        log.trace("Read successful, retrying unwrap");
                                                        // retry the unwrap!
                                                        continue UNWRAP;
                                                    }
                                                } finally {
                                                    receiveBuffer.flip();
                                                }
                                            }
                                            case BUFFER_OVERFLOW: {
                                                // read buffer is not big enough.
                                                final int appBufSize = sslEngine.getSession().getApplicationBufferSize();
                                                if (readBuffer.capacity() >= appBufSize) {
                                                    // it's already the required size...
                                                    throw new IOException("Unexpected/inexplicable buffer overflow from the SSL engine");
                                                }
                                                log.trace("Read buffer is too small, growing from %s", readBuffer);
                                                log.trace("Grew read buffer to %s", this.readBuffer = Buffers.flip(ByteBuffer.allocate(appBufSize).put(readBuffer)));
                                                continue UNWRAP;
                                            }
                                            case CLOSED: {
                                                log.trace("Read on closed channel, return");
                                                return consumed == 0 ? -1 : consumed;
                                            }
                                            case OK: {
                                                log.trace("Unwrap succeeded, proceeding with wrap");
                                                // great, now we shold be able to proceed with wrap
                                                continue WRAP;
                                            }
                                            default: {
                                                throw new IOException("Unexpected unwrap result status " + unwrapResult.getStatus());
                                            }
                                        }
                                        // not reached
                                    }
                                    // not reached
                                }
                                default: {
                                    throw new IOException("Unexpected handshake state " + wrapResult.getHandshakeStatus() + " on wrap");
                                }
                            }
                            // not reached
                        }
                        // else we consumed some write data, so call the op finished
                        return consumed;
                    }
                    default: {
                        throw new IOException("Unexpected wrap result status " + wrapResult.getStatus());
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
        return (int) read(new ByteBuffer[] { dst }, 0, 1);
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        final Lock mainLock = this.mainLock;
        mainLock.lock();
        try {
            ByteBuffer readBuffer = this.readBuffer;
            final int r = readBuffer.remaining();
            if (r > 0) {
                log.trace("Returning data from read buffer %s", readBuffer);
                return Buffers.put(dsts, offset, length, readBuffer);
            }
            final TcpChannel tcpChannel = this.tcpChannel;
            final SSLEngine sslEngine = this.sslEngine;
            UNWRAP: for (;;) {
                final ByteBuffer receiveBuffer = this.receiveBuffer;
                // no bytes in the read buffer (it is fully cleared) - need to unwrap some
                final ByteBuffer[] target = new ByteBuffer[length + 1];
                System.arraycopy(dsts, offset, target, 0, length);
                target[length] = readBuffer;
                readBuffer.compact();
                final SSLEngineResult unwrapResult;
                final boolean readBufferFilled;
                try {
                    final int t = readBuffer.position();
                    log.trace("Unwrapping from %s to %s (and possibly more)", receiveBuffer, target[0]);
                    unwrapResult = sslEngine.unwrap(receiveBuffer, target);
                    log.trace("Unwrap result is %s", unwrapResult);
                    readBufferFilled = t != readBuffer.position();
                } finally {
                    readBuffer.flip();
                }
                if (! receiveBuffer.hasRemaining()) {
                    receiveBuffer.clear().flip();
                }
                final int produced = unwrapResult.bytesProduced();

                // this statement RIGHT HERE is why I hate SSLEngine oh so much
                switch (unwrapResult.getStatus()) {
                    case BUFFER_OVERFLOW: {
                        // read buffer too small!  dynamically resize & repeat...
                        // the read buffer would still be empty at this point (by the spec) - if not, blow up
                        assert readBuffer.position() == 0;
                        log.trace("Growing application readBuffer from %s", readBuffer);
                        final int appBufSize = sslEngine.getSession().getApplicationBufferSize();
                        if (readBuffer.capacity() >= appBufSize) {
                            // the say the buf is too small, yet it's already at least their required size...?
                            throw new IOException("Unexpected/inexplicable buffer overflow from the SSL engine");
                        }
                        log.trace("Grew application readBuffer to %s", readBuffer = this.readBuffer = ByteBuffer.allocate(appBufSize));
                        // try again with the bigger buffer...
                        continue;
                    }
                    case BUFFER_UNDERFLOW: {
                        // not enough data.  First, see if there is room left in the receive buf - if not, grow it.
                        if (receiveBuffer.position() == 0 && receiveBuffer.limit() == receiveBuffer.capacity()) {
                            // receive buffer is full but it's still not big enough, so grow it
                            log.trace("Growing receive buffer from %s", receiveBuffer);
                            final int pktBufSize = sslEngine.getSession().getPacketBufferSize();
                            if (receiveBuffer.capacity() >= pktBufSize) {
                                // it's already the required size...
                                throw new IOException("Unexpected/inexplicable buffer underflow from the SSL engine");
                            }
                            log.trace("Grew receive buffer to %s", this.receiveBuffer = Buffers.flip(ByteBuffer.allocate(pktBufSize).put(receiveBuffer)));
                            continue UNWRAP;
                        }
                        // fill the rest of the buffer, then retry!
                        final int rres;
                        receiveBuffer.compact();
                        try {
                            log.trace("Reading into %s", receiveBuffer);
                            rres = tcpChannel.read(receiveBuffer);
                            log.trace("Read into %s", receiveBuffer);
                        } finally {
                            receiveBuffer.flip();
                        }
                        if (rres == -1) {
                            // TCP stream EOF... give the ssl engine the bad news
                            log.trace("Hit EOF on TCP stream, closing SSL inbound");
                            sslEngine.closeInbound();
                            // continue
                        } else if (rres == 0) {
                            return 0;
                        }
                        // else some data was received, so continue
                        continue;
                    }
                    case CLOSED: {
                        log.trace("Read from closed SSL inbound");
                        // end of the line, dude
                        // if we need to wrap more data, the write side will take care of it
                        needsUnwrap = false;
                        return -1;
                    }
                    case OK: {
                        needsUnwrap = false;
                        if (produced > 0) {
                            if (readBufferFilled) {
                                // we just added data to readBuffer!  notify the waiters, cos that's the rules baby
                                readAwaiters.signalAll();
                            }
                            return produced;
                        } else {
                            // find out why nothing was produced if everything is "OK" :-/
                            switch (unwrapResult.getHandshakeStatus()) {
                                case NEED_TASK: {
                                    // todo - background might be tricky, since the channel has to be unreadable until it's done (maybe?)
                                    final Runnable task = sslEngine.getDelegatedTask();
                                    log.trace("Running delegated task %s", task);
                                    task.run();
                                    log.trace("Delegated task %s complete", task);
                                    // try unwrap again
                                    continue;
                                }
                                case NEED_WRAP: {
                                    log.trace("Wrap required for read to proceed");
                                    // can't proceed until a message is wrapped!
                                    WRAP: for (;;) {
                                        // first wrap an empty buffer into the send buffer
                                        final ByteBuffer sendBuffer = this.sendBuffer;
                                        log.trace("Wrapping empty buffer into %s", sendBuffer);
                                        final SSLEngineResult wrapResult = sslEngine.wrap(Buffers.EMPTY_BYTE_BUFFER, sendBuffer);
                                        log.trace("Wrap result is %s", wrapResult);
                                        writeAwaiters.signalAll();
                                        switch (wrapResult.getStatus()) {
                                            case BUFFER_OVERFLOW: {
                                                // check to see if the send buffer is too small
                                                final int pktBufSize = sslEngine.getSession().getPacketBufferSize();
                                                if (sendBuffer.capacity() < pktBufSize) {
                                                    log.trace("Send buffer is too small; resizing from %s", sendBuffer);
                                                    // our send buffer is too small.  Reallocate and retry the wrap
                                                    log.trace("Send buffer resized to %s", (this.sendBuffer = ByteBuffer.allocate(pktBufSize)).put(sendBuffer).flip());
                                                    continue;
                                                }
                                                // send buffer is not too small, it just doesn't have enough space
                                                // thus we have to flush the send buffer
                                                sendBuffer.flip();
                                                try {
                                                    log.trace("Send buffer has insufficient space, flushing");
                                                    final int res = tcpChannel.write(sendBuffer);
                                                    if (res == 0) {
                                                        log.trace("Channel is not writable, set needsWrap = true");
                                                        // the channel is not readable until it's writable, what a pain in the ass :(
                                                        needsWrap = true;
                                                        return 0;
                                                    }
                                                } finally {
                                                    sendBuffer.compact();
                                                }
                                                // OK, we made some space, retry the wrap
                                                continue WRAP;
                                            }
                                            case OK: {
                                                log.trace("Wrap successful, continuing unwrap");
                                                // OK, the path is clear! try the read again.
                                                needsWrap = false;
                                                continue UNWRAP;
                                            }
                                            default: {
                                                throw new IOException("Unexpected status of " + wrapResult.getStatus() + " while wrapping for an unwrap");
                                            }
                                        }
                                        // not reached
                                    }
                                    // not reached
                                }
                                default: {
                                    throw new IOException("Unexpected handshake status of " + unwrapResult.getHandshakeStatus() + " while unwrapping");
                                }
                                // not reached
                            }
                            // not reached
                        }
                        // not reached
                    }
                    default: {
                        throw new IllegalStateException();
                    }
                    // not reached
                }
                // not reached
            }
            // not reached
        } finally {
            mainLock.unlock();
        }
        // not reached
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    private class WriteListener implements ChannelListener<TcpChannel> {

        public void handleEvent(final TcpChannel channel) {
            boolean runRead = false;
            boolean runWrite = false;
            final Lock mainLock = WrappingSslTcpChannel.this.mainLock;
            mainLock.lock();
            try {
                if (needsWrap) {
                    readAwaiters.signalAll();
                }
                if (! needsUnwrap) {
                    writeAwaiters.signalAll();
                    if (userWrites) {
                        userWrites = false;
                        runWrite = true;
                    }
                }
                if (userReads && needsWrap) {
                    userReads = false;
                    runRead = true;
                }
            } finally {
                mainLock.unlock();
            }
            if (runRead) runReadListener();
            if (runWrite) runWriteListener();
        }
    }

    private class ReadListener implements ChannelListener<TcpChannel> {

        public void handleEvent(final TcpChannel channel) {
            boolean runRead = false;
            boolean runWrite = false;
            final Lock mainLock = WrappingSslTcpChannel.this.mainLock;
            mainLock.lock();
            try {
                if (needsUnwrap) {
                    writeAwaiters.signalAll();
                }
                if (! needsWrap) {
                    readAwaiters.signalAll();
                    if (userReads) {
                        userReads = false;
                        runRead = true;
                    }
                }
                if (userWrites && needsUnwrap) {
                    userWrites = false;
                    runWrite = true;
                }
            } finally {
                mainLock.unlock();
            }
            if (runRead) runReadListener();
            if (runWrite) runWriteListener();
        }
    }

    public String toString() {
        return String.format("SSL wrapped <%H> %s", this, tcpChannel);
    }
}
