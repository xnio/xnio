/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
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

package org.xnio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

final class XnioFileChannel extends FileChannel {
    private final FileChannel delegate;

    XnioFileChannel(final FileChannel delegate) {
        this.delegate = delegate;
    }

    public int read(final ByteBuffer dst) throws IOException {
        return delegate.read(dst);
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        return delegate.read(dsts, offset, length);
    }

    public int write(final ByteBuffer src) throws IOException {
        return delegate.write(src);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        return delegate.write(srcs, offset, length);
    }

    public long position() throws IOException {
        return delegate.position();
    }

    public FileChannel position(final long newPosition) throws IOException {
        return delegate.position(newPosition);
    }

    public long size() throws IOException {
        return delegate.size();
    }

    public FileChannel truncate(final long size) throws IOException {
        return delegate.truncate(size);
    }

    public void force(final boolean metaData) throws IOException {
        delegate.force(metaData);
    }

    public long transferTo(final long position, final long count, final WritableByteChannel target) throws IOException {
        if (target instanceof StreamSinkChannel) {
            return ((StreamSinkChannel) target).transferFrom(delegate, position, count);
        } else {
            return delegate.transferTo(position, count, target);
        }
    }

    public long transferFrom(final ReadableByteChannel src, final long position, final long count) throws IOException {
        if (src instanceof StreamSourceChannel) {
            return ((StreamSourceChannel) src).transferTo(position, count, delegate);
        } else {
            return delegate.transferFrom(src, position, count);
        }
    }

    public int read(final ByteBuffer dst, final long position) throws IOException {
        return delegate.read(dst, position);
    }

    public int write(final ByteBuffer src, final long position) throws IOException {
        return delegate.write(src, position);
    }

    public MappedByteBuffer map(final MapMode mode, final long position, final long size) throws IOException {
        return delegate.map(mode, position, size);
    }

    public FileLock lock(final long position, final long size, final boolean shared) throws IOException {
        return delegate.lock(position, size, shared);
    }

    public FileLock tryLock(final long position, final long size, final boolean shared) throws IOException {
        return delegate.tryLock(position, size, shared);
    }

    public void implCloseChannel() throws IOException {
        delegate.close();
    }
}
