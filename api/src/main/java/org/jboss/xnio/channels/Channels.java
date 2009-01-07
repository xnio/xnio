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

package org.jboss.xnio.channels;

import org.jboss.xnio.ChannelSource;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.AbstractConvertingIoFuture;
import org.jboss.xnio.log.Logger;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.util.concurrent.TimeUnit;

/**
 * A utility class containing static methods to convert from one channel type to another.
 */
public final class Channels {

    private Channels() {
    }

    /**
     * Create a channel source for an allocated message channel.  The resulting channel uses a simple protocol to send
     * and receive messages.  First, a four-byte length field is sent in network order; then a message of that length
     * follows.  If an incoming message is too large, it is ignored.  If an outgoing message is too large, an exception
     * is thrown for that send.
     *
     * @param streamChannelSource the stream channel source to encapsulate
     * @param maxInboundMessageSize the maximum incoming message size
     * @param maxOutboundMessageSize the maximum outgoing message size
     * @return an allocated message channel source
     */
    public static ChannelSource<AllocatedMessageChannel> convertStreamToAllocatedMessage(final ChannelSource<? extends StreamChannel> streamChannelSource, final int maxInboundMessageSize, final int maxOutboundMessageSize) {
        return new ChannelSource<AllocatedMessageChannel>() {
            public IoFuture<AllocatedMessageChannel> open(final IoHandler<? super AllocatedMessageChannel> handler) {
                final AllocatedMessageChannelStreamChannelHandler innerHandler = new AllocatedMessageChannelStreamChannelHandler(handler, maxInboundMessageSize, maxOutboundMessageSize);
                return new AbstractConvertingIoFuture<AllocatedMessageChannel, StreamChannel>(streamChannelSource.open(innerHandler)) {
                    protected AllocatedMessageChannel convert(final StreamChannel arg) {
                        return innerHandler.getChannel(arg);
                    }
                };
            }
        };
    }

    /**
     * Create a handler factory for an allocated message channel.  The resulting channel uses a simple protocol to send
     * and receive messages.  First, a four-byte length field is sent in network order; then a message of that length
     * follows.  If an incoming message is too large, it is ignored.  If an outgoing message is too large, an exception
     * is thrown for that send.
     *
     * @param handlerFactory the user allocated message channel handler factory
     * @param maxInboundMessageSize the maximum incoming message size
     * @param maxOutboundMessageSize the maximum outgoing message size
     * @return a stream channel handler factory that implements the protocol
     */
    public static IoHandlerFactory<StreamChannel> convertStreamToAllocatedMessage(final IoHandlerFactory<? super AllocatedMessageChannel> handlerFactory, final int maxInboundMessageSize, final int maxOutboundMessageSize) {
        return new IoHandlerFactory<StreamChannel>() {
            public IoHandler<? super StreamChannel> createHandler() {
                //noinspection unchecked
                return new AllocatedMessageChannelStreamChannelHandler(handlerFactory.createHandler(), maxInboundMessageSize, maxOutboundMessageSize);
            }
        };
    }

    /**
     * Create a channel source for a stream channel.  The resulting channel simply converts the messages into stream bytes.
     * The resulting channel is strictly edge-triggered: if a read handler does not fully consume all the readable bytes
     * on the channel before calling {@link StreamChannel#resumeReads() StreamChannel.resumeReads()} or
     * {@link StreamChannel#awaitReadable() StreamChannel.awaitReadable()}, then the channel may block even though there
     * is more data to be read.
     *
     * @param messageChannelSource the allocated message channel source
     * @return a stream channel source
     */
    public static ChannelSource<StreamChannel> convertAllocatedMessageToStream(final ChannelSource<? extends AllocatedMessageChannel> messageChannelSource) {
        return new ChannelSource<StreamChannel>() {
            public IoFuture<StreamChannel> open(final IoHandler<? super StreamChannel> handler) {
                final StreamChannelAllocatedMessageChannelHandler innerHandler = new StreamChannelAllocatedMessageChannelHandler(handler);
                return new AbstractConvertingIoFuture<StreamChannel, AllocatedMessageChannel>(messageChannelSource.open(innerHandler)) {
                    protected StreamChannel convert(final AllocatedMessageChannel arg) throws IOException {
                        return innerHandler.getChannel(arg);
                    }
                };
            }
        };
    }

