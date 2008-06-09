package org.jboss.xnio.core.nio;

import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.ConfigurableChannel;
import org.jboss.xnio.IoHandler;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.Collections;

/**
 *
 */
public final class NioPipeChannelImpl implements StreamChannel {
    private final Pipe.SourceChannel sourceChannel;
    private final Pipe.SinkChannel sinkChannel;
    private final IoHandler<? super StreamChannel> handler;
    private final NioHandle sourceHandle;
    private final NioHandle sinkHandle;
    private final AtomicBoolean callFlag = new AtomicBoolean(false);

    public NioPipeChannelImpl(final Pipe.SourceChannel sourceChannel, final Pipe.SinkChannel sinkChannel, final IoHandler<? super StreamChannel> handler, final NioProvider nioProvider) throws IOException {
        this.sourceChannel = sourceChannel;
        this.sinkChannel = sinkChannel;
        this.handler = handler;
        // todo leaking [this]
        sourceHandle = nioProvider.addReadHandler(sourceChannel, new ReadHandler());
        sinkHandle = nioProvider.addWriteHandler(sinkChannel, new WriteHandler());
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        return sinkChannel.write(srcs, offset, length);
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return sinkChannel.write(srcs);
    }

    public int write(final ByteBuffer src) throws IOException {
        return sinkChannel.write(src);
    }

    public boolean isOpen() {
        return sourceChannel.isOpen() && sinkChannel.isOpen();
    }

    public void close() throws IOException {
        // since we've got two channels, only rethrow a failure on the WRITE side, since that's the side that stands to lose data
        try {
            sourceChannel.close();
        } catch (Throwable t) {
            // todo log @ trace
        }
        try {
            sinkChannel.close();
        } finally {
            if (callFlag.getAndSet(true) == false) try {
                handler.handleClose(this);
            } catch (Throwable t) {
                // todo log it
            }
            sinkHandle.cancelKey();
            sourceHandle.cancelKey();
        }
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        return sourceChannel.read(dsts, offset, length);
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return sourceChannel.read(dsts);
    }

    public int read(final ByteBuffer dst) throws IOException {
        return sourceChannel.read(dst);
    }

    public void suspendReads() {
        try {
            sourceHandle.getSelectionKey().interestOps(0).selector().wakeup();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void suspendWrites() {
        try {
            sinkHandle.getSelectionKey().interestOps(0).selector().wakeup();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeReads() {
        try {
            sourceHandle.getSelectionKey().interestOps(SelectionKey.OP_READ).selector().wakeup();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeWrites() {
        try {
            sinkHandle.getSelectionKey().interestOps(SelectionKey.OP_WRITE).selector().wakeup();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void shutdownReads() throws IOException {
        sourceChannel.close();
    }

    public void shutdownWrites() throws IOException {
        sinkChannel.close();
    }

    public Object getOption(final String name) throws UnsupportedOptionException, IOException {
        throw new UnsupportedOptionException("No options supported");
    }

    public Map<String, Class<?>> getOptions() {
        return Collections.emptyMap();
    }

    public ConfigurableChannel setOption(final String name, final Object value) throws IllegalArgumentException, IOException {
        throw new UnsupportedOptionException("No options supported");
    }

    private class ReadHandler implements Runnable {
        public void run() {
            IoHandler<? super StreamChannel> handler = NioPipeChannelImpl.this.handler;
            try {
                handler.handleReadable(NioPipeChannelImpl.this);
            } catch (Throwable t) {
                // todo log it
                t.printStackTrace();
            }
        }
    }

    private class WriteHandler implements Runnable {
        public void run() {
            IoHandler<? super StreamChannel> handler = NioPipeChannelImpl.this.handler;
            try {
                handler.handleWritable(NioPipeChannelImpl.this);
            } catch (Throwable t) {
                // todo log it
                t.printStackTrace();
            }
        }
    }
}
