/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.xnio.ChannelListener;
import org.xnio.XnioExecutor;

import static java.lang.System.nanoTime;
import static java.lang.Thread.currentThread;
import static org.xnio.IoUtils.safeClose;
import static org.xnio.nio.Log.log;
import static org.xnio.nio.Log.selectorLog;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class WorkerThread extends Thread {
    private static final long LONGEST_DELAY = 9223372036853L;

    private final NioXnioWorker worker;

    private final Selector selector;
    private final Object workLock = new Object();

    private final Queue<SelectorTask> selectorWorkQueue = new ArrayDeque<SelectorTask>();
    private final Set<TimeKey> delayWorkQueue = new TreeSet<TimeKey>();

    private volatile int state;

    private static final int SHUTDOWN = (1 << 31);

    private static final AtomicIntegerFieldUpdater<WorkerThread> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(WorkerThread.class, "state");

    WorkerThread(final NioXnioWorker worker, final Selector selector, final String name, final ThreadGroup group, final long stackSize) {
        super(group, null, name, stackSize);
        this.selector = selector;
        this.worker = worker;
    }

    public void run() {
        final Selector selector = this.selector;
        try {
            log.tracef("Starting worker thread %s", this);
            final Object lock = workLock;
            final Queue<SelectorTask> workQueue = selectorWorkQueue;
            final Set<TimeKey> delayQueue = delayWorkQueue;
            log.debugf("Started channel thread '%s', selector %s", currentThread().getName(), selector);
            SelectorTask task;
            Runnable command;
            Queue<Runnable> runQueue = new ArrayDeque<Runnable>();
            Iterator<TimeKey> iterator;
            long delayTime;
            Set<SelectionKey> selectedKeys;
            Iterator<SelectionKey> keyIterator;
            int oldState;
            int keyCount;
            for (;;) {
                // Run all tasks
                for (;;) {
                    synchronized (lock) {
                        task = workQueue.poll();
                        if (task == null) {
                            iterator = delayQueue.iterator();
                            delayTime = Long.MAX_VALUE;
                            if (iterator.hasNext()) {
                                final long now = nanoTime();
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
                while ((command = runQueue.poll()) != null) {
                    safeRun(command);
                }
                // all tasks have been run
                oldState = state;
                if ((oldState & SHUTDOWN) != 0) {
                    synchronized (lock) {
                        keyCount = selector.keys().size();
                        state = keyCount | SHUTDOWN;
                        if (keyCount == 0 && workQueue.isEmpty() && delayQueue.isEmpty()) {
                            // no keys or tasks left, shut down
                            return;
                        }
                    }
                    // shut em down
                    for (SelectionKey key : selector.keys()) {
                        safeClose(((NioHandle<?>) key.attachment()).getChannel());
                    }
                }
                // perform select
                try {
                    if ((oldState & SHUTDOWN) != 0) {
                        selectorLog.tracef("Beginning select on %s (shutdown in progress)", selector);
                        selector.selectNow();
                    } else if (delayTime == Long.MAX_VALUE) {
                        selectorLog.tracef("Beginning select on %s", selector);
                        selector.select();
                    } else {
                        final long millis = 1L + delayTime / 1000000L;
                        selectorLog.tracef("Beginning select on %s (with timeout)", selector);
                        selector.select(millis);
                    }
                } catch (IOException e) {
                    selectorLog.warnf("Received an I/O error on selection: %s", e);
                    // hopefully transient; should never happen
                }
                selectorLog.tracef("Selected on %s", selector);
                // iterate the ready key set
                selectedKeys = selector.selectedKeys();
                // copy so that handlers can safely cancel keys
                keyIterator = new ArrayList<SelectionKey>(selectedKeys).iterator();
                while (keyIterator.hasNext()) {
                    final SelectionKey key = keyIterator.next();
                    selectorLog.tracef("Selected key %s", key);
                    ((NioHandle<?>) key.attachment()).invoke();
                    selectedKeys.remove(key);
                }
                // all selected keys invoked; loop back to run tasks
            }
        } finally {
            log.tracef("Shutting down channel thread \"%s\"", this);
            safeClose(selector);
            worker.closeResource();
        }
    }

    private static void safeRun(final Selector selector, final SelectorTask task) {
        try {
            log.tracef("Running selector task %s on %s", task, selector);
            task.run(selector);
        } catch (Throwable t) {
            log.error("Task failed on channel thread", t);
        }
    }

    private static void safeRun(final Runnable command) {
        try {
            log.tracef("Running task %s", command);
            command.run();
        } catch (Throwable t) {
            log.error("Task failed on channel thread", t);
        }
    }

    void execute(final Runnable command) {
        if ((state & SHUTDOWN) != 0) {
            throw new RejectedExecutionException("Thread is terminating");
        }
        synchronized (workLock) {
            selectorWorkQueue.add(new SelectorTask() {
                public void run(final Selector selector) {
                    safeRun(command);
                }
            });
            selector.wakeup();
        }
    }

    void shutdown() {
        int oldState;
        do {
            oldState = state;
            if ((oldState & SHUTDOWN) != 0) {
                // idempotent
                return;
            }
        } while (! stateUpdater.compareAndSet(this, oldState, oldState | SHUTDOWN));
        selector.wakeup();
    }

    XnioExecutor.Key executeAfter(final Runnable command, final long time) {
        if ((state & SHUTDOWN) != 0) {
            throw new RejectedExecutionException("Thread is terminating");
        }
        if (time <= 0) {
            execute(command);
            return XnioExecutor.Key.IMMEDIATE;
        }
        final long deadline = nanoTime() + Math.min(time, LONGEST_DELAY) * 1000000L;
        final TimeKey key = new TimeKey(deadline, command);
        synchronized (workLock) {
            final Set<TimeKey> queue = delayWorkQueue;
            queue.add(key);
            if (queue.iterator().next() == key) {
                // we're the next one up; poke the selector to update its delay time
                selector.wakeup();
            }
            return key;
        }
    }

    <C extends Channel> NioHandle<C> addChannel(final AbstractSelectableChannel channel, final C xnioChannel, final int ops, final ChannelListener.SimpleSetter<C> setter) throws ClosedChannelException {
        log.tracef("Adding channel %s to %s for XNIO channel %s", channel, this, xnioChannel);
        if (currentThread() == this) {
            final SelectionKey key = channel.register(selector, 0);
            final NioHandle<C> handle = new NioHandle<C>(key, this, setter, xnioChannel);
            key.attach(handle);
            if (ops != 0) key.interestOps(ops);
            return handle;
        } else {
            final SynchTask task = new SynchTask();
            queueTask(task);
            final SelectionKey key;
            try {
                // Prevent selector from sleeping until we're done!
                selector.wakeup();
                key = channel.register(selector, 0);
            } finally {
                task.done();
            }
            final NioHandle<C> handle = new NioHandle<C>(key, this, setter, xnioChannel);
            key.attach(handle);
            if (ops != 0) key.interestOps(ops);
            return handle;
        }
    }

    void queueTask(final SelectorTask task) {
        synchronized (workLock) {
            selectorWorkQueue.add(task);
        }
    }

    void cancelKey(final SelectionKey key) {
        if (currentThread() == this) {
            log.tracef("Cancelling key %s of %s from same thread", key, key.channel());
            key.cancel();
            try {
                selector.selectNow();
            } catch (IOException e) {
                log.warnf("Received an I/O error on selection: %s", e);
            }
        } else {
            queueTask(new SelectorTask() {
                public void run(final Selector selector) {
                    log.tracef("Cancelling key %s of %s from queue", key, key.channel());
                    key.cancel();
                    try {
                        selector.selectNow();
                    } catch (IOException e) {
                        log.warnf("Received an I/O error on selection: %s", e);
                    }
                }
            });
            log.tracef("Enqueued cancellation of key '%s'", key);
            selector.wakeup();
        }
    }

    void setOps(final SelectionKey key, final int ops) {
        if (currentThread() == this) {
            try {
                key.interestOps(ops);
            } catch (CancelledKeyException ignored) {}
        } else {
            queueTask(new SelectorTask() {
                public void run(final Selector selector) {
                    try {
                        key.interestOps(ops);
                    } catch (CancelledKeyException ignored) {}
                }
            });
            selector.wakeup();
        }
    }

    Selector getSelector() {
        return selector;
    }

    public boolean equals(final Object obj) {
        return obj == this;
    }

    public int hashCode() {
        return System.identityHashCode(this);
    }

    final class TimeKey implements XnioExecutor.Key {
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

    static final class SynchTask implements SelectorTask {
        boolean done = false;

        public void run(final Selector selector) {
            boolean intr = false;
            try {
                synchronized (this) {
                    while (! done) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            intr = true;
                        }
                    }
                }
            } finally {
                if (intr) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        synchronized void done() {
            done = true;
            notify();
        }
    }
}
