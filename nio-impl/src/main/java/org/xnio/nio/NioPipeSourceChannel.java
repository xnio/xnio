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
import java.nio.channels.Pipe;
import java.nio.channels.ScatteringByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import org.xnio.Option;
import org.xnio.channels.UnsupportedOptionException;

final class NioPipeSourceChannel extends AbstractNioStreamSourceChannel<NioPipeSourceChannel> {

    private final Pipe.SourceChannel channel;
    private final AtomicBoolean callFlag = new AtomicBoolean(false);

    NioPipeSourceChannel(final NioXnio xnio, final Pipe.SourceChannel channel) {
        super(xnio);
        this.channel = channel;
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    public void close() throws IOException {
        if (! callFlag.getAndSet(true)) {
            invokeCloseHandler();
            channel.close();
        }
    }

    protected ScatteringByteChannel getReadChannel() {
        return channel;
    }

    public void shutdownReads() throws IOException {
        close();
    }

    public boolean supportsOption(final Option<?> option) {
        return false;
    }

    public <T> T getOption(final Option<T> option) throws UnsupportedOptionException, IOException {
        return null;
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return null;
    }

    @Override
    public String toString() {
        return String.format("pipe source channel (NIO) <%s>", Integer.toString(hashCode(), 16));
    }
}
