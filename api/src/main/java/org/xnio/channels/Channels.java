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

package org.xnio.channels;

import java.io.InterruptedIOException;
import java.nio.channels.FileChannel;
import org.xnio.Buffers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.util.concurrent.TimeUnit;
import org.xnio.ChannelListener;
import org.xnio.ReadChannelThread;
import org.xnio.WriteChannelThread;

/**
 * A utility class containing static methods to support channel usage.
 *
 * @apiviz.exclude
 */
public final class Channels {

    private Channels() {
    }

    /**
     * Simple utility method to execute a blocking flush on a writable channel.  The method blocks until there are no
     * remaining bytes in the send queue.
     *
     * @param channel the writable channel
     * @throws IOException if an I/O exception occurs
     *
     * @since 2.0
     */
    public static void flushBlocking(SuspendableWriteChannel channel) throws IOException {
        while (! channel.flush()) {
            channel.awaitWritable();
        }
    }

    /**
     * Simple utility method to execute a blocking write shutdown on a writable channel.  The method blocks until the
     * channel's output side is fully shut down.
     *
     * @param channel the writable channel
     * @throws IOException if an I/O exception occurs
     *
     * @since 2.0
     */
    public static void shutdownWritesBlocking(SuspendableWriteChannel channel) throws IOException {
        while (! channel.shutdownWrites()) {
            channel.awaitWritable();
        }
    }

    /**
     * Simple utility method to execute a blocking write on a byte channel.  The method blocks until the bytes in the
     * buffer have been fully written.  To ensure that the data is sent, the {@link #flushBlocking(SuspendableWriteChannel)}
     * method should be called after all writes are complete.
     *
     * @param channel the channel to write on
     * @param buffer the data to write
     * @param <C> the channel type
     * @return the number of bytes written
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends WritableByteChannel & SuspendableWriteChannel> int writeBlocking(C channel, ByteBuffer buffer) throws IOException {
        int t = 0;
        while (buffer.hasRemaining()) {
            final int res = channel.write(buffer);
            if (res == 0) {
                channel.awaitWritable();
            } else {
                t += res;
            }
        }
        return t;
    }

    /**
     * Simple utility method to execute a blocking write on a byte channel with a timeout.  The method blocks until
     * either the bytes in the buffer have been fully written, or the timeout expires, whichever comes first.
     *
     * @param channel the channel to write on
     * @param buffer the data to write
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the channel type
     * @return the number of bytes written
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends WritableByteChannel & SuspendableWriteChannel> int writeBlocking(C channel, ByteBuffer buffer, long time, TimeUnit unit) throws IOException {
        long remaining = unit.toMillis(time);
        long now = System.currentTimeMillis();
        int t = 0;
        while (buffer.hasRemaining() && remaining > 0L) {
            int res = channel.write(buffer);
            if (res == 0) {
                channel.awaitWritable(remaining, TimeUnit.MILLISECONDS);
                remaining -= Math.max(-now + (now = System.currentTimeMillis()), 0L);
            } else {
                t += res;
            }
        }
        return t;
    }

    /**
     * Simple utility method to execute a blocking write on a gathering byte channel.  The method blocks until the
     * bytes in the buffer have been fully written.
     *
     * @param channel the channel to write on
     * @param buffers the data to write
     * @param offs the index of the first buffer to write
     * @param len the number of buffers to write
     * @param <C> the channel type
     * @return the number of bytes written
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends GatheringByteChannel & SuspendableWriteChannel> long writeBlocking(C channel, ByteBuffer[] buffers, int offs, int len) throws IOException {
        long t = 0;
        while (Buffers.hasRemaining(buffers, offs, len)) {
            final long res = channel.write(buffers, offs, len);
            if (res == 0) {
                channel.awaitWritable();
            } else {
                t += res;
            }
        }
        return t;
    }

    /**
     * Simple utility method to execute a blocking write on a gathering byte channel with a timeout.  The method blocks until all
     * the bytes are written, or until the timeout occurs.
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
     * @since 1.2
     */
    public static <C extends GatheringByteChannel & SuspendableWriteChannel> long writeBlocking(C channel, ByteBuffer[] buffers, int offs, int len, long time, TimeUnit unit) throws IOException {
        long remaining = unit.toMillis(time);
        long now = System.currentTimeMillis();
        long t = 0;
        while (Buffers.hasRemaining(buffers, offs, len) && remaining > 0L) {
            long res = channel.write(buffers, offs, len);
            if (res == 0) {
                channel.awaitWritable(remaining, TimeUnit.MILLISECONDS);
                remaining -= Math.max(-now + (now = System.currentTimeMillis()), 0L);
            } else {
                t += res;
            }
        }
        return t;
    }

