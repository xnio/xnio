/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2011 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
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

    FileChannel getDelegate() {
        return delegate;
    }
}
