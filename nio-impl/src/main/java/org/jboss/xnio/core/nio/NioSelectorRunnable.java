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
import java.util.concurrent.atomic.AtomicBoolean;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class NioSelectorRunnable implements Runnable {

    private static final Logger log = Logger.getLogger(NioSelectorRunnable.class);

    private final Selector selector;
    private final Queue<SelectorTask> selectorWorkQueue = new LinkedList<SelectorTask>();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private volatile int keyLoad;
    private volatile Thread thread;

    protected NioSelectorRunnable() throws IOException {
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
        if (! shutdown.getAndSet(true)) {
            try {
                selector.close();
            } catch (Throwable t) {
                log.trace(t, "Failed to close selector");
            }
            final Thread thread = this.thread;
            if (thread != null && thread != Thread.currentThread()) try {
                thread.interrupt();
            } catch (Throwable t) {
                log.trace(t, "Failed to interrupt selector thread");
            }
        }
    }

    public void run() {
        final Selector selector = this.selector;
        final Queue<SelectorTask> queue = selectorWorkQueue;
        for (; ;) {
            try {
                if (shutdown.get()) {
                    return;
                }
                keyLoad = selector.keys().size();
                selector.select();
                synchronized (queue) {
                    while (! queue.isEmpty()) {
                        final SelectorTask task = queue.remove();
                        try {
                            task.run(selector);
                        } catch (Throwable t) {
                            log.trace(t, "NIO selector task failed");
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
                            log.trace(t, "Failed to execute handler");
                        }
                    } catch (CancelledKeyException e) {
                        log.trace("Key %s cancelled", key);
                        continue;
                    }
                }
            } catch (ClosedSelectorException e) {
                // THIS is the "official" signalling mechanism
                log.trace("Selector %s closed", selector);
                return;
            } catch (IOException e) {
                log.trace(e, "I/O error in selector loop");
            }
        }
    }
}
