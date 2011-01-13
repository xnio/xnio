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

package org.xnio.nio;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.jboss.logging.Logger;
import org.xnio.IoUtils;

/**
 *
 */
final class NioSelectorRunnable implements Runnable {

    private static final Logger log = Logger.getLogger("org.xnio.nio.selector");

    private final Selector selector;
    private final Queue<SelectorTask> selectorWorkQueue = new ArrayDeque<SelectorTask>();
    private volatile int shutdown = 0;
    private volatile int keyLoad;
    private volatile Thread thread;

    private static final AtomicIntegerFieldUpdater<NioSelectorRunnable> shutdownUpdater = AtomicIntegerFieldUpdater.newUpdater(NioSelectorRunnable.class, "shutdown");
    private volatile AbstractNioChannelThread channelThread;

    protected NioSelectorRunnable() throws IOException {
        selector = Selector.open();
    }

    public void runTask(SelectorTask task) {
        if (Thread.currentThread() == thread) {
            task.run(selector);
        } else {
            synchronized (selectorWorkQueue) {
                selectorWorkQueue.add(task);
            }
            selector.wakeup();
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
        if (shutdownUpdater.compareAndSet(this, 0, 1)) {
            wakeup();
        }
    }

    public void run() {
        thread = Thread.currentThread();
        final Selector selector = this.selector;
        log.tracef("NIO selector thread started (%s, %s)", thread, selector);
        final Queue<SelectorTask> queue = selectorWorkQueue;
        try {
            for (; ;) {
                try {
                    synchronized (queue) {
                        for (SelectorTask task = queue.poll(); task != null; task = queue.poll()) try {
                            task.run(selector);
                        } catch (Throwable t) {
                            log.tracef(t, "NIO selector task failed");
                        }
                        final int keyLoad = this.keyLoad = selector.keys().size();
                        if (keyLoad == 0 && shutdown != 0) {
                            return;
                        }
                    }
                    selector.select();
                    final Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    final Iterator<SelectionKey> iterator = selectedKeys.iterator();
                    while (iterator.hasNext()) {
                        final SelectionKey key = iterator.next();
                        iterator.remove();
                        try {
                            final NioHandle<?> handle = (NioHandle<?>) key.attachment();
                            if (handle.isOneShot()) {
                                key.interestOps(0);
                            }
                            handle.invoke();
                        } catch (CancelledKeyException e) {
                            log.tracef("Key %s cancelled", key);
                        }
                    }
                } catch (ClosedSelectorException e) {
                    // THIS is the "official" signalling mechanism
                    log.tracef("Selector %s closed", selector);
                    return;
                } catch (IOException e) {
                    log.trace("I/O error in selector loop", e);
                }
                if (Thread.interrupted()) {
                    log.trace("Selector thread interrupted");
                    // do nothing else.  Shutdown is tested at the top of the loop
                }
            }
        } finally {
            IoUtils.safeClose(selector);
            log.tracef("NIO selector thread finished (%s)", thread);
            thread = null;
            synchronized (queue) {
                shutdown = 1;
                queue.notifyAll();
            }
            channelThread.done();
        }
    }

    SelectionKey register(final AbstractSelectableChannel channel, final int ops) throws ClosedChannelException {
        if (shutdown != 0) {
            throw new ClosedChannelException();
        }
        if (Thread.currentThread() == thread) {
            final SelectionKey key = channel.register(selector, ops);
            try {
                selector.selectNow();
            } catch (IOException e) {
                log.error("Failed to notify selector of selection key registration", e);
            }
            return key;
        } else {
            final SynchronousHolder<SelectionKey> holder = new SynchronousHolder<SelectionKey>();
            runTask(new SelectorTask() {
                public void run(final Selector selector) {
                    try {
                        if (shutdown != 0) {
                            throw new ClosedChannelException();
                        }
                        final SelectionKey key = channel.register(selector, ops);
                        try {
                            selector.selectNow();
                        } catch (IOException e) {
                            log.error("Failed to notify selector of selection key registration", e);
                        }
                        holder.set(key);
                    } catch (IllegalBlockingModeException e) {
                        holder.setProblem(e);
                    } catch (ClosedChannelException e) {
                        holder.setProblem(e);
                    }
                }
            });
            return holder.get();
        }
    }

    void setChannelThread(final AbstractNioChannelThread channelThread) {
        this.channelThread = channelThread;
    }
}
