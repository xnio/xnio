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
import org.jboss.logging.Logger;
import org.xnio.AbstractChannelThread;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;

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
    protected final Thread thread;
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
                                Log.log.tracef("Shutting down channel thread \"%s\"", AbstractNioChannelThread.this);
                                return;
                            }
                        }
                    }
                    // perform select
                    try {
                        if (delayTime == Long.MAX_VALUE) {
                            Log.log.tracef("Beginning select on %s", selector);
                            selector.select();
                        } else {
                            final long millis = 1L + delayTime / 1000000L;
                            Log.log.tracef("Beginning select on %s (with timeout)", selector);
                            selector.select(millis);
                        }
                    } catch (IOException e) {
                        log.warnf("Received an I/O error on selection: %s", e);
                        // hopefully transient; should never happen
                    }
                    Log.log.tracef("Selected on %s", selector);
                    // iterate the ready key set
                    selectedKeys = selector.selectedKeys();
                    // copy so that handlers can safely cancel keys
                    keyIterator = new ArrayList<SelectionKey>(selectedKeys).iterator();
                    while (keyIterator.hasNext()) {
                        final SelectionKey key = keyIterator.next();
                        Log.log.tracef("Selected key %s", key);
                        ((NioHandle<?>) key.attachment()).invoke();
                        selectedKeys.remove(key);
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

    private static final Thread.UncaughtExceptionHandler HANDLER = new Thread.UncaughtExceptionHandler() {
        public void uncaughtException(final Thread t, final Throwable e) {
            log.errorf(e, "Thread named \"%s\" threw an uncaught exception", t);
        }
    };

    protected AbstractNioChannelThread(final ThreadGroup threadGroup, final OptionMap optionMap) throws IOException {
        final String threadName = optionMap.contains(Options.THREAD_NAME) ? optionMap.get(Options.THREAD_NAME) : generateName();
        final Thread thread;
        thread = new Thread(threadGroup, task, threadName, optionMap.get(Options.STACK_SIZE, 0L));
        thread.setDaemon(optionMap.get(Options.THREAD_DAEMON, false));
        thread.setPriority(optionMap.get(Options.THREAD_PRIORITY, Thread.NORM_PRIORITY));
        thread.setContextClassLoader(null);
        thread.setUncaughtExceptionHandler(HANDLER);
        this.thread = thread;
        selector = Selector.open();
        if (! optionMap.get(Options.ALLOW_BLOCKING, true)) {
            execute(blockingDisabler);
        }
        log.tracef("Creating channel thread '%s', selector %s", this, selector);
    }

    private static final Runnable blockingDisabler = new Runnable() {
        public void run() {
            Xnio.allowBlocking(false);
        }
    };

    protected abstract String generateName();

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
        final long deadline = System.nanoTime() + Math.min(time, LONGEST_DELAY) * 1000000L;
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
                            holder.setProblem(new IllegalStateException(String.format("Cannot add channel %s to %s (stopping)", xnioChannel, AbstractNioChannelThread.this)));
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
        if (thread == Thread.currentThread()) {
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

    public String toString() {
        return String.format("Channel thread \"%s\"", thread.getName());
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
