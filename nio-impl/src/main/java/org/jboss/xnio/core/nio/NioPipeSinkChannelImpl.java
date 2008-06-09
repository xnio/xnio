package org.jboss.xnio.core.nio;

import java.nio.channels.Pipe;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.Collections;
import org.jboss.xnio.channels.StreamSinkChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.ConfigurableChannel;
import org.jboss.xnio.IoHandler;

/**
 *
 */
public final class NioPipeSinkChannelImpl implements StreamSinkChannel {
    private final Pipe.SinkChannel channel;
    private final NioHandle handle;
    private final IoHandler<? super StreamSinkChannel> handler;
    private final AtomicBoolean callFlag = new AtomicBoolean(false);

    public NioPipeSinkChannelImpl(final Pipe.SinkChannel channel, final IoHandler<? super StreamSinkChannel> handler, final NioProvider nioProvider) throws IOException {
        this.channel = channel;
        this.handler = handler;
        handle = nioProvider.addWriteHandler(channel, new Handler());
    }

    public int write(final ByteBuffer dst) throws IOException {
        return channel.write(dst);
    }

    public long write(final ByteBuffer[] dsts) throws IOException {
        return channel.write(dsts);
    }

    public long write(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        return channel.write(dsts, offset, length);
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    public void close() throws IOException {
        try {
            channel.close();
        } finally {
            handle.cancelKey();
            if (! callFlag.getAndSet(true)) {
                handler.handleClose(this);
            }
        }
    }

    public void suspendWrites() {
        try {
            handle.getSelectionKey().interestOps(0).selector().wakeup();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void resumeWrites() {
        try {
            handle.getSelectionKey().interestOps(SelectionKey.OP_WRITE).selector().wakeup();
        } catch (CancelledKeyException ex) {
            // ignore
        }
    }

    public void shutdownWrites() throws IOException {
        channel.close();
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

    private final class Handler implements Runnable {
        public void run() {
            IoHandler<? super StreamSinkChannel> handler = NioPipeSinkChannelImpl.this.handler;
            try {
                handler.handleWritable(NioPipeSinkChannelImpl.this);
            } catch (Throwable t) {
                // todo log it
                t.printStackTrace();
            }
        }
    }
}