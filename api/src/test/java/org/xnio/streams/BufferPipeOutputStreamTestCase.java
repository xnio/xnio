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
package org.xnio.streams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.xnio.Buffers;
import org.xnio.ByteBufferSlicePool;
import org.xnio.Pooled;

/**
 * Test for {@link BufferPipeOutputStream}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class BufferPipeOutputStreamTestCase {

    private BufferPipeOutputStream stream;
    private TestBufferWriter bufferWriter;

    @Before
    public void init() throws IOException {
        bufferWriter = new TestBufferWriter();
        stream = new BufferPipeOutputStream(bufferWriter);
    }

    @Test
    public void writeAndFlush() throws IOException {
        stream.write("test".getBytes("UTF-8"));
        assertTrue(bufferWriter.getAcceptedBuffers().isEmpty());
        stream.flush();
        List<Pooled<ByteBuffer>> acceptedBuffers = bufferWriter.getAcceptedBuffers();
        assertEquals(1, acceptedBuffers.size());
        assertAcceptedMessage(acceptedBuffers.get(0).getResource(), "test");
    }

    @Test
    public void writeBytesAndFlush() throws IOException {
        stream.write('b');
        stream.write('y');
        stream.write('t');
        stream.write('e');
        stream.write('s');
        assertTrue(bufferWriter.getAcceptedBuffers().isEmpty());
        stream.flush();
        List<Pooled<ByteBuffer>> acceptedBuffers = bufferWriter.getAcceptedBuffers();
        assertEquals(1, acceptedBuffers.size());
        assertAcceptedMessage(acceptedBuffers.get(0).getResource(), "bytes");
    }

    @Test
    public void writeAndFlushRepeatedly() throws IOException {
        // flush empty stream
        stream.flush();
        // nothing happens
        assertTrue(bufferWriter.getAcceptedBuffers().isEmpty());

        stream.write("test".getBytes("UTF-8"));
        assertTrue(bufferWriter.getAcceptedBuffers().isEmpty());
        stream.flush();
        stream.write("repeatedly".getBytes("UTF-8"));
        assertEquals(2, bufferWriter.getAcceptedBuffers().size());
        // flush more than once, flush should be idempotent
        stream.flush();
        stream.flush();
        stream.write("again".getBytes("UTF-8"));
        assertEquals(3, bufferWriter.getAcceptedBuffers().size());
        stream.flush();
        stream.write("and again".getBytes("UTF-8"));
        assertEquals(5, bufferWriter.getAcceptedBuffers().size());
        stream.flush();
        List<Pooled<ByteBuffer>> acceptedBuffers = bufferWriter.getAcceptedBuffers();
        assertEquals(6, acceptedBuffers.size());
        assertAcceptedMessage(acceptedBuffers.get(0).getResource(), "test");
        assertAcceptedMessage(acceptedBuffers.get(1).getResource(), "repea");
        assertAcceptedMessage(acceptedBuffers.get(2).getResource(), "tedly");
        assertAcceptedMessage(acceptedBuffers.get(3).getResource(), "again");
        assertAcceptedMessage(acceptedBuffers.get(4).getResource(), "and a");
        assertAcceptedMessage(acceptedBuffers.get(5).getResource(), "gain");
    }

    @Test
    public void writeBytesAndFlushRepeatedly() throws IOException {
        stream.write("r".getBytes("UTF-8"));
        assertTrue(bufferWriter.getAcceptedBuffers().isEmpty());
        stream.flush();
        stream.write("e".getBytes("UTF-8"));
        assertEquals(1, bufferWriter.getAcceptedBuffers().size());
        stream.flush();
        stream.write("p".getBytes("UTF-8"));
        assertEquals(2, bufferWriter.getAcceptedBuffers().size());
        stream.flush();
        stream.write("e".getBytes("UTF-8"));
        assertEquals(3, bufferWriter.getAcceptedBuffers().size());
        stream.flush();
        stream.write("a".getBytes("UTF-8"));
        assertEquals(4, bufferWriter.getAcceptedBuffers().size());
        stream.flush();
        stream.write("t".getBytes("UTF-8"));
        assertEquals(5, bufferWriter.getAcceptedBuffers().size());
        stream.flush();
        stream.write("e".getBytes("UTF-8"));
        assertEquals(6, bufferWriter.getAcceptedBuffers().size());
        stream.flush();
        stream.write("d".getBytes("UTF-8"));
        assertEquals(7, bufferWriter.getAcceptedBuffers().size());
        stream.flush();
        stream.write("l".getBytes("UTF-8"));
        assertEquals(8, bufferWriter.getAcceptedBuffers().size());
        stream.flush();
        stream.write("y".getBytes("UTF-8"));
        assertEquals(9, bufferWriter.getAcceptedBuffers().size());
        // flush more than once, flush should be idempotent
        stream.flush();
        stream.flush();
        List<Pooled<ByteBuffer>> acceptedBuffers = bufferWriter.getAcceptedBuffers();
        assertEquals(10, acceptedBuffers.size());
        assertAcceptedMessage(acceptedBuffers.get(0).getResource(), "r");
        assertAcceptedMessage(acceptedBuffers.get(1).getResource(), "e");
        assertAcceptedMessage(acceptedBuffers.get(2).getResource(), "p");
        assertAcceptedMessage(acceptedBuffers.get(3).getResource(), "e");
        assertAcceptedMessage(acceptedBuffers.get(4).getResource(), "a");
        assertAcceptedMessage(acceptedBuffers.get(5).getResource(), "t");
        assertAcceptedMessage(acceptedBuffers.get(6).getResource(), "e");
        assertAcceptedMessage(acceptedBuffers.get(7).getResource(), "d");
        assertAcceptedMessage(acceptedBuffers.get(8).getResource(), "l");
        assertAcceptedMessage(acceptedBuffers.get(9).getResource(), "y");
    }

    @Test
    public void closeEmptyStream() throws IOException {
        assertTrue(bufferWriter.getAcceptedBuffers().isEmpty());
        assertTrue(bufferWriter.isFlushed());
        assertFalse(bufferWriter.isEof());
        stream.close();
        assertClosedChannel(true, true, 1, "");
    }

    @Test
    public void close() throws IOException {
        assertTrue(bufferWriter.getAcceptedBuffers().isEmpty());
        assertTrue(bufferWriter.isFlushed());
        assertFalse(bufferWriter.isEof());
        stream.write("test".getBytes());
        assertTrue(bufferWriter.getAcceptedBuffers().isEmpty());
        assertTrue(bufferWriter.isFlushed());
        assertFalse(bufferWriter.isEof());
        stream.close();
        assertClosedChannel(true, true, 1, "test");
    }

    @Test
    public void bufferWriterAcceptThrowsIOException() throws IOException {
        stream.write("12345".getBytes("UTF-8"));
        assertTrue(bufferWriter.getAcceptedBuffers().isEmpty());
        assertTrue(bufferWriter.isFlushed());
        assertFalse(bufferWriter.isEof());

        bufferWriter.throwIOExceptionOnAccept();
        IOException exceptionThrownByAccept = null;
        try {
            stream.write("won't accept".getBytes("UTF-8"));
        } catch (IOException e) {
            exceptionThrownByAccept = e;
        }
        assertNotNull(exceptionThrownByAccept);
        assertClosedChannel(true, false, 0);
    }

    @Test
    public void bufferWriterFlushThrowsIOException() throws IOException {
        stream.write('1');
        stream.write("23456".getBytes("UTF-8"));
        List<Pooled<ByteBuffer>> acceptedBuffers = bufferWriter.getAcceptedBuffers();
        assertEquals(1, acceptedBuffers.size());
        assertAcceptedMessage(acceptedBuffers.get(0).getResource(), "12345");
        assertFalse(bufferWriter.isFlushed());
        assertFalse(bufferWriter.isEof());

        bufferWriter.throwIOExceptionOnFlush();
        IOException exceptionThrownByFlush = null;
        try {
            stream.flush();
        } catch (IOException e) {
            exceptionThrownByFlush= e;
        }
        assertNotNull(exceptionThrownByFlush);
        acceptedBuffers.get(0).getResource().flip();
        assertClosedChannel(false, false, 2, "12345", "6");
    }

    private void assertClosedChannel(boolean flushed, boolean flushedEof, int numberOfAcceptedBuffers, String... bufferContents) throws IOException {
        List<Pooled<ByteBuffer>> acceptedBuffers = bufferWriter.getAcceptedBuffers();
        assertEquals(numberOfAcceptedBuffers, acceptedBuffers.size());
        for (int i = 0; i < bufferContents.length; i++) {
            assertAcceptedMessage(acceptedBuffers.get(i).getResource(), bufferContents[i]);
        }
        assertEquals(flushed, bufferWriter.isFlushed());
        assertEquals(flushedEof, bufferWriter.isEof());
        // close is idempotent
        stream.close();
        assertEquals(numberOfAcceptedBuffers, bufferWriter.getAcceptedBuffers().size());
        assertEquals(flushed, bufferWriter.isFlushed());
        assertEquals(flushedEof, bufferWriter.isEof());
        // can't write
        IOException cantWriteException = null;
        try {
            stream.write("can't write this".getBytes());
        } catch (IOException e) {
            cantWriteException = e;
        }
        assertNotNull(cantWriteException);
        cantWriteException = null;
        try {
            stream.write('a');
        } catch (IOException e) {
            cantWriteException = e;
        }
        assertNotNull(cantWriteException);
        // flushing is useless after closed
        stream.flush();
        assertEquals(numberOfAcceptedBuffers, bufferWriter.getAcceptedBuffers().size());
        assertEquals(flushed, bufferWriter.isFlushed());
        assertEquals(flushedEof, bufferWriter.isEof());
    }

    private static final void assertAcceptedMessage(ByteBuffer dst, String... message) {
        final StringBuffer stringBuffer = new StringBuffer();
        for (String messageString: message) {
            stringBuffer.append(messageString);
        }
        assertEquals(stringBuffer.toString(), Buffers.getModifiedUtf8(dst));
    }

    private static class TestBufferWriter implements BufferPipeOutputStream.BufferWriter {

        private ByteBufferSlicePool bufferPool = new ByteBufferSlicePool(5, 10);
        private List<Pooled<ByteBuffer>> acceptedBuffers = new ArrayList<Pooled<ByteBuffer>>();
        private boolean eof = false;;
        private boolean flushed = true;
        private boolean throwIOExceptionOnAccept = false;
        private boolean throwIOExceptionOnFlush = false;

        @Override
        public Pooled<ByteBuffer> getBuffer(boolean firstBuffer) throws IOException {
            return bufferPool.allocate();
        }

        @Override
        public void accept(Pooled<ByteBuffer> pooledBuffer, boolean eof) throws IOException {
            if (throwIOExceptionOnAccept) {
                throwIOExceptionOnAccept = false;
                throw new IOException("test requested bufferWriter to throw IOException on accept");
            }
            acceptedBuffers.add(pooledBuffer);
            this.eof = this.eof || eof;
            flushed = false;
        }

        @Override
        public void flush() throws IOException {
            if (throwIOExceptionOnFlush) {
                throwIOExceptionOnFlush = false;
                throw new IOException("test requested bufferWriter to throw IOException on flush");
            }
            flushed = true;
        }

        public boolean isFlushed() {
            return flushed;
        }

        public  List<Pooled<ByteBuffer>> getAcceptedBuffers() {
            return acceptedBuffers;
        }

        public boolean isEof() {
            return eof;
        }

        public void throwIOExceptionOnAccept() {
            throwIOExceptionOnAccept = true;
        }

        public void throwIOExceptionOnFlush() {
            throwIOExceptionOnFlush = true;
        }
    }
}
