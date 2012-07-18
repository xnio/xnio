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

import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * A simple allocator for buffers.
 *
 * @param <B> the buffer type
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface BufferAllocator<B extends Buffer> {

    /**
     * Allocate a buffer of the given size.
     *
     * @param size the size
     * @return the buffer
     * @throws IllegalArgumentException if the given buffer size is less than zero
     */
    B allocate(int size) throws IllegalArgumentException;

    /**
     * A simple allocator for heap-array-backed byte buffers.
     */
    BufferAllocator<ByteBuffer> BYTE_BUFFER_ALLOCATOR = new BufferAllocator<ByteBuffer>() {
        public ByteBuffer allocate(final int size) {
            return ByteBuffer.allocate(size);
        }
    };

    /**
     * A simple allocator for direct byte buffers.
     */
    BufferAllocator<ByteBuffer> DIRECT_BYTE_BUFFER_ALLOCATOR = new BufferAllocator<ByteBuffer>() {
        public ByteBuffer allocate(final int size) {
            return ByteBuffer.allocateDirect(size);
        }
    };
}