    /**
     * Create a channel source for a stream channel.  The resulting channel simply converts the messages into stream bytes.
     * The resulting channel is strictly edge-triggered: if a read handler does not fully consume all the readable bytes
     * on the channel before calling {@link StreamChannel#resumeReads() StreamChannel.resumeReads()} or
     * {@link StreamChannel#awaitReadable() StreamChannel.awaitReadable()}, then the channel may block even though there
     * is more data to be read.
     *
     * @param handlerFactory the user stream channel handler factory
     * @return an allocated message channel handler factory that implements the protocol
     */
    public static IoHandlerFactory<AllocatedMessageChannel> convertAllocatedMessageToStream(final IoHandlerFactory<? super StreamChannel> handlerFactory) {
        return new IoHandlerFactory<AllocatedMessageChannel>() {
            public IoHandler<? super AllocatedMessageChannel> createHandler() {
                //noinspection unchecked
                return new StreamChannelAllocatedMessageChannelHandler(handlerFactory.createHandler());
            }
        };
    }

    private static final Logger mergedLog = Logger.getLogger("org.jboss.xnio.channels.merged");

    /**
     * Create a handler that is a merged view of two separate handlers, one for read operations and one for write operations.
     * The {@code handleOpened()} and {@code handleClosed()} methods are called on each of the two sub-handlers.
     *
     * @param <T> the resultant channel type
     * @param readSide the handler to handle read operations
     * @param writeSide the handler to handle write operations
     * @return a combined handler
     */
    public static <T extends SuspendableChannel> IoHandler<T> createMergedHandler(final IoHandler<? super T> readSide, final IoHandler<? super T> writeSide) {
        return new IoHandler<T>() {
            public void handleOpened(final T channel) {
                readSide.handleOpened(channel);
                boolean ok = false;
                try {
                    writeSide.handleOpened(channel);
                    ok = true;
                } finally {
                    if (! ok) try {
                        readSide.handleClosed(channel);
                    } catch (Throwable t) {
                        mergedLog.error(t, "Error in close handler");
                    }
                }
            }

            public void handleReadable(final T channel) {
                readSide.handleReadable(channel);
            }

            public void handleWritable(final T channel) {
                writeSide.handleReadable(channel);
            }

            public void handleClosed(final T channel) {
                try {
                    readSide.handleClosed(channel);
                } catch (Throwable t) {
                    mergedLog.error(t, "Error in close handler");
                }
                try {
                    writeSide.handleClosed(channel);
                } catch (Throwable t) {
                    mergedLog.error(t, "Error in close handler");
                }
            }
        };
    }

    /**
     * Create a handler factory that is a merged view of two separate handler factories, one for read operations and one for write operations.
     *
     * @param <T> the resultant channel type
     * @param readFactory the handler factory to create handlers that handle read operations
     * @param writeFactory the handler factory to create handlers that handle write operations
     * @return a combined handler factory
     */
    public static <T extends SuspendableChannel> IoHandlerFactory<T> createMergedHandlerFactory(final IoHandlerFactory<? super T> readFactory, final IoHandlerFactory<? super T> writeFactory) {
        return new IoHandlerFactory<T>() {
            @SuppressWarnings({"unchecked"})
            public IoHandler<T> createHandler() {
                return createMergedHandler((IoHandler)readFactory.createHandler(), (IoHandler)writeFactory.createHandler());
            }
        };
    }

    /**
     * Simple utility method to execute a blocking write on a byte channel.  The method blocks until the channel
     * is writable, and then the message is written.
     *
     * @param channel the channel to write on
     * @param buffer the data to write
     * @param <C> the channel type
     * @return the number of bytes written
     * @throws IOException if an I/O exception occurs
     */
    public static <C extends WritableByteChannel & SuspendableWriteChannel> int writeBlocking(C channel, ByteBuffer buffer) throws IOException {
        int res;
        while ((res = channel.write(buffer)) == 0) {
            channel.awaitWritable();
        }
        return res;
    }

    /**
     * Simple utility method to execute a blocking write on a byte channel with a timeout.  The method blocks until the channel
     * is writable, and then the message is written.
     *
     * @param channel the channel to write on
     * @param buffer the data to write
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the channel type
     * @return the number of bytes written
     * @throws IOException if an I/O exception occurs
     */
    public static <C extends WritableByteChannel & SuspendableWriteChannel> int writeBlocking(C channel, ByteBuffer buffer, long time, TimeUnit unit) throws IOException {
        int res = channel.write(buffer);
        if (res == 0) {
            channel.awaitWritable(time, unit);
            return channel.write(buffer);
        } else {
            return res;
        }
    }

    /**
     * Simple utility method to execute a blocking write on a gathering byte channel.  The method blocks until the channel
     * is writable, and then the message is written.
     *
     * @param channel the channel to write on
     * @param buffers the data to write
     * @param offs the index of the first buffer to write
     * @param len the number of buffers to write
     * @param <C> the channel type
     * @return the number of bytes written
     * @throws IOException if an I/O exception occurs
     */
    public static <C extends GatheringByteChannel & SuspendableWriteChannel> long writeBlocking(C channel, ByteBuffer[] buffers, int offs, int len) throws IOException {
        long res;
        while ((res = channel.write(buffers, offs, len)) == 0) {
            channel.awaitWritable();
        }
        return res;
    }

