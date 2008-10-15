/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

package org.jboss.xnio.nio;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class NioSelectorRunnable implements Runnable {

    private static final Logger log = Logger.getLogger(NioSelectorRunnable.class);

    private final Selector selector;
    private final Queue<SelectorTask> selectorWorkQueue = new ConcurrentLinkedQueue<SelectorTask>();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private volatile int keyLoad;
    volatile Thread thread;

    protected NioSelectorRunnable() throws IOException {
        selector = Selector.open();
    }

    public void queueTask(SelectorTask task) {
        if (Thread.currentThread() == thread) {
            task.run(selector);
        } else {
            selectorWorkQueue.add(task);
        }
    }

    public void wakeup() {
        if (Thread.currentThread() != thread) {
            selector.wakeup();
        }
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
        thread = Thread.currentThread();
        final Selector selector = this.selector;
        final Queue<SelectorTask> queue = selectorWorkQueue;
        for (; ;) {
            try {
                if (shutdown.get()) {
                    return;
                }
                keyLoad = selector.keys().size();
                selector.select();
                for (SelectorTask task = queue.poll(); task != null; task = queue.poll()) try {
                    task.run(selector);
                } catch (Throwable t) {
                    log.trace(t, "NIO selector task failed");
                }
                final Set<SelectionKey> selectedKeys = selector.selectedKeys();
                final Iterator<SelectionKey> iterator = selectedKeys.iterator();
                while (iterator.hasNext()) {
                    final SelectionKey key = iterator.next();
                    iterator.remove();
                    try {
                        final NioHandle handle = (NioHandle) key.attachment();
                        if (handle.isOneshot()) {
                            key.interestOps(0);
                        }
                        handle.getHandlerExecutor().execute(handle.getHandler());
                    } catch (CancelledKeyException e) {
                        log.trace("Key %s cancelled", key);
                    } catch (Throwable t) {
                        log.trace(t, "Failed to execute handler");
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
