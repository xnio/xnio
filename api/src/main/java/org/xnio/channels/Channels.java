/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008 Red Hat, Inc. and/or its affiliates.
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

package org.xnio.channels;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOError;
import java.io.InterruptedIOException;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Locale;
import org.xnio.Buffers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.util.concurrent.TimeUnit;
import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.XnioIoThread;

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
        channel.shutdownWrites();
        flushBlocking(channel);
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
        long remaining = unit.toNanos(time);
        long now = System.nanoTime();
        int t = 0;
        while (buffer.hasRemaining() && remaining > 0L) {
            int res = channel.write(buffer);
            if (res == 0) {
                channel.awaitWritable(remaining, TimeUnit.NANOSECONDS);
                remaining -= Math.max(-now + (now = System.nanoTime()), 0L);
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
        long remaining = unit.toNanos(time);
        long now = System.nanoTime();
        long t = 0;
        while (Buffers.hasRemaining(buffers, offs, len) && remaining > 0L) {
            long res = channel.write(buffers, offs, len);
            if (res == 0) {
                channel.awaitWritable(remaining, TimeUnit.NANOSECONDS);
                remaining -= Math.max(-now + (now = System.nanoTime()), 0L);
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
        long remaining = unit.toNanos(time);
        long now = System.nanoTime();
        while (remaining > 0L) {
            if (!channel.send(buffer)) {
                channel.awaitWritable(remaining, TimeUnit.NANOSECONDS);
                remaining -= Math.max(-now + (now = System.nanoTime()), 0L);
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
        long remaining = unit.toNanos(time);
        long now = System.nanoTime();
        while (remaining > 0L) {
            if (!channel.send(buffers, offs, len)) {
                channel.awaitWritable(remaining, TimeUnit.NANOSECONDS);
                remaining -= Math.max(-now + (now = System.nanoTime()), 0L);
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
     * @param <C> the connection channel type
     * @param <A> the accepting channel type
     * @return the accepted channel
     * @throws IOException if an I/O error occurs
     * @since 3.0
     */
    public static <C extends ConnectedChannel, A extends AcceptingChannel<C>> C acceptBlocking(A channel) throws IOException {
        C accepted;
        while ((accepted = channel.accept()) == null) {
            channel.awaitAcceptable();
        }
        return accepted;
    }

    /**
     * Simple utility method to execute a blocking accept on an accepting channel, with a timeout.  This method blocks until
     * an accept is possible, and then returns the accepted connection.
     *
     * @param channel the accepting channel
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the connection channel type
     * @param <A> the accepting channel type
     * @return the accepted channel, or {@code null} if the timeout occurred before a connection was accepted
     * @throws IOException if an I/O error occurs
     * @since 3.0
     */
    public static <C extends ConnectedChannel, A extends AcceptingChannel<C>> C acceptBlocking(A channel, long time, TimeUnit unit) throws IOException {
        final C accepted = channel.accept();
        if (accepted == null) {
            channel.awaitAcceptable(time, unit);
            return channel.accept();
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
     * Transfer bytes between two channels efficiently, blocking if necessary.
     *
     * @param destination the destination channel
     * @param source the source channel
     * @param throughBuffer the buffer to transfer through,
     * @param count the number of bytes to transfer
     * @return the number of bytes actually transferred (will be fewer than {@code count} if EOF was reached)
     * @throws IOException if the transfer fails
     */
    public static long transferBlocking(StreamSinkChannel destination, StreamSourceChannel source, ByteBuffer throughBuffer, long count) throws IOException {
        long t = 0L;
        long res;
        while (t < count) {
            try {
                while ((res = source.transferTo(count, throughBuffer, destination)) == 0L) {
                    if (throughBuffer.hasRemaining()) {
                        writeBlocking(destination, throughBuffer);
                    } else {
                        source.awaitReadable();
                    }
                }
                t += res;
            } catch (InterruptedIOException e) {
                int transferred = e.bytesTransferred;
                t += transferred;
                if (transferred < 0 || t > (long) Integer.MAX_VALUE) {
                    e.bytesTransferred = -1;
                } else {
                    e.bytesTransferred = (int) t;
                }
                throw e;
            }
            if (res == -1L) {
                return t == 0L ? -1L : t;
            }
        }
        return t;
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

    /**
     * Create a wrapper for a byte channel which does not expose other methods.
     *
     * @param original the original
     * @return the wrapped channel
     */
    public static ByteChannel wrapByteChannel(final ByteChannel original) {
        return new ByteChannel() {
            public int read(final ByteBuffer dst) throws IOException {
                return original.read(dst);
            }

            public boolean isOpen() {
                return original.isOpen();
            }

            public void close() throws IOException {
                original.close();
            }

            public int write(final ByteBuffer src) throws IOException {
                return original.write(src);
            }

            public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
                return original.write(srcs, offset, length);
            }

            public long write(final ByteBuffer[] srcs) throws IOException {
                return original.write(srcs);
            }

            public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
                return original.read(dsts, offset, length);
            }

            public long read(final ByteBuffer[] dsts) throws IOException {
                return original.read(dsts);
            }
        };
    }

    /**
     * Get an option value from a configurable target.  If the method throws an exception then the default value
     * is returned.
     *
     * @param configurable the configurable target
     * @param option the option
     * @param defaultValue the default value
     * @param <T> the option value type
     * @return the value
     */
    public static <T> T getOption(Configurable configurable, Option<T> option, T defaultValue) {
        try {
            final T value = configurable.getOption(option);
            return value == null ? defaultValue : value;
        } catch (IOException e) {
            return defaultValue;
        }
    }

    /**
     * Get an option value from a configurable target.  If the method throws an exception then the default value
     * is returned.
     *
     * @param configurable the configurable target
     * @param option the option
     * @param defaultValue the default value
     * @return the value
     */
    public static boolean getOption(Configurable configurable, Option<Boolean> option, boolean defaultValue) {
        try {
            final Boolean value = configurable.getOption(option);
            return value == null ? defaultValue : value.booleanValue();
        } catch (IOException e) {
            return defaultValue;
        }
    }

    /**
     * Get an option value from a configurable target.  If the method throws an exception then the default value
     * is returned.
     *
     * @param configurable the configurable target
     * @param option the option
     * @param defaultValue the default value
     * @return the value
     */
    public static int getOption(Configurable configurable, Option<Integer> option, int defaultValue) {
        try {
            final Integer value = configurable.getOption(option);
            return value == null ? defaultValue : value.intValue();
        } catch (IOException e) {
            return defaultValue;
        }
    }

    /**
     * Get an option value from a configurable target.  If the method throws an exception then the default value
     * is returned.
     *
     * @param configurable the configurable target
     * @param option the option
     * @param defaultValue the default value
     * @return the value
     */
    public static long getOption(Configurable configurable, Option<Long> option, long defaultValue) {
        try {
            final Long value = configurable.getOption(option);
            return value == null ? defaultValue : value.longValue();
        } catch (IOException e) {
            return defaultValue;
        }
    }

    /**
     * Unwrap a nested channel type.  If the channel does not wrap the target type, {@code null} is returned.
     *
     * @param targetType the class to unwrap
     * @param channel the channel
     * @param <T> the type to unwrap
     * @return the unwrapped type, or {@code null} if the given type is not wrapped
     * @see WrappedChannel
     */
    public static <T extends Channel> T unwrap(Class<T> targetType, Channel channel) {
        for (;;) {
            if (channel == null) {
                return null;
            } else if (targetType.isInstance(channel)) {
                return targetType.cast(channel);
            } else if (channel instanceof WrappedChannel) {
                channel = ((WrappedChannel<?>)channel).getChannel();
            } else {
                return null;
            }
        }
    }

    private static final FileChannel NULL_FILE_CHANNEL;
    private static final ByteBuffer DRAIN_BUFFER = ByteBuffer.allocateDirect(16384);

    /**
     * Attempt to drain the given number of bytes from the stream source channel.
     *
     * @param channel the channel to drain
     * @param count the number of bytes
     * @return the number of bytes drained, 0 if reading the channel would block, or -1 if the EOF was reached
     * @throws IOException if an error occurs
     */
    public static long drain(StreamSourceChannel channel, long count) throws IOException {
        long total = 0L, lres;
        int ires;
        ByteBuffer buffer = null;
        for (;;) {
            if (count == 0L) return total;
            if (NULL_FILE_CHANNEL != null) {
                while (count > 0) {
                    if ((lres = channel.transferTo(0, count, NULL_FILE_CHANNEL)) == 0L) {
                        break;
                    }
                    total += lres;
                    count -= lres;
                }
                // jump out quick if we drained the fast way
                if (total > 0L) return total;
            }
            if (buffer == null) buffer = DRAIN_BUFFER.duplicate();
            if ((long) buffer.limit() > count) buffer.limit((int) count);
            ires = channel.read(buffer);
            buffer.clear();
            switch (ires) {
                case -1: return total == 0L ? -1L : total;
                case 0: return total;
                default: total += (long) ires; count -= (long) ires;
            }
        }
    }

    /**
     * Attempt to drain the given number of bytes from the readable byte channel.
     *
     * @param channel the channel to drain
     * @param count the number of bytes
     * @return the number of bytes drained, 0 if reading the channel would block, or -1 if the EOF was reached
     * @throws IOException if an error occurs
     */
    public static long drain(ReadableByteChannel channel, long count) throws IOException {
        if (channel instanceof StreamSourceChannel) {
            return drain((StreamSourceChannel) channel, count);
        } else {
            long total = 0L, lres;
            int ires;
            ByteBuffer buffer = null;
            for (;;) {
                if (count == 0L) return total;
                if (NULL_FILE_CHANNEL != null) {
                    while (count > 0) {
                        if ((lres = NULL_FILE_CHANNEL.transferFrom(channel, 0, count)) == 0L) {
                            break;
                        }
                        total += lres;
                        count -= lres;
                    }
                    // jump out quick if we drained the fast way
                    if (total > 0L) return total;
                }
                if (buffer == null) buffer = DRAIN_BUFFER.duplicate();
                if ((long) buffer.limit() > count) buffer.limit((int) count);
                ires = channel.read(buffer);
                buffer.clear();
                switch (ires) {
                    case -1: return total == 0L ? -1L : total;
                    case 0: return total;
                    default: total += (long) ires; count -= (long) ires;
                }
            }
        }
    }

    /**
     * Attempt to drain the given number of bytes from the file channel.  This does nothing more than force a
     * read of bytes in the file.
     *
     * @param channel the channel to drain
     * @param position the position to drain from
     * @param count the number of bytes
     * @return the number of bytes drained, 0 if reading the channel would block, or -1 if the EOF was reached
     * @throws IOException if an error occurs
     */
    public static long drain(FileChannel channel, long position, long count) throws IOException {
        if (channel instanceof StreamSourceChannel) {
            return drain((StreamSourceChannel) channel, count);
        } else {
            long total = 0L, lres;
            int ires;
            ByteBuffer buffer = null;
            for (;;) {
                if (count == 0L) return total;
                if (NULL_FILE_CHANNEL != null) {
                    while (count > 0) {
                        if ((lres = channel.transferTo(position, count, NULL_FILE_CHANNEL)) == 0L) {
                            break;
                        }
                        total += lres;
                        count -= lres;
                    }
                    // jump out quick if we drained the fast way
                    if (total > 0L) return total;
                }
                if (buffer == null) buffer = DRAIN_BUFFER.duplicate();
                if ((long) buffer.limit() > count) buffer.limit((int) count);
                ires = channel.read(buffer);
                buffer.clear();
                switch (ires) {
                    case -1: return total == 0L ? -1L : total;
                    case 0: return total;
                    default: total += (long) ires;
                }
            }
        }
    }

    /**
     * Resume reads asynchronously.  Queues a task on the channel's I/O thread to resume.  Note that if a channel
     * has multiple threads associated with it, the results may not be desirable.
     *
     * @param channel the channel to resume
     */
    public static void resumeReadsAsync(final SuspendableReadChannel channel) {
        final XnioIoThread ioThread = channel.getIoThread();
        if (ioThread == Thread.currentThread()) {
            channel.resumeReads();
        } else {
            ioThread.execute(new Runnable() {
                public void run() {
                    channel.resumeReads();
                }
            });
        }
    }

    /**
     * Resume writes asynchronously.  Queues a task on the channel's I/O thread to resume.  Note that if a channel
     * has multiple threads associated with it, the results may not be desirable.
     *
     * @param channel the channel to resume
     */
    public static void resumeWritesAsync(final SuspendableWriteChannel channel) {
        final XnioIoThread ioThread = channel.getIoThread();
        if (ioThread == Thread.currentThread()) {
            channel.resumeWrites();
        } else {
            ioThread.execute(new Runnable() {
                public void run() {
                    channel.resumeWrites();
                }
            });
        }
    }

    /**
     * Writes out the data in the buffer to the channel. If all the data is written out
     * then the channel will have its writes shutdown.
     *
     * @param channel The channel
     * @param src The buffer
     * @return The number of bytes written
     * @throws IOException
     */
    public static int writeFinalBasic(StreamSinkChannel channel, ByteBuffer src) throws IOException {
        int res = channel.write(src);
        if(!src.hasRemaining()) {
            channel.shutdownWrites();
        }
        return res;
    }

    /**
     * Writes out the data in the buffer to the channel. If all the data is written out
     * then the channel will have its writes shutdown.
     *
     * @param channel The channel
     * @param srcs The buffers
     * @param offset The offset into the srcs array
     * @param length The number buffers to write
     * @return The number of bytes written
     * @throws IOException
     */
    public static long writeFinalBasic(StreamSinkChannel channel, ByteBuffer[] srcs, int offset, int length) throws IOException {
        final long res = channel.write(srcs, offset, length);
        if (!Buffers.hasRemaining(srcs, offset, length)) {
            channel.shutdownWrites();
        }
        return res;
    }

    static {
        NULL_FILE_CHANNEL = AccessController.doPrivileged(new PrivilegedAction<FileChannel>() {
            public FileChannel run() {
                final String osName = System.getProperty("os.name", "unknown").toLowerCase(Locale.US);
                try {
                    if (osName.contains("windows")) {
                        return new FileOutputStream("NUL:").getChannel();
                    } else {
                        return new FileOutputStream("/dev/null").getChannel();
                    }
                } catch (FileNotFoundException e) {
                    throw new IOError(e);
                }
            }
        });
    }
}