    /**
     * Simple utility method to execute a blocking write on a gathering byte channel with a timeout.  The method blocks until the channel
     * is writable, and then the message is written.
     *
     * @param channel the channel to write on
     * @param buffers the data to write
     * @param offs the index of the first buffer to write
     * @param len the number of buffers to write
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the channel type
     * @return the number of bytes written
     * @throws IOException if an I/O exception occurs
     */
    public static <C extends GatheringByteChannel & SuspendableWriteChannel> long writeBlocking(C channel, ByteBuffer[] buffers, int offs, int len, long time, TimeUnit unit) throws IOException {
        long res = channel.write(buffers, offs, len);
        if (res == 0L) {
            channel.awaitWritable(time, unit);
            return channel.write(buffers, offs, len);
        } else {
            return res;
        }
    }

    /**
     * Simple utility method to execute a blocking send on a message channel.  The method blocks until the channel
     * is writable, and then the message is written.
     *
     * @param channel the channel to write on
     * @param buffer the data to write
     * @param <C> the channel type
     * @throws IOException if an I/O exception occurs
     */
    public static <C extends WritableMessageChannel & SuspendableWriteChannel> void sendBlocking(C channel, ByteBuffer buffer) throws IOException {
        while (! (channel.send(buffer))) {
            channel.awaitWritable();
        }
    }

    /**
     * Simple utility method to execute a blocking send on a message channel with a timeout.  The method blocks until the channel
     * is writable, and then the message is written.
     *
     * @param channel the channel to write on
     * @param buffer the data to write
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the channel type
     * @return {@code true} if the message was written before the timeout
     * @throws IOException if an I/O exception occurs
     */
    public static <C extends WritableMessageChannel & SuspendableWriteChannel> boolean sendBlocking(C channel, ByteBuffer buffer, long time, TimeUnit unit) throws IOException {
        if (! (channel.send(buffer))) {
            channel.awaitWritable(time, unit);
            return channel.send(buffer);
        } else {
            return true;
        }
    }

    /**
     * Simple utility method to execute a blocking gathering send on a message channel.  The method blocks until the channel
     * is writable, and then the message is written.
     *
     * @param channel the channel to write on
     * @param buffers the data to write
     * @param offs the index of the first buffer to write
     * @param len the number of buffers to write
     * @param <C> the channel type
     * @throws IOException if an I/O exception occurs
     */
    public static <C extends WritableMessageChannel & SuspendableWriteChannel> void sendBlocking(C channel, ByteBuffer[] buffers, int offs, int len) throws IOException {
        while (! (channel.send(buffers, offs, len))) {
            channel.awaitWritable();
        }
    }

    /**
     * Simple utility method to execute a blocking gathering send on a message channel with a timeout.  The method blocks until the channel
     * is writable, and then the message is written.
     *
     * @param channel the channel to write on
     * @param buffers the data to write
     * @param offs the index of the first buffer to write
     * @param len the number of buffers to write
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the channel type
     * @return {@code true} if the message was written before the timeout
     * @throws IOException if an I/O exception occurs
     */
    public static <C extends WritableMessageChannel & SuspendableWriteChannel> boolean sendBlocking(C channel, ByteBuffer[] buffers, int offs, int len, long time, TimeUnit unit) throws IOException {
        if (! (channel.send(buffers, offs, len))) {
            channel.awaitWritable(time, unit);
            return channel.send(buffers, offs, len);
        } else {
            return true;
        }
    }

    /**
     * Simple utility method to execute a blocking read on a readable byte channel.  This method blocks until the
     * channel is readable, and then the message is read.
     *
     * @param channel the channel to read from
     * @param buffer the buffer into which bytes are to be transferred
     * @param <C> the channel type
     * @return the number of bytes read
     * @throws IOException if an I/O exception occurs
     */
    public static <C extends ReadableByteChannel & SuspendableReadChannel> int readBlocking(C channel, ByteBuffer buffer) throws IOException {
        int res;
        while ((res = channel.read(buffer)) == 0) {
            channel.awaitReadable();
        }
        return res;
    }

    /**
     * Simple utility method to execute a blocking read on a readable byte channel with a timeout.  This method blocks until the
     * channel is readable, and then the message is read.
     *
     * @param channel the channel to read from
     * @param buffer the buffer into which bytes are to be transferred
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the channel type
     * @return the number of bytes read
     * @throws IOException if an I/O exception occurs
     */
    public static <C extends ReadableByteChannel & SuspendableReadChannel> int readBlocking(C channel, ByteBuffer buffer, long time, TimeUnit unit) throws IOException {
        int res = channel.read(buffer);
        if (res == 0) {
            channel.awaitReadable(time, unit);
            return channel.read(buffer);
        } else {
            return res;
        }
    }

