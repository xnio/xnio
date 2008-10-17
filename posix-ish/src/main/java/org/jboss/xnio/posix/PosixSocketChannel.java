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

package org.jboss.xnio.posix;

import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.Configurable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.nio.ByteBuffer;

/**
 *
 */
public final class PosixSocketChannel extends PosixChannel implements StreamChannel {

    PosixSocketChannel(int fd) {
        super(fd);
    }

    public void suspendReads() {
    }

    public void resumeReads() {
    }

    public void shutdownReads() throws IOException {
    }

    public void awaitReadable() throws IOException {
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
    }

    public <T> T getOption(final ChannelOption<T> option) throws UnsupportedOptionException, IOException {
        return null;
    }

    public Set<ChannelOption<?>> getOptions() {
        return null;
    }

    public <T> Configurable setOption(final ChannelOption<T> option, final T value) throws IllegalArgumentException, IOException {
        return null;
    }

    public void suspendWrites() {
    }

    public void resumeWrites() {
    }

    public void shutdownWrites() throws IOException {
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
