package org.xnio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class InflateStreamSourceChannel implements StreamSourceChannel {
    private final StreamSourceChannel delegate;
    private final Inflater inflater;
    private final ByteBuffer buffer;

    private ChannelListener<? super InflateStreamSourceChannel> readListener;
    private ChannelListener<? super InflateStreamSourceChannel> closeListener;

    InflateStreamSourceChannel(final StreamSourceChannel delegate, final Inflater inflater) {
        this.delegate = delegate;
        this.inflater = inflater;
        delegate.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
            public void handleEvent(final StreamSourceChannel channel) {
                ChannelListeners.invokeChannelListener(InflateStreamSourceChannel.this, readListener);
            }
        });
        delegate.getCloseSetter().set(new ChannelListener<StreamSourceChannel>() {
            public void handleEvent(final StreamSourceChannel channel) {
                ChannelListeners.invokeChannelListener(InflateStreamSourceChannel.this, closeListener);
            }
        });
        buffer = ByteBuffer.allocate(16384);
    }

    public boolean isOpen() {
        return delegate.isOpen();
    }

    public void setReadListener(final ChannelListener<? super InflateStreamSourceChannel> readListener) {
        this.readListener = readListener;
    }

    public void setCloseListener(final ChannelListener<? super InflateStreamSourceChannel> closeListener) {
        this.closeListener = closeListener;
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getReadSetter() {
        return new ChannelListener.Setter<StreamSourceChannel>() {
            public void set(final ChannelListener<? super StreamSourceChannel> listener) {
                setReadListener(listener);
            }
        };
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getCloseSetter() {
        return new ChannelListener.Setter<StreamSourceChannel>() {
            public void set(final ChannelListener<? super StreamSourceChannel> listener) {
                setCloseListener(listener);
            }
        };
    }

    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return target.transferFrom(this, position, count);
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return IoUtils.transfer(this, count, throughBuffer, target);
    }

    public int read(final ByteBuffer dst) throws IOException {
        final int remaining = dst.remaining();
        final int position = dst.position();
        final Inflater inflater = this.inflater;
        int res;
        if (dst.hasArray()) {
            // fast path
            final byte[] array = dst.array();
            final int off = dst.arrayOffset();
            for (;;) {
                try {
                    res = inflater.inflate(array, off + position, remaining);
                } catch (DataFormatException e) {
                    throw new IOException(e);
                }
                if (res > 0) {
                    dst.position(position + res);
                    return res;
                }
                if (inflater.needsDictionary()) {
                    throw new IOException("Needs dictionary");
                }
                final ByteBuffer buffer = this.buffer;
                buffer.clear();
                res = delegate.read(buffer);
                if (res > 0) {
                    inflater.setInput(buffer.array(), buffer.arrayOffset(), res);
                } else {
                    return res;
                }
            }
        } else {
            final byte[] space = new byte[remaining];
            for (;;) {
                try {
                    res = inflater.inflate(space);
                } catch (DataFormatException e) {
                    throw new IOException(e);
                }
                if (res > 0) {
                    dst.put(space, 0, res);
                    return res;
                }
                if (inflater.needsDictionary()) {
                    throw new IOException("Needs dictionary");
                }
                final ByteBuffer buffer = this.buffer;
                buffer.clear();
                res = delegate.read(buffer);
                if (res > 0) {
                    inflater.setInput(buffer.array(), buffer.arrayOffset(), res);
                } else {
                    return res;
                }
            }
        }
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        for (int i = 0; i < length; i ++) {
            final ByteBuffer buffer = dsts[i + offset];
            if (buffer.hasRemaining()) {
                return read(buffer);
            }
        }
        return 0L;
    }

    public void suspendReads() {
        delegate.suspendReads();
    }

    public void resumeReads() {
        delegate.resumeReads();
    }

    public boolean isReadResumed() {
        return delegate.isReadResumed();
    }

    public void wakeupReads() {
        delegate.wakeupReads();
    }

    public void shutdownReads() throws IOException {
        inflater.end();
        delegate.shutdownReads();
    }

    public void awaitReadable() throws IOException {
        if (! inflater.needsInput()) {
            return;
        }
        delegate.awaitReadable();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        if (! inflater.needsInput()) {
            return;
        }
        delegate.awaitReadable(time, timeUnit);
    }

    public XnioExecutor getReadThread() {
        return delegate.getReadThread();
    }

    public void close() throws IOException {
        inflater.end();
        delegate.close();
    }

    public boolean supportsOption(final Option<?> option) {
        return delegate.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return delegate.getOption(option);
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return delegate.setOption(option, value);
    }

}
