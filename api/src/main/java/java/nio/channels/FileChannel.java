/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package java.nio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.spi.AbstractInterruptibleChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;

/**
 * Compatibility stub.
 */
public abstract class FileChannel extends AbstractInterruptibleChannel implements GatheringByteChannel, ScatteringByteChannel {
    protected FileChannel() {}

    public static FileChannel open(Path path, OpenOption... options) throws IOException {return null;};
    public abstract int read(ByteBuffer dst) throws IOException;
    public abstract long read(ByteBuffer[] dsts, int offset, int length) throws IOException;
    public final long read(ByteBuffer[] dsts) throws IOException { return 0L; }
    public abstract int write(ByteBuffer src) throws IOException;
    public abstract long write(ByteBuffer[] srcs, int offset, int length) throws IOException;
    public final long write(ByteBuffer[] srcs) throws IOException { return 0L; }
    public abstract long position() throws IOException;
    public abstract FileChannel position(long newPosition) throws IOException;
    public abstract long size() throws IOException;
    public abstract FileChannel truncate(long size) throws IOException;
    public abstract void force(boolean metaData) throws IOException;
    public abstract long transferTo(long position, long count, WritableByteChannel target) throws IOException;
    public abstract long transferFrom(ReadableByteChannel src, long position, long count) throws IOException;
    public abstract int read(ByteBuffer dst, long position) throws IOException;
    public abstract int write(ByteBuffer src, long position) throws IOException;
    public abstract MappedByteBuffer map(MapMode mode, long position, long size) throws IOException;
    public abstract FileLock lock(long position, long size, boolean shared) throws IOException;
    public final FileLock lock() throws IOException { return null; };
    public abstract FileLock tryLock(long position, long size, boolean shared) throws IOException;
    public final FileLock tryLock() throws IOException { return null; }

    public static class MapMode {
        public static final MapMode READ_ONLY = null;
        public static final MapMode READ_WRITE = null;
        public static final MapMode PRIVATE = null;
    }
}