    /**
     * Simple utility method to execute a blocking read on a scattering byte channel.  This method blocks until the
     * channel is readable, and then the message is read.
     *
     * @param channel the channel to read from
     * @param buffers the buffers into which bytes are to be transferred
     * @param offs the first buffer to use
     * @param len the number of buffers to use
     * @param <C> the channel type
     * @return the number of bytes read
     * @throws IOException if an I/O exception occurs
     */
    public static <C extends ScatteringByteChannel & SuspendableReadChannel> long readBlocking(C channel, ByteBuffer[] buffers, int offs, int len) throws IOException {
        long res;
        while ((res = channel.read(buffers, offs, len)) == 0) {
            channel.awaitReadable();
        }
        return res;
    }

    /**
     * Simple utility method to execute a blocking read on a scattering byte channel with a timeout.  This method blocks until the
     * channel is readable, and then the message is read.
     *
     * @param channel the channel to read from
     * @param buffers the buffers into which bytes are to be transferred
     * @param offs the first buffer to use
     * @param len the number of buffers to use
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the channel type
     * @return the number of bytes read
     * @throws IOException if an I/O exception occurs
     */
    public static <C extends ScatteringByteChannel & SuspendableReadChannel> long readBlocking(C channel, ByteBuffer[] buffers, int offs, int len, long time, TimeUnit unit) throws IOException {
        long res = channel.read(buffers, offs, len);
        if (res == 0L) {
            channel.awaitReadable(time, unit);
            return channel.read(buffers, offs, len);
        } else {
            return res;
        }
    }

    /**
     * Simple utility method to execute a blocking receive on a readable message channel.  This method blocks until the
     * channel is readable, and then the message is received.
     *
     * @param channel the channel to read from
     * @param buffer the buffer into which bytes are to be transferred
     * @param <C> the channel type
     * @return the number of bytes read
     * @throws IOException if an I/O exception occurs
     */
    public static <C extends ReadableMessageChannel & SuspendableReadChannel> int receiveBlocking(C channel, ByteBuffer buffer) throws IOException {
        int res;
        while ((res = channel.receive(buffer)) == 0) {
            channel.awaitReadable();
        }
        return res;
    }

    /**
     * Simple utility method to execute a blocking receive on a readable message channel with a timeout.  This method blocks until the
     * channel is readable, and then the message is received.
     *
     * @param channel the channel to read from
     * @param buffer the buffer into which bytes are to be transferred
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the channel type
     * @return the number of bytes read
     * @throws IOException if an I/O exception occurs
     */
    public static <C extends ReadableMessageChannel & SuspendableReadChannel> int receiveBlocking(C channel, ByteBuffer buffer, long time, TimeUnit unit) throws IOException {
        int res = channel.receive(buffer);
        if ((res) == 0) {
            channel.awaitReadable(time, unit);
            return channel.receive(buffer);
        } else {
            return res;
        }
    }

    /**
     * Simple utility method to execute a blocking receive on a readable message channel.  This method blocks until the
     * channel is readable, and then the message is received.
     *
     * @param channel the channel to read from
     * @param buffers the buffers into which bytes are to be transferred
     * @param offs the first buffer to use
     * @param len the number of buffers to use
     * @param <C> the channel type
     * @return the number of bytes read
     * @throws IOException if an I/O exception occurs
     */
    public static <C extends ReadableMessageChannel & SuspendableReadChannel> long receiveBlocking(C channel, ByteBuffer[] buffers, int offs, int len) throws IOException {
        long res;
        while ((res = channel.receive(buffers, offs, len)) == 0) {
            channel.awaitReadable();
        }
        return res;
    }

    /**
     * Simple utility method to execute a blocking receive on a readable message channel with a timeout.  This method blocks until the
     * channel is readable, and then the message is received.
     *
     * @param channel the channel to read from
     * @param buffers the buffers into which bytes are to be transferred
     * @param offs the first buffer to use
     * @param len the number of buffers to use
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the channel type
     * @return the number of bytes read
     * @throws IOException if an I/O exception occurs
     */
    public static <C extends ReadableMessageChannel & SuspendableReadChannel> long receiveBlocking(C channel, ByteBuffer[] buffers, int offs, int len, long time, TimeUnit unit) throws IOException {
        long res = channel.receive(buffers, offs, len);
        if ((res) == 0) {
            channel.awaitReadable(time, unit);
            return channel.receive(buffers, offs, len);
        } else {
            return res;
        }
    }

}
