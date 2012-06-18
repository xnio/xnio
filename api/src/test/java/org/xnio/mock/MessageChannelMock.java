/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.xnio.mock;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.ChannelListener.Setter;
import org.xnio.channels.MessageChannel;
import org.xnio.channels.ReadableMessageChannel;
import org.xnio.channels.WritableMessageChannel;

/**
 * Mock for {@code ReadableMessageChannel} and {@code WritableMessageChannel}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class MessageChannelMock implements ReadableMessageChannel, WritableMessageChannel {

    private final ConnectedStreamChannelMock channel;
    private boolean writeResumed = false;

    private ChannelListener<? super WritableMessageChannel> writeListener;
    private final ChannelListener.Setter<WritableMessageChannel> writeListenerSetter = new ChannelListener.Setter<WritableMessageChannel>() {
        @Override
        public void set(ChannelListener<? super WritableMessageChannel> listener) {
            writeListener = listener;
        }
    };

    public MessageChannelMock(ConnectedStreamChannelMock c) {
        channel = c;
    }

    @Override
    public void suspendReads() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void resumeReads() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isReadResumed() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void wakeupReads() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void shutdownReads() throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void awaitReadable() throws IOException {
        channel.awaitReadable();
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        channel.awaitReadable(time, timeUnit);
    }

    @Override
    public XnioExecutor getReadThread() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public XnioWorker getWorker() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void close() throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isOpen() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void suspendWrites() {
        writeResumed = false;
    }

    @Override
    public void resumeWrites() {
        writeResumed = true;
    }

    @Override
    public boolean isWriteResumed() {
        return writeResumed;
    }

    @Override
    public void wakeupWrites() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void shutdownWrites() throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void awaitWritable() throws IOException {
        channel.awaitWritable();
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        channel.awaitWritable(time, timeUnit);
    }

    @Override
    public XnioExecutor getWriteThread() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean flush() throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean send(ByteBuffer buffer) throws IOException {
        return !buffer.hasRemaining() || channel.write(buffer) > 0;
    }

    @Override
    public boolean send(ByteBuffer[] buffers) throws IOException {
        return !Buffers.hasRemaining(buffers) || channel.write(buffers) > 0;
    }

    @Override
    public boolean send(ByteBuffer[] buffers, int offs, int len) throws IOException {
        return !Buffers.hasRemaining(buffers, offs, len) || channel.write(buffers, offs, len) > 0;
    }

    @Override
    public Setter<? extends WritableMessageChannel> getWriteSetter() {
        return writeListenerSetter;
    }

    @Override
    public int receive(ByteBuffer buffer) throws IOException {
        return channel.read(buffer);
    }

    @Override
    public long receive(ByteBuffer[] buffers) throws IOException {
        return channel.read(buffers);
    }

    @Override
    public long receive(ByteBuffer[] buffers, int offs, int len) throws IOException {
        return channel.read(buffers, offs, len);
    }

    @Override
    public Setter<? extends ReadableMessageChannel> getReadSetter() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Setter<MessageChannel> getCloseSetter() {
        throw new RuntimeException("Not implemented");
    }

    public ChannelListener<? super WritableMessageChannel> getWriteListener() {
        return writeListener;
    }
}
