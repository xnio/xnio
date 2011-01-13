/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
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
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import org.jboss.logging.Logger;
import org.xnio.AbstractChannelThread;
import org.xnio.IoUtils;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
abstract class AbstractNioChannelThread extends AbstractChannelThread {

    private static final Logger log = Logger.getLogger("org.xnio.nio.channel-thread");
    private static final long LONGEST_DELAY = 9223372036853L;

    private volatile int keyLoad;
    private volatile boolean shutdown;

    private final Selector selector;
    private final Object workLock = new Object();
    private final Queue<SelectorTask> selectorWorkQueue = new ArrayDeque<SelectorTask>();
    private final Set<TimeKey> delayWorkQueue = new TreeSet<TimeKey>();
    private final Thread thread;
    private final Runnable task = new Runnable() {
        public void run() {
            final Selector selector = AbstractNioChannelThread.this.selector;
            final Object lock = workLock;
            final Queue<SelectorTask> workQueue = selectorWorkQueue;
            final Set<TimeKey> delayQueue = delayWorkQueue;
            log.debugf("Started channel thread '%s', selector %s", Thread.currentThread().getName(), selector);
            SelectorTask task;
            Runnable command;
            Queue<Runnable> runQueue = new ArrayDeque<Runnable>();
            Iterator<TimeKey> iterator;
            long delayTime;
            Set<SelectionKey> selectedKeys;
            Iterator<SelectionKey> keyIterator;
            try {
                for (;;) {
                    // Run all tasks
                    for (;;) {
                        synchronized (lock) {
                            task = workQueue.poll();
                            if (task == null) {
                                iterator = delayQueue.iterator();
                                delayTime = Long.MAX_VALUE;
                                if (iterator.hasNext()) {
                                    final long now = System.nanoTime();
                                    do {
                                        final TimeKey key = iterator.next();
                                        if (key.deadline <= now) {
                                            runQueue.add(key.command);
                                            iterator.remove();
                                        } else {
                                            delayTime = key.deadline - now;
                                            // the rest are in the future
                                            break;
                                        }
                                    } while (iterator.hasNext());
                                }
                                break;
                            }
                        }
                        safeRun(selector, task);
                    }
                    for (;;) {
                        command = runQueue.poll();
                        if (command == null) {
                            break;
                        }
                        safeRun(command);
                    }
                    // all tasks have been run
                    if (shutdown) {
                        synchronized (lock) {
                            if ((keyLoad = selector.keys().size()) == 0 && workQueue.isEmpty() && delayQueue.isEmpty()) {
                                // no keys or tasks left, shut down
                                return;
                            }
                        }
                    }
                    // perform select
                    try {
                        if (delayTime == Long.MAX_VALUE) {
                            selector.select();
                        } else {
                            final long millis = 1L + delayTime / 1000000L;
                            selector.select(millis);
                        }
                    } catch (IOException e) {
                        log.warnf("Received an I/O error on selection: %s", e);
                        // hopefully transient; should never happen
                    }
                    // iterate the ready key set
                    selectedKeys = selector.selectedKeys();
                    keyIterator = selectedKeys.iterator();
                    while (keyIterator.hasNext()) {
                        ((NioHandle<?>) keyIterator.next().attachment()).invoke();
                        keyIterator.remove();
                    }
                    // all selected keys invoked; loop back to run tasks
                }
            } finally {
                IoUtils.safeClose(selector);
                done();
            }
        }
    };

    private static void safeRun(final Selector selector, final SelectorTask task) {
        try {
            task.run(selector);
        } catch (Throwable t) {
            log.error("Task failed on channel thread", t);
        }
    }

    private static void safeRun(final Runnable command) {
        try {
            command.run();
        } catch (Throwable t) {
            log.error("Task failed on channel thread", t);
        }
    }

    protected AbstractNioChannelThread(ThreadFactory threadFactory) throws IOException {
        thread = threadFactory.newThread(task);
        if (thread == null) {
            throw new IllegalArgumentException("Thread factory did not yield a thread");
        }
        selector = Selector.open();
    }

    protected void start() {
        thread.start();
    }

    protected void startShutdown() {
        shutdown = true;
        selector.wakeup();
    }

