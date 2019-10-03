/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2019 Red Hat, Inc. and/or its affiliates, and individual
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

import java.nio.ByteBuffer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test for {@link ByteBufferSlicePool}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class ByteBufferSlicePoolTestCase {

    @Test
    public void basicSlicePool() {
        ByteBufferSlicePool slicePool = new ByteBufferSlicePool(10, 10);
        for (int i = 0; i < 10; i++){
            Pooled<ByteBuffer> pooledBuffer = slicePool.allocate();
            assertNotNull(pooledBuffer);
            ByteBuffer buffer = pooledBuffer.getResource();
            assertNotNull(buffer);
            assertEquals(10, buffer.capacity());
            assertEquals(0, buffer.position());
            pooledBuffer.free();
            boolean failed = false;
            try {
                pooledBuffer.getResource();
            } catch (IllegalStateException expected) {
                failed = true;
            }
            assertTrue(failed);
        }
    }

    @Test
    public void bufferAllocatorSlicePool() {
        ByteBufferSlicePool slicePool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 2, 5);
        for (int i = 0; i < 2; i++){
            Pooled<ByteBuffer> pooledBuffer = slicePool.allocate();
            assertNotNull(pooledBuffer);
            ByteBuffer buffer = pooledBuffer.getResource();
            assertNotNull(buffer);
            assertEquals(2, buffer.capacity());
            assertEquals(0, buffer.position());
            pooledBuffer.free();
            boolean failed = false;
            try {
                pooledBuffer.getResource();
            } catch (IllegalStateException expected) {
                failed = true;
            }
            assertTrue(failed);
        }
    }

    @Test
    public void bufferAllocatorNoThreadLocalSlicePool() {
        ByteBufferSlicePool slicePool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 8, 12, 0);
        Pooled<ByteBuffer> pooledBuffer = slicePool.allocate();
        assertNotNull(pooledBuffer);
        ByteBuffer buffer = pooledBuffer.getResource();
        assertNotNull(buffer);
        assertEquals(8, buffer.capacity());
        assertEquals(0, buffer.position());
        pooledBuffer.free();
        boolean failed = false;
        try {
            pooledBuffer.getResource();
        } catch (IllegalStateException expected) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void directBufferAllocator() throws InterruptedException {
        for (int i = 0; i < 100; i++)
            stressDirectBufferReuse();
    }

    private void stressDirectBufferReuse() throws InterruptedException {
        ByteBufferSlicePool slicePool = new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 8, 24);
        slicePool.allocate();
        slicePool.clean();

        // reusing the same size
        slicePool = new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 8, 24);
        slicePool.allocate();
        slicePool.clean();

        // reusing for a smaller size
        slicePool = new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 7, 21);
        slicePool.allocate();
        slicePool.clean();

        // reusing for a bigger size
        slicePool = new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 10, 20);
        slicePool.allocate();
        slicePool.clean();
    }


    @Test
    public void directBufferAllocator2() throws InterruptedException {
        for (int i = 0; i < 100; i++)
            stressDirectBufferReuse2();
    }

    private void stressDirectBufferReuse2() throws InterruptedException {
        ByteBufferSlicePool slicePool = new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 8, 24);
        slicePool.allocate();
        slicePool.clean();

        // reusing the same size
        slicePool = new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 8, 24);
        slicePool.allocate();
        slicePool.clean();

        // reusing for a smaller size
        slicePool = new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 7, 21);
        slicePool.allocate();
        slicePool.clean();

        // reusing for a bigger size whose first buffer is bigger than all previous allocated byte buffer regions
        slicePool = new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 250, 10000);
        slicePool.allocate();
        slicePool.clean();
    }

}