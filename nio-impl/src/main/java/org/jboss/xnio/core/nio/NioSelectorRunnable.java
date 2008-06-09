package org.jboss.xnio.core.nio;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 *
 */
public final class NioSelectorRunnable implements Runnable {

    private final Selector selector;
    private final Queue<SelectorTask> selectorWorkQueue = new LinkedList<SelectorTask>();
    private volatile int keyLoad;
    private final Executor handlerExecutor;

    protected NioSelectorRunnable(final Executor handlerExecutor) throws IOException {
        this.handlerExecutor = handlerExecutor;
        selector = Selector.open();
    }

    public void queueTask(SelectorTask task) {
        synchronized (selectorWorkQueue) {
            selectorWorkQueue.add(task);
        }
    }

    public void wakeup() {
        selector.wakeup();
    }

    public int getKeyLoad() {
        return keyLoad;
    }

    public void shutdown() {
        try {
            selector.close();
        } catch (Throwable t) {
            // todo log @ trace
            t.printStackTrace();
        }
    }

    public void run() {
        final Selector selector = this.selector;
        for (; ;) {
            try {
                keyLoad = selector.keys().size();
                selector.select();
                synchronized (selectorWorkQueue) {
                    while (! selectorWorkQueue.isEmpty()) {
                        final SelectorTask task = selectorWorkQueue.remove();
                        try {
                            task.run(selector);
                        } catch (Throwable t) {
                            t.printStackTrace();
                            // todo log & ignore
                        }
                    }
                }
                final Set<SelectionKey> selectedKeys = selector.selectedKeys();
                final Iterator<SelectionKey> iterator = selectedKeys.iterator();
                while (iterator.hasNext()) {
                    final SelectionKey key = iterator.next();
                    iterator.remove();
                    try {
                        key.interestOps(key.interestOps() & (SelectionKey.OP_ACCEPT));
                        try {
                            final NioHandle handle = (NioHandle) key.attachment();
                            handle.getHandlerExecutor().execute(handle.getHandler());
                        } catch (Throwable t) {
                            // tough crap
                            // todo log @ trace
                            t.printStackTrace();
                        }
                    } catch (CancelledKeyException e) {
                        // todo log @ trace (normal)
                        // e.printStackTrace();
                        continue;
                    }
                }
            } catch (ClosedSelectorException e) {
                // THIS is the "official" signalling mechanism
                // todo log @ trace (normal)
                // e.printStackTrace();
                return;
            } catch (IOException e) {
                // todo - other I/O errors - should they be fatal?
                e.printStackTrace();
            }
        }
    }
}