    public void execute(final Runnable command) {
        // todo - reject if stopped
        synchronized (workLock) {
            selectorWorkQueue.add(new SelectorTask() {
                public void run(final Selector selector) {
                    safeRun(command);
                }
            });
            selector.wakeup();
        }
    }

    public Key executeAfter(final Runnable command, final long time) {
        if (shutdown) {
            throw new RejectedExecutionException("Thread is terminating");
        }
        if (time <= 0) {
            execute(command);
            return Key.IMMEDIATE;
        }
        synchronized (workLock) {
            final long deadline;
            if (time > LONGEST_DELAY) {
                deadline = System.nanoTime() + LONGEST_DELAY;
            } else {
                deadline = System.nanoTime() + (time * 1000000L);
            }
            final TimeKey key = new TimeKey(deadline, command);
            delayWorkQueue.add(key);
            if (delayWorkQueue.iterator().next() == key) {
                // we're the next one up; poke the selector to update its delay time
                selector.wakeup();
            }
            return key;
        }
    }

    void done() {
        shutdownFinished();
    }

    public int getLoad() {
        return keyLoad;
    }

    <C extends Channel> NioHandle<C> addChannel(final AbstractSelectableChannel channel, final C xnioChannel, final int ops, final NioSetter<C> setter) throws ClosedChannelException {
        log.tracef("Adding channel %s to %s for XNIO channel %s", channel, this, xnioChannel);
        if (thread == Thread.currentThread()) {
            if (shutdown) {
                throw new IllegalStateException(String.format("Cannot add channel %s to %s (stopping)", xnioChannel, this));
            }
            final SelectionKey key = channel.register(selector, ops);
            final NioHandle<C> handle = new NioHandle<C>(key, this, setter, xnioChannel);
            key.attach(handle);
            key.interestOps(ops);
            return handle;
        } else {
            final SynchronousHolder<NioHandle<C>, ClosedChannelException> holder = new SynchronousHolder<NioHandle<C>, ClosedChannelException>(ClosedChannelException.class);
            queueTask(new SelectorTask() {
                public void run(final Selector selector) {
                    try {
                        if (shutdown) {
                            holder.setProblem(new IllegalStateException(String.format("Cannot add channel %s to %s (stopping)", xnioChannel, this)));
                            return;
                        }
                        final SelectionKey key = channel.register(selector, ops);
                        final NioHandle<C> handle = new NioHandle<C>(key, AbstractNioChannelThread.this, setter, xnioChannel);
                        key.attach(handle);
                        key.interestOps(ops);
                        holder.set(handle);
                    } catch (ClosedChannelException e) {
                        holder.setProblem(e);
                    }
                }
            });
            selector.wakeup();
            return holder.get();
        }
    }

    void queueTask(final SelectorTask task) {
        synchronized (workLock) {
            selectorWorkQueue.add(task);
        }
    }

    void cancelKey(final SelectionKey key) {
        if (thread == Thread.currentThread()) {
            key.cancel();
        } else {
            final SynchronousHolder<Void, RuntimeException> holder = new SynchronousHolder<Void, RuntimeException>(RuntimeException.class);
            queueTask(new SelectorTask() {
                public void run(final Selector selector) {
                    key.cancel();
                    holder.set(null);
                    try {
                        selector.selectNow();
                    } catch (IOException e) {
                        log.warnf("Received an I/O error on selection: %s", e);
                    }
                }
            });
            selector.wakeup();
            holder.get();
        }
    }

    void setOps(final SelectionKey key, final int ops) {
        if (thread == Thread.currentThread()) {
            key.interestOps(ops);
        } else {
            final SynchronousHolder<Void, RuntimeException> holder = new SynchronousHolder<Void, RuntimeException>(RuntimeException.class);
            queueTask(new SelectorTask() {
                public void run(final Selector selector) {
                    key.interestOps(ops);
                    holder.set(null);
                }
            });
            selector.wakeup();
            holder.get();
        }
    }

    final class TimeKey implements Key {
        private final long deadline;
        private final Runnable command;

        TimeKey(final long deadline, final Runnable command) {
            this.deadline = deadline;
            this.command = command;
        }

        public boolean remove() {
            synchronized (workLock) {
                return delayWorkQueue.remove(this);
            }
        }
    }
}
