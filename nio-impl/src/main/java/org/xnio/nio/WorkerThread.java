/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xnio.nio;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.security.AccessController;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.jboss.logging.Logger;
import org.xnio.ReadPropertyAction;
import org.xnio.XnioExecutor;

import static java.lang.System.identityHashCode;
import static java.lang.System.nanoTime;
import static java.util.concurrent.locks.LockSupport.park;
import static java.util.concurrent.locks.LockSupport.unpark;
import static org.xnio.IoUtils.safeClose;
import static org.xnio.nio.Log.log;
import static org.xnio.nio.Log.selectorLog;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class WorkerThread extends Thread implements XnioExecutor {
    private static final long LONGEST_DELAY = 9223372036853L;
    private static final String FQCN = WorkerThread.class.getName();
    private static final boolean OLD_LOCKING;
    private static final boolean THREAD_SAFE_SELECTION_KEYS;

    private final NioXnioWorker worker;

    private final Selector selector;
    private final Object workLock = new Object();

    private final boolean writeThread;
    private final int number;
    private final Queue<Runnable> selectorWorkQueue = new ArrayDeque<Runnable>();
    private final Set<TimeKey> delayWorkQueue = new TreeSet<TimeKey>();
    private final IdentityHashMap<Object, Object> tls = new IdentityHashMap<Object, Object>();

    private volatile int state;

    private static final int SHUTDOWN = (1 << 31);

    private static final AtomicIntegerFieldUpdater<WorkerThread> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(WorkerThread.class, "state");

    static {
        OLD_LOCKING = Boolean.parseBoolean(AccessController.doPrivileged(new ReadPropertyAction("xnio.nio.old-locking", "false")));
        THREAD_SAFE_SELECTION_KEYS = Boolean.parseBoolean(AccessController.doPrivileged(new ReadPropertyAction("xnio.xnio.thread-safe-selection-keys", "false")));
    }

    WorkerThread(final NioXnioWorker worker, final Selector selector, final String name, final ThreadGroup group, final long stackSize, final boolean writeThread, final int number) {
        super(group, null, name, stackSize);
        this.selector = selector;
        this.worker = worker;
        this.writeThread = writeThread;
        this.number = number;
    }

    static WorkerThread getCurrent() {
        final Thread thread = currentThread();
        return thread instanceof WorkerThread ? (WorkerThread) thread : null;
    }

    boolean isWriteThread() {
        return writeThread;
    }

    int getNumber() {
        return number;
    }

    public void run() {
        final Selector selector = this.selector;
        try {
            log.tracef("Starting worker thread %s", this);
            final Object lock = workLock;
            final Queue<Runnable> workQueue = selectorWorkQueue;
            final Set<TimeKey> delayQueue = delayWorkQueue;
            log.debugf("Started channel thread '%s', selector %s", currentThread().getName(), selector);
            Runnable task;
            Iterator<TimeKey> iterator;
            long delayTime = Long.MAX_VALUE;
            Set<SelectionKey> selectedKeys;
            SelectionKey[] keys = new SelectionKey[16];
            int oldState;
            int keyCount;
            for (;;) {
                // Run all tasks
                do {
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
                                        workQueue.add(key.command);
                                        iterator.remove();
                                    } else {
                                        delayTime = key.deadline - now;
                                        // the rest are in the future
                                        break;
                                    }
                                } while (iterator.hasNext());
                            }
                            task = workQueue.poll();
                        }
                    }
                    safeRun(task);
                } while (task != null);
                // all tasks have been run
                oldState = state;
                if ((oldState & SHUTDOWN) != 0) {
                    synchronized (lock) {
                        keyCount = selector.keys().size();
                        state = keyCount | SHUTDOWN;
                        if (keyCount == 0 && workQueue.isEmpty()) {
                            // no keys or tasks left, shut down (delay tasks are discarded)
                            return;
                        }
                    }
                    synchronized (selector) {
                        final Set<SelectionKey> keySet = selector.keys();
                        synchronized (keySet) {
                            keys = keySet.toArray(keys);
                        }
                    }
                    // shut em down
                    for (SelectionKey key : keys) {
                        if (key == null) break; //end of list
                        final AbstractNioConduit<?> attachment = (AbstractNioConduit<?>) key.attachment();
                        if (attachment != null) {
                            safeClose(key.channel());
                            attachment.forceTermination();
                        }
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
                } catch (CancelledKeyException ignored) {
                    // Mac and other buggy implementations sometimes spits these out
                    selectorLog.trace("Spurious cancelled key exception");
                } catch (IOException e) {
                    selectorLog.warnf("Received an I/O error on selection: %s", e);
                    // hopefully transient; should never happen
                }
                selectorLog.tracef("Selected on %s", selector);
                // iterate the ready key set
                synchronized (selector) {
                    selectedKeys = selector.selectedKeys();
                    synchronized (selectedKeys) {
                        // copy so that handlers can safely cancel keys
                        keys = selectedKeys.toArray(keys);
                        selectedKeys.clear();
                    }
                }
                for (SelectionKey key : keys) {
                    if (key == null) break; //end of list
                    final int ops;
                    try {
                        ops = key.interestOps();
                        if (ops != 0) {
                            selectorLog.tracef("Selected key %s for %s", key, key.channel());
                            final AbstractNioConduit<?> conduit = (AbstractNioConduit<?>) key.attachment();
                            if (conduit == null) {
                                cancelKey(key);
                            } else {
                                conduit.run();
                            }
                        }
                    } catch (CancelledKeyException ignored) {
                        selectorLog.tracef("Skipping selection of cancelled key %s", key);
                    } catch (Throwable t) {
                        selectorLog.tracef(t, "Unexpected failure of selection of key %s", key);
                    }
                }
                // all selected keys invoked; loop back to run tasks
            }
        } finally {
            log.tracef("Shutting down channel thread \"%s\"", this);
            safeClose(selector);
            worker.closeResource();
        }
    }

    NioXnioWorker getWorker() {
        return worker;
    }

    private static void safeRun(final Runnable command) {
        if (command != null) try {
            log.tracef("Running task %s", command);
            command.run();
        } catch (Throwable t) {
            log.error("Task failed on channel thread", t);
        }
    }

    public void execute(final Runnable command) {
        if ((state & SHUTDOWN) != 0) {
            throw new RejectedExecutionException("Thread is terminating");
        }
        synchronized (workLock) {
            selectorWorkQueue.add(command);
        }
        selector.wakeup();
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

    public Key executeAfter(final Runnable command, final long time, final TimeUnit unit) {
        return executeAfter(command, unit.toMillis(time));
    }

    Key executeAfter(final Runnable command, final long time) {
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

    SelectionKey registerChannel(final AbstractSelectableChannel channel) throws ClosedChannelException {
        if (currentThread() == this) {
            return channel.register(selector, 0);
        } else if (THREAD_SAFE_SELECTION_KEYS) {
            try {
                return channel.register(selector, 0);
            } finally {
                selector.wakeup();
            }
        } else {
            final SynchTask task = new SynchTask();
            queueTask(task);
            try {
                // Prevent selector from sleeping until we're done!
                selector.wakeup();
                return channel.register(selector, 0);
            } finally {
                task.done();
            }
        }
    }

    void queueTask(final Runnable task) {
        synchronized (workLock) {
            selectorWorkQueue.add(task);
        }
    }

    void cancelKey(final SelectionKey key) {
        assert key.selector() == selector;
        final SelectableChannel channel = key.channel();
        if (currentThread() == this) {
            log.logf(FQCN, Logger.Level.TRACE, null, "Cancelling key %s of %s (same thread)", key, channel);
            try {
                key.cancel();
                try {
                    selector.selectNow();
                } catch (IOException e) {
                    log.warnf("Received an I/O error on selection: %s", e);
                }
            } catch (Throwable t) {
                log.logf(FQCN, Logger.Level.TRACE, t, "Error cancelling key %s of %s (same thread)", key, channel);
            }
        } else if (OLD_LOCKING) {
            log.logf(FQCN, Logger.Level.TRACE, null, "Cancelling key %s of %s (other thread)", key, channel);
            final SynchTask task = new SynchTask();
            queueTask(task);
            try {
                // Prevent selector from sleeping until we're done!
                selector.wakeup();
                cancelKey(key);
            } finally {
                task.done();
            }
        } else {
            log.logf(FQCN, Logger.Level.TRACE, null, "Cancelling key %s of %s (other thread)", key, channel);
            try {
                key.cancel();
                selector.wakeup();
            } catch (Throwable t) {
                log.logf(FQCN, Logger.Level.TRACE, t, "Error cancelling key %s of %s (other thread)", key, channel);
            }
        }
    }

    void setOps(final SelectionKey key, final int ops) {
        if (currentThread() == this) {
            try {
                key.interestOps(ops);
            } catch (CancelledKeyException ignored) {}
        } else if (OLD_LOCKING) {
            final SynchTask task = new SynchTask();
            queueTask(task);
            try {
                // Prevent selector from sleeping until we're done!
                selector.wakeup();
                key.interestOps(ops);
            } catch (CancelledKeyException ignored) {
            } finally {
                task.done();
            }
        } else {
            try {
                key.interestOps(ops);
                selector.wakeup();
            } catch (CancelledKeyException ignored) {
            }
        }
    }

    Selector getSelector() {
        return selector;
    }

    public boolean equals(final Object obj) {
        return obj == this;
    }

    public int hashCode() {
        return identityHashCode(this);
    }

    final class TimeKey implements XnioExecutor.Key, Comparable<TimeKey> {
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

        public int compareTo(final TimeKey o) {
            return (int) Math.signum(deadline - o.deadline);
        }
    }

    final class SynchTask implements Runnable {
        volatile boolean done;

        public void run() {
            while (! done) {
                park();
            }
        }

        void done() {
            done = true;
            unpark(WorkerThread.this);
        }
    }
}
