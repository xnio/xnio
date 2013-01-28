package org.xnio.conduits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import org.xnio.ChannelListener;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * A conduit which wraps a channel, for compatibility.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class StreamSourceChannelWrappingConduit implements StreamSourceConduit {
    private final StreamSourceChannel channel;

    /**
     * Construct a new instance.
     *
     * @param channel the channel to wrap
     */
    public StreamSourceChannelWrappingConduit(final StreamSourceChannel channel) {
        this.channel = channel;
    }

    public void terminateReads() throws IOException {
        channel.shutdownReads();
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return channel.transferTo(position, count, target);
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return channel.transferTo(count, throughBuffer, target);
    }

    public int read(final ByteBuffer dst) throws IOException {
        return channel.read(dst);
    }

    public long read(final ByteBuffer[] dsts, final int offs, final int len) throws IOException {
        return channel.read(dsts, offs, len);
    }

    public boolean isReadShutdown() {
        return ! channel.isOpen();
    }

    public void resumeReads() {
        channel.resumeReads();
    }

    public void suspendReads() {
        channel.suspendReads();
    }

    public void wakeupReads() {
        channel.wakeupReads();
    }

    public boolean isReadResumed() {
        return channel.isReadResumed();
    }

    public void awaitReadable() throws IOException {
        channel.awaitReadable();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        channel.awaitReadable(time, timeUnit);
    }

    public XnioIoThread getReadThread() {
        return channel.getIoThread();
    }

    public void setReadReadyHandler(final ReadReadyHandler handler) {
        channel.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
            public void handleEvent(final StreamSourceChannel channel) {
                handler.readReady();
            }
        });
    }

    public XnioWorker getWorker() {
        return channel.getWorker();
    }
}
