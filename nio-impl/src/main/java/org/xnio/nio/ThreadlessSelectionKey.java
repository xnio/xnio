package org.xnio.nio;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ThreadlessSelectionKey extends SelectionKey {
    private final NioXnioWorker worker;
    private final SelectableChannel channel;
    private int ops;

    ThreadlessSelectionKey(final NioXnioWorker worker, final SelectableChannel channel) {
        this.worker = worker;
        this.channel = channel;
    }

    public SelectableChannel channel() {
        return channel;
    }

    public Selector selector() {
        return null;
    }

    public boolean isValid() {
        return true;
    }

    public void cancel() {
    }

    public int interestOps() {
        return ops;
    }

    public SelectionKey interestOps(final int ops) {
        this.ops = ops;
        return this;
    }

    public int readyOps() {
        return 0;
    }

    NioXnioWorker getWorker() {
        return worker;
    }
}