    /**
     * Simple utility method to execute a blocking send on a message channel.  The method blocks until the message is written.
     *
     * @param channel the channel to write on
     * @param buffer the data to write
     * @param <C> the channel type
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends WritableMessageChannel> void sendBlocking(C channel, ByteBuffer buffer) throws IOException {
        while (! channel.send(buffer)) {
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
     * @return the write result
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends WritableMessageChannel> boolean sendBlocking(C channel, ByteBuffer buffer, long time, TimeUnit unit) throws IOException {
        long remaining = unit.toMillis(time);
        long now = System.currentTimeMillis();
        while (remaining > 0L) {
            if (!channel.send(buffer)) {
                channel.awaitWritable(remaining, TimeUnit.MILLISECONDS);
                remaining -= Math.max(-now + (now = System.currentTimeMillis()), 0L);
            } else {
                return true;
            }
        }
        return false;
    }

    /**
     * Simple utility method to execute a blocking gathering send on a message channel.  The method blocks until the message is written.
     *
     * @param channel the channel to write on
     * @param buffers the data to write
     * @param offs the index of the first buffer to write
     * @param len the number of buffers to write
     * @param <C> the channel type
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends WritableMessageChannel> void sendBlocking(C channel, ByteBuffer[] buffers, int offs, int len) throws IOException {
        while (! channel.send(buffers, offs, len)) {
            channel.awaitWritable();
        }
    }

    /**
     * Simple utility method to execute a blocking gathering send on a message channel with a timeout.  The method blocks until either
     * the message is written or the timeout expires.
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
     * @since 1.2
     */
    public static <C extends WritableMessageChannel> boolean sendBlocking(C channel, ByteBuffer[] buffers, int offs, int len, long time, TimeUnit unit) throws IOException {
        long remaining = unit.toMillis(time);
        long now = System.currentTimeMillis();
        while (remaining > 0L) {
            if (!channel.send(buffers, offs, len)) {
                channel.awaitWritable(remaining, TimeUnit.MILLISECONDS);
                remaining -= Math.max(-now + (now = System.currentTimeMillis()), 0L);
            } else {
                return true;
            }
        }
        return false;
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
     * @since 1.2
     */
    public static <C extends ReadableByteChannel & SuspendableReadChannel> int readBlocking(C channel, ByteBuffer buffer) throws IOException {
        int res;
        while ((res = channel.read(buffer)) == 0 && buffer.hasRemaining()) {
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
     * @since 1.2
     */
    public static <C extends ReadableByteChannel & SuspendableReadChannel> int readBlocking(C channel, ByteBuffer buffer, long time, TimeUnit unit) throws IOException {
        int res = channel.read(buffer);
        if (res == 0 && buffer.hasRemaining()) {
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
     * @since 1.2
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
     * @since 1.2
     */
    public static <C extends ScatteringByteChannel & SuspendableReadChannel> long readBlocking(C channel, ByteBuffer[] buffers, int offs, int len, long time, TimeUnit unit) throws IOException {
        long res = channel.read(buffers, offs, len);
        if (res == 0L && Buffers.hasRemaining(buffers, offs, len)) {
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
     * @since 1.2
     */
    public static <C extends ReadableMessageChannel> int receiveBlocking(C channel, ByteBuffer buffer) throws IOException {
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
     * @since 1.2
     */
    public static <C extends ReadableMessageChannel> int receiveBlocking(C channel, ByteBuffer buffer, long time, TimeUnit unit) throws IOException {
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
     * @since 1.2
     */
    public static <C extends ReadableMessageChannel> long receiveBlocking(C channel, ByteBuffer[] buffers, int offs, int len) throws IOException {
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
     * @since 1.2
     */
    public static <C extends ReadableMessageChannel> long receiveBlocking(C channel, ByteBuffer[] buffers, int offs, int len, long time, TimeUnit unit) throws IOException {
        long res = channel.receive(buffers, offs, len);
        if ((res) == 0) {
            channel.awaitReadable(time, unit);
            return channel.receive(buffers, offs, len);
        } else {
            return res;
        }
    }

    /**
     * Simple utility method to execute a blocking accept on an accepting channel.  This method blocks until
     * an accept is possible, and then returns the accepted connection.
     *
     * @param channel the accepting channel
     * @param readThread the initial read thread to use for the new connection, or {@code null} for none
     * @param writeThread the initial write thread to use for the new connection, or {@code null} for none
     * @param <C> the connection channel type
     * @param <A> the accepting channel type
     * @return the accepted channel
     * @throws IOException if an I/O error occurs
     * @since 3.0
     */
    public static <C extends ConnectedChannel, A extends AcceptingChannel<C>> C acceptBlocking(A channel, ReadChannelThread readThread, WriteChannelThread writeThread) throws IOException {
        C accepted;
        while ((accepted = channel.accept(readThread, writeThread)) == null) {
            channel.awaitAcceptable();
        }
        return accepted;
    }

    /**
     * Simple utility method to execute a blocking accept on an accepting channel, with a timeout.  This method blocks until
     * an accept is possible, and then returns the accepted connection.
     *
     * @param channel the accepting channel
     * @param readThread the initial read thread to use for the new connection, or {@code null} for none
     * @param writeThread the initial write thread to use for the new connection, or {@code null} for none
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the connection channel type
     * @param <A> the accepting channel type
     * @return the accepted channel, or {@code null} if the timeout occurred before a connection was accepted
     * @throws IOException if an I/O error occurs
     * @since 3.0
     */
    public static <C extends ConnectedChannel, A extends AcceptingChannel<C>> C acceptBlocking(A channel, ReadChannelThread readThread, WriteChannelThread writeThread, long time, TimeUnit unit) throws IOException {
        final C accepted = channel.accept(readThread, writeThread);
        if (accepted == null) {
            channel.awaitAcceptable(time, unit);
            return channel.accept(readThread, writeThread);
        } else {
            return accepted;
        }
    }

    /**
     * Transfer bytes between two channels efficiently, blocking if necessary.
     *
     * @param destination the destination channel
     * @param source the source file channel
     * @param startPosition the start position in the source file
     * @param count the number of bytes to transfer
     * @throws IOException if an I/O error occurs
     */
    public static void transferBlocking(StreamSinkChannel destination, FileChannel source, long startPosition, final long count) throws IOException {
        long remaining = count;
        long res;
        while (remaining > 0L) {
            while ((res = destination.transferFrom(source, startPosition, remaining)) == 0L) {
                try {
                    destination.awaitWritable();
                } catch (InterruptedIOException e) {
                    final long bytes = count - remaining;
                    if (bytes > (long) Integer.MAX_VALUE) {
                        e.bytesTransferred = -1;
                    } else {
                        e.bytesTransferred = (int) bytes;
                    }
                }
            }
            remaining -= res;
            startPosition += res;
        }
    }

    /**
     * Transfer bytes between two channels efficiently, blocking if necessary.
     *
     * @param destination the destination file channel
     * @param source the source channel
     * @param startPosition the start position in the destination file
     * @param count the number of bytes to transfer
     * @throws IOException if an I/O error occurs
     */
    public static void transferBlocking(FileChannel destination, StreamSourceChannel source, long startPosition, final long count) throws IOException {
        long remaining = count;
        long res;
        while (remaining > 0L) {
            while ((res = source.transferTo(startPosition, remaining, destination)) == 0L) {
                try {
                    source.awaitReadable();
                } catch (InterruptedIOException e) {
                    final long bytes = count - remaining;
                    if (bytes > (long) Integer.MAX_VALUE) {
                        e.bytesTransferred = -1;
                    } else {
                        e.bytesTransferred = (int) bytes;
                    }
                }
            }
            remaining -= res;
            startPosition += res;
        }
    }

    /**
     * Set the close listener for a channel (type-safe).
     *
     * @param channel the channel
     * @param listener the listener to set
     * @param <T> the channel type
     */
    public static <T extends CloseableChannel> void setCloseListener(T channel, ChannelListener<? super T> listener) {
        @SuppressWarnings("unchecked")
        ChannelListener.Setter<? extends T> setter = (ChannelListener.Setter<? extends T>) channel.getCloseSetter();
        setter.set(listener);
    }

    /**
     * Set the accept listener for a channel (type-safe).
     *
     * @param channel the channel
     * @param listener the listener to set
     * @param <T> the channel type
     */
    public static <T extends AcceptingChannel<?>> void setAcceptListener(T channel, ChannelListener<? super T> listener) {
        @SuppressWarnings("unchecked")
        ChannelListener.Setter<? extends T> setter = (ChannelListener.Setter<? extends T>) channel.getAcceptSetter();
        setter.set(listener);
    }

    /**
     * Set the read listener for a channel (type-safe).
     *
     * @param channel the channel
     * @param listener the listener to set
     * @param <T> the channel type
     */
    public static <T extends SuspendableReadChannel> void setReadListener(T channel, ChannelListener<? super T> listener) {
        @SuppressWarnings("unchecked")
        ChannelListener.Setter<? extends T> setter = (ChannelListener.Setter<? extends T>) channel.getReadSetter();
        setter.set(listener);
    }

    /**
     * Set the write listener for a channel (type-safe).
     *
     * @param channel the channel
     * @param listener the listener to set
     * @param <T> the channel type
     */
    public static <T extends SuspendableWriteChannel> void setWriteListener(T channel, ChannelListener<? super T> listener) {
        @SuppressWarnings("unchecked")
        ChannelListener.Setter<? extends T> setter = (ChannelListener.Setter<? extends T>) channel.getWriteSetter();
        setter.set(listener);
    }


}
