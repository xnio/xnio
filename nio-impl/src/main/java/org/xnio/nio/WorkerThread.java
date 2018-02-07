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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.Pipe;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.security.AccessController;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.logging.Logger;
import org.xnio.Cancellable;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.ChannelPipe;
import org.xnio.ClosedWorkerException;
import org.xnio.FailedIoFuture;
import org.xnio.FinishedIoFuture;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.ReadPropertyAction;
import org.xnio.StreamConnection;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoFactory;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

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
final class WorkerThread extends XnioIoThread implements XnioExecutor {
    private static final long LONGEST_DELAY = 9223372036853L;
    private static final String FQCN = WorkerThread.class.getName();
    private static final boolean OLD_LOCKING;
    private static final boolean THREAD_SAFE_SELECTION_KEYS;
    private static final long START_TIME = System.nanoTime();

    private final Selector selector;
    private final Object workLock = new Object();

    private final Queue<Runnable> selectorWorkQueue = new ArrayDeque<Runnable>();
    private final TreeSet<TimeKey> delayWorkQueue = new TreeSet<TimeKey>();

    private volatile int state;

    private static final int SHUTDOWN = (1 << 31);

    private static final AtomicIntegerFieldUpdater<WorkerThread> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(WorkerThread.class, "state");

    static {
        OLD_LOCKING = Boolean.parseBoolean(AccessController.doPrivileged(new ReadPropertyAction("xnio.nio.old-locking", "false")));
        THREAD_SAFE_SELECTION_KEYS = Boolean.parseBoolean(AccessController.doPrivileged(new ReadPropertyAction("xnio.nio.thread-safe-selection-keys", "false")));
    }

    WorkerThread(final NioXnioWorker worker, final Selector selector, final String name, final ThreadGroup group, final long stackSize, final int number) {
        super(worker, number, group, name, stackSize);
        this.selector = selector;
    }

    static WorkerThread getCurrent() {
        final Thread thread = currentThread();
        return thread instanceof WorkerThread ? (WorkerThread) thread : null;
    }

    public NioXnioWorker getWorker() {
        return (NioXnioWorker) super.getWorker();
    }

    protected IoFuture<StreamConnection> acceptTcpStreamConnection(final InetSocketAddress destination, final ChannelListener<? super StreamConnection> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        try {
            getWorker().checkShutdown();
        } catch (ClosedWorkerException e) {
            return new FailedIoFuture<StreamConnection>(e);
        }
        final FutureResult<StreamConnection> futureResult = new FutureResult<StreamConnection>(this);
        try {
            boolean ok = false;
            final ServerSocketChannel serverChannel = ServerSocketChannel.open();
            try {
                serverChannel.configureBlocking(false);
                if (optionMap.contains(Options.RECEIVE_BUFFER)) {
                    serverChannel.socket().setReceiveBufferSize(optionMap.get(Options.RECEIVE_BUFFER, -1));
                }
                serverChannel.socket().setReuseAddress(optionMap.get(Options.REUSE_ADDRESSES, true));
                serverChannel.bind(destination);
                if (bindListener != null) ChannelListeners.invokeChannelListener(new BoundChannel() {
                    public SocketAddress getLocalAddress() {
                        return serverChannel.socket().getLocalSocketAddress();
                    }

                    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
                        final SocketAddress address = getLocalAddress();
                        return type.isInstance(address) ? type.cast(address) : null;
                    }

                    public ChannelListener.Setter<? extends BoundChannel> getCloseSetter() {
                        return new ChannelListener.SimpleSetter<BoundChannel>();
                    }

                    public XnioWorker getWorker() {
                        return WorkerThread.this.getWorker();
                    }

                    public XnioIoThread getIoThread() {
                        return WorkerThread.this;
                    }

                    public void close() throws IOException {
                        serverChannel.close();
                    }

                    public boolean isOpen() {
                        return serverChannel.isOpen();
                    }

                    public boolean supportsOption(final Option<?> option) {
                        return false;
                    }

                    public <T> T getOption(final Option<T> option) throws IOException {
                        return null;
                    }

                    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
                        return null;
                    }
                }, bindListener);
                final SelectionKey key = this.registerChannel(serverChannel);
                final NioHandle handle = new NioHandle(this, key) {
                    void handleReady(final int ops) {
                        boolean ok = false;
                        try {
                            final SocketChannel channel = serverChannel.accept();
                            if (channel == null) {
                                ok = true;
                                return;
                            } else {
                                safeClose(serverChannel);
                            }
                            try {
                                channel.configureBlocking(false);
                                if (optionMap.contains(Options.TCP_OOB_INLINE)) channel.socket().setOOBInline(optionMap.get(Options.TCP_OOB_INLINE, false));
                                if (optionMap.contains(Options.TCP_NODELAY)) channel.socket().setTcpNoDelay(optionMap.get(Options.TCP_NODELAY, false));
                                if (optionMap.contains(Options.IP_TRAFFIC_CLASS)) channel.socket().setTrafficClass(optionMap.get(Options.IP_TRAFFIC_CLASS, -1));
                                if (optionMap.contains(Options.CLOSE_ABORT)) channel.socket().setSoLinger(optionMap.get(Options.CLOSE_ABORT, false), 0);
                                if (optionMap.contains(Options.KEEP_ALIVE)) channel.socket().setKeepAlive(optionMap.get(Options.KEEP_ALIVE, false));
                                if (optionMap.contains(Options.SEND_BUFFER)) channel.socket().setSendBufferSize(optionMap.get(Options.SEND_BUFFER, -1));
                                final SelectionKey selectionKey = WorkerThread.this.registerChannel(channel);
                                final NioSocketStreamConnection connection = new NioSocketStreamConnection(WorkerThread.this, selectionKey, null);
                                if (optionMap.contains(Options.READ_TIMEOUT)) connection.setOption(Options.READ_TIMEOUT, optionMap.get(Options.READ_TIMEOUT, 0));
                                if (optionMap.contains(Options.WRITE_TIMEOUT)) connection.setOption(Options.WRITE_TIMEOUT, optionMap.get(Options.WRITE_TIMEOUT, 0));
                                if (futureResult.setResult(connection)) {
                                    ok = true;
                                    ChannelListeners.invokeChannelListener(connection, openListener);
                                }
                            } finally {
                                if (! ok) safeClose(channel);
                            }
                        } catch (IOException e) {
                            futureResult.setException(e);
                        } finally {
                            if (! ok) {
                                safeClose(serverChannel);
                            }
                        }
                    }

                    void terminated() {
                    }

                    void forceTermination() {
                        futureResult.setCancelled();
                    }
                };
                key.attach(handle);
                handle.resume(SelectionKey.OP_ACCEPT);
                ok = true;
                futureResult.addCancelHandler(new Cancellable() {
                    public Cancellable cancel() {
                        if (futureResult.setCancelled()) {
                            safeClose(serverChannel);
                        }
                        return this;
                    }
                });
                return futureResult.getIoFuture();
            } finally {
                if (! ok) safeClose(serverChannel);
            }
        } catch (IOException e) {
            return new FailedIoFuture<StreamConnection>(e);
        }
    }

    protected IoFuture<StreamConnection> openTcpStreamConnection(final InetSocketAddress bindAddress, final InetSocketAddress destinationAddress, final ChannelListener<? super StreamConnection> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        try {
            getWorker().checkShutdown();
        } catch (ClosedWorkerException e) {
            return new FailedIoFuture<StreamConnection>(e);
        }
        try {
            final SocketChannel channel = SocketChannel.open();
            boolean ok = false;
            try {
                channel.configureBlocking(false);
                if (optionMap.contains(Options.TCP_OOB_INLINE)) channel.socket().setOOBInline(optionMap.get(Options.TCP_OOB_INLINE, false));
                if (optionMap.contains(Options.TCP_NODELAY)) channel.socket().setTcpNoDelay(optionMap.get(Options.TCP_NODELAY, false));
                if (optionMap.contains(Options.IP_TRAFFIC_CLASS)) channel.socket().setTrafficClass(optionMap.get(Options.IP_TRAFFIC_CLASS, -1));
                if (optionMap.contains(Options.CLOSE_ABORT)) channel.socket().setSoLinger(optionMap.get(Options.CLOSE_ABORT, false), 0);
                if (optionMap.contains(Options.KEEP_ALIVE)) channel.socket().setKeepAlive(optionMap.get(Options.KEEP_ALIVE, false));
                if (optionMap.contains(Options.RECEIVE_BUFFER)) channel.socket().setReceiveBufferSize(optionMap.get(Options.RECEIVE_BUFFER, -1));
                if (optionMap.contains(Options.REUSE_ADDRESSES)) channel.socket().setReuseAddress(optionMap.get(Options.REUSE_ADDRESSES, false));
                if (optionMap.contains(Options.SEND_BUFFER)) channel.socket().setSendBufferSize(optionMap.get(Options.SEND_BUFFER, -1));
                final SelectionKey key = registerChannel(channel);
                final NioSocketStreamConnection connection = new NioSocketStreamConnection(this, key, null);
                if (optionMap.contains(Options.READ_TIMEOUT)) connection.setOption(Options.READ_TIMEOUT, optionMap.get(Options.READ_TIMEOUT, 0));
                if (optionMap.contains(Options.WRITE_TIMEOUT)) connection.setOption(Options.WRITE_TIMEOUT, optionMap.get(Options.WRITE_TIMEOUT, 0));
                if (bindAddress != null || bindListener != null) {
                    channel.socket().bind(bindAddress);
                    ChannelListeners.invokeChannelListener(connection, bindListener);
                }
                if (channel.connect(destinationAddress)) {
                    selectorLog.tracef("Synchronous connect");
                    execute(ChannelListeners.getChannelListenerTask(connection, openListener));
                    final FinishedIoFuture<StreamConnection> finishedIoFuture = new FinishedIoFuture<StreamConnection>(connection);
                    ok = true;
                    return finishedIoFuture;
                }
                selectorLog.tracef("Asynchronous connect");
                final FutureResult<StreamConnection> futureResult = new FutureResult<StreamConnection>(this);
                final ConnectHandle connectHandle = new ConnectHandle(this, key, futureResult, connection, openListener);
                key.attach(connectHandle);
                futureResult.addCancelHandler(new Cancellable() {
                    public Cancellable cancel() {
                        if (futureResult.setCancelled()) {
                            safeClose(connection);
                        }
                        return this;
                    }
                });
                connectHandle.resume(SelectionKey.OP_CONNECT);
                ok = true;
                return futureResult.getIoFuture();
            } finally {
                if (! ok) safeClose(channel);
            }
        } catch (IOException e) {
            return new FailedIoFuture<StreamConnection>(e);
        }
    }

    WorkerThread getNextThread() {
        final WorkerThread[] all = getWorker().getAll();
        final int number = getNumber();
        if (number == all.length - 1) {
            return all[0];
        } else {
            return all[number + 1];
        }
    }

    static final class ConnectHandle extends NioHandle {

        private final FutureResult<StreamConnection> futureResult;
        private final NioSocketStreamConnection connection;
        private final ChannelListener<? super StreamConnection> openListener;

        ConnectHandle(final WorkerThread workerThread, final SelectionKey selectionKey, final FutureResult<StreamConnection> futureResult, final NioSocketStreamConnection connection, final ChannelListener<? super StreamConnection> openListener) {
            super(workerThread, selectionKey);
            this.futureResult = futureResult;
            this.connection = connection;
            this.openListener = openListener;
        }

        void handleReady(final int ops) {
            final SocketChannel channel = getChannel();
            boolean ok = false;
            try {
                if (channel.finishConnect()) {
                    selectorLog.tracef("handleReady connect finished");
                    suspend(SelectionKey.OP_CONNECT);
                    getSelectionKey().attach(connection.getConduit());
                    if (futureResult.setResult(connection)) {
                        ok = true;
                        ChannelListeners.invokeChannelListener(connection, openListener);
                    }
                }
            } catch (IOException e) {
                selectorLog.tracef("ConnectHandle.handleReady Exception, " + e);
                futureResult.setException(e);
            } finally {
                if (!ok) {
                    selectorLog.tracef("!OK, closing connection");
                    safeClose(connection);
                }
            }
        }

        private SocketChannel getChannel() {
            return (SocketChannel) getSelectionKey().channel();
        }

        void forceTermination() {
            futureResult.setCancelled();
            safeClose(getChannel());
        }

        void terminated() {
        }
    }

    private static WorkerThread getPeerThread(final XnioIoFactory peer) throws ClosedWorkerException {
        final WorkerThread peerThread;
        if (peer instanceof NioXnioWorker) {
            final NioXnioWorker peerWorker = (NioXnioWorker) peer;
            peerWorker.checkShutdown();
            peerThread = peerWorker.chooseThread();
        } else if (peer instanceof WorkerThread) {
            peerThread = (WorkerThread) peer;
            peerThread.getWorker().checkShutdown();
        } else {
            throw log.notNioProvider();
        }
        return peerThread;
    }

    public ChannelPipe<StreamConnection, StreamConnection> createFullDuplexPipeConnection(XnioIoFactory peer) throws IOException {
        getWorker().checkShutdown();
        boolean ok = false;
        final Pipe topPipe = Pipe.open();
        try {
            topPipe.source().configureBlocking(false);
            topPipe.sink().configureBlocking(false);
            final Pipe bottomPipe = Pipe.open();
            try {
                bottomPipe.source().configureBlocking(false);
                bottomPipe.sink().configureBlocking(false);
                final WorkerThread peerThread = getPeerThread(peer);
                final SelectionKey topSourceKey = registerChannel(topPipe.source());
                final SelectionKey topSinkKey = peerThread.registerChannel(topPipe.sink());
                final SelectionKey bottomSourceKey = peerThread.registerChannel(bottomPipe.source());
                final SelectionKey bottomSinkKey = registerChannel(bottomPipe.sink());
                final NioPipeStreamConnection leftConnection = new NioPipeStreamConnection(this, bottomSourceKey, topSinkKey);
                final NioPipeStreamConnection rightConnection = new NioPipeStreamConnection(this, topSourceKey, bottomSinkKey);
                final ChannelPipe<StreamConnection, StreamConnection> result = new ChannelPipe<StreamConnection, StreamConnection>(leftConnection, rightConnection);
                ok = true;
                return result;
            } finally {
                if (! ok) {
                    safeClose(bottomPipe.sink());
                    safeClose(bottomPipe.source());
                }
            }
        } finally {
            if (! ok) {
                safeClose(topPipe.sink());
                safeClose(topPipe.source());
            }
        }
    }

    public ChannelPipe<StreamSourceChannel, StreamSinkChannel> createHalfDuplexPipe(final XnioIoFactory peer) throws IOException {
        getWorker().checkShutdown();
        final Pipe pipe = Pipe.open();
        boolean ok = false;
        try {
            pipe.source().configureBlocking(false);
            pipe.sink().configureBlocking(false);
            final WorkerThread peerThread = getPeerThread(peer);
            final SelectionKey readKey = registerChannel(pipe.source());
            final SelectionKey writeKey = peerThread.registerChannel(pipe.sink());
            final NioPipeStreamConnection leftConnection = new NioPipeStreamConnection(this, readKey, null);
            final NioPipeStreamConnection rightConnection = new NioPipeStreamConnection(this, null, writeKey);
            leftConnection.writeClosed();
            rightConnection.readClosed();
            final ChannelPipe<StreamSourceChannel,StreamSinkChannel> result = new ChannelPipe<StreamSourceChannel, StreamSinkChannel>(leftConnection.getSourceChannel(), rightConnection.getSinkChannel());
            ok = true;
            return result;
        } finally {
            if (! ok) {
                safeClose(pipe.sink());
                safeClose(pipe.source());
            }
        }
    }

    volatile boolean polling;

    public void run() {
        final Selector selector = this.selector;
        try {
            log.tracef("Starting worker thread %s", this);
            final Object lock = workLock;
            final Queue<Runnable> workQueue = selectorWorkQueue;
            final TreeSet<TimeKey> delayQueue = delayWorkQueue;
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
                                    if (key.deadline <= (now - START_TIME)) {
                                        workQueue.add(key.command);
                                        iterator.remove();
                                    } else {
                                        delayTime = key.deadline - (now - START_TIME);
                                        // the rest are in the future
                                        break;
                                    }
                                } while (iterator.hasNext());
                            }
                            task = workQueue.poll();
                        }
                    }
                    // clear interrupt status
                    Thread.interrupted();
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
                            Arrays.fill(keys, keySet.size(), keys.length, null);
                        }
                    }
                    // shut em down
                    for (int i = 0; i < keys.length; i++) {
                        final SelectionKey key = keys[i];
                        if (key == null) break; //end of list
                        keys[i] = null;
                        final NioHandle attachment = (NioHandle) key.attachment();
                        if (attachment != null) {
                            safeClose(key.channel());
                            attachment.forceTermination();
                        }
                    }
                    Arrays.fill(keys, 0, keys.length, null);
                }
                // clear interrupt status
                Thread.interrupted();
                // perform select
                try {
                    if ((oldState & SHUTDOWN) != 0) {
                        selectorLog.tracef("Beginning select on %s (shutdown in progress)", selector);
                        selector.selectNow();
                    } else if (delayTime == Long.MAX_VALUE) {
                        selectorLog.tracef("Beginning select on %s", selector);
                        polling = true;
                        try {
                            Runnable item = null;
                            synchronized (lock) {
                               item =  workQueue.peek();
                            }
                            if (item != null) {
                                log.tracef("SelectNow, queue is not empty");
                                selector.selectNow();
                            } else {
                                log.tracef("Select, queue is empty");
                                selector.select();
                            }
                        } finally {
                            polling = false;
                        }
                    } else {
                        final long millis = 1L + delayTime / 1000000L;
                        selectorLog.tracef("Beginning select on %s (with timeout)", selector);
                        polling = true;
                        try {
                            Runnable item = null;
                            synchronized (lock) {
                               item =  workQueue.peek();
                            }
                            if (item != null) {
                                log.tracef("SelectNow, queue is not empty");
                                selector.selectNow();
                            } else {
                                log.tracef("Select, queue is empty");
                                selector.select(millis);
                            }
                        } finally {
                            polling = false;
                        }
                    }
                } catch (CancelledKeyException ignored) {
                    // Mac and other buggy implementations sometimes spits these out
                    selectorLog.trace("Spurious cancelled key exception");
                } catch (IOException e) {
                    selectorLog.selectionError(e);
                    // hopefully transient; should never happen
                }
                selectorLog.tracef("Selected on %s", selector);
                // iterate the ready key set
                synchronized (selector) {
                    selectedKeys = selector.selectedKeys();
                    synchronized (selectedKeys) {
                        // copy so that handlers can safely cancel keys
                        keys = selectedKeys.toArray(keys);
                        Arrays.fill(keys, selectedKeys.size(), keys.length, null);
                        selectedKeys.clear();
                    }
                }
                for (int i = 0; i < keys.length; i++) {
                    final SelectionKey key = keys[i];
                    if (key == null) break; //end of list
                    keys[i] = null;
                    final int ops;
                    try {
                        ops = key.interestOps();
                        if (ops != 0) {
                            selectorLog.tracef("Selected key %s for %s", key, key.channel());
                            final NioHandle handle = (NioHandle) key.attachment();
                            if (handle == null) {
                                cancelKey(key, false);
                            } else {
                                // clear interrupt status
                                Thread.interrupted();
                                selectorLog.tracef("Calling handleReady key %s for %s", key.readyOps(), key.channel());
                                handle.handleReady(key.readyOps());
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
            getWorker().closeResource();
        }
    }

    private static void safeRun(final Runnable command) {
        if (command != null) try {
            log.tracef("Running task %s", command);
            command.run();
        } catch (Throwable t) {
            log.taskFailed(command, t);
        }
    }

    public void execute(final Runnable command) {
        if ((state & SHUTDOWN) != 0) {
            throw log.threadExiting();
        }
        synchronized (workLock) {
            selectorWorkQueue.add(command);
            log.tracef("Added task " + command);
        }
        if (polling) { // flag is always false if we're the same thread
            selector.wakeup();
        } else {
            log.tracef("Not polling, no wakeup");
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
        if(currentThread() != this) {
            selector.wakeup();
        }
    }

    public Key executeAfter(final Runnable command, final long time, final TimeUnit unit) {
        final long millis = unit.toMillis(time);
        if ((state & SHUTDOWN) != 0) {
            throw log.threadExiting();
        }
        if (millis <= 0) {
            execute(command);
            return Key.IMMEDIATE;
        }
        final long deadline = (nanoTime() - START_TIME) + Math.min(millis, LONGEST_DELAY) * 1000000L;
        final TimeKey key = new TimeKey(deadline, command);
        synchronized (workLock) {
            final TreeSet<TimeKey> queue = delayWorkQueue;
            queue.add(key);
            if (queue.iterator().next() == key) {
                // we're the next one up; poke the selector to update its delay time
                if (polling) { // flag is always false if we're the same thread
                    selector.wakeup();
                }
            }
            return key;
        }
    }

    class RepeatKey implements Key, Runnable {
        private final Runnable command;
        private final long millis;
        private final AtomicReference<Key> current = new AtomicReference<>();

        RepeatKey(final Runnable command, final long millis) {
            this.command = command;
            this.millis = millis;
        }

        public boolean remove() {
            final Key removed = current.getAndSet(this);
            // removed key should not be null because remove cannot be called before it is populated.
            assert removed != null;
            return removed != this && removed.remove();
        }

        void setFirst(Key key) {
            current.compareAndSet(null, key);
        }

        public void run() {
            try {
                command.run();
            } finally {
                Key o, n;
                o = current.get();
                if (o != this) {
                    n = executeAfter(this, millis, TimeUnit.MILLISECONDS);
                    if (!current.compareAndSet(o, n)) {
                        n.remove();
                    }
                }
            }
        }
    }

    public Key executeAtInterval(final Runnable command, final long time, final TimeUnit unit) {
        final long millis = unit.toMillis(time);
        final RepeatKey repeatKey = new RepeatKey(command, millis);
        final Key firstKey = executeAfter(repeatKey, millis, TimeUnit.MILLISECONDS);
        repeatKey.setFirst(firstKey);
        return repeatKey;
    }

    SelectionKey registerChannel(final AbstractSelectableChannel channel) throws ClosedChannelException {
        if (currentThread() == this) {
            return channel.register(selector, 0);
        } else if (THREAD_SAFE_SELECTION_KEYS) {
            try {
                return channel.register(selector, 0);
            } finally {
                if (polling) selector.wakeup();
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

    void cancelKey(final SelectionKey key, final boolean block) {
        assert key.selector() == selector;
        final SelectableChannel channel = key.channel();
        if (currentThread() == this) {
            log.logf(FQCN, Logger.Level.TRACE, null, "Cancelling key %s of %s (same thread)", key, channel);
            try {
                key.cancel();
                try {
                    selector.selectNow();
                } catch (IOException e) {
                    log.selectionError(e);
                }
            } catch (Throwable t) {
                log.logf(FQCN, Logger.Level.TRACE, t, "Error cancelling key %s of %s (same thread)", key, channel);
            }
        } else if (OLD_LOCKING) {
            log.logf(FQCN, Logger.Level.TRACE, null, "Cancelling key %s of %s (same thread, old locking)", key, channel);
            final SynchTask task = new SynchTask();
            queueTask(task);
            try {
                // Prevent selector from sleeping until we're done!
                selector.wakeup();
                key.cancel();
            } catch (Throwable t) {
                log.logf(FQCN, Logger.Level.TRACE, t, "Error cancelling key %s of %s (same thread, old locking)", key, channel);
            } finally {
                task.done();
            }
        } else {
            log.logf(FQCN, Logger.Level.TRACE, null, "Cancelling key %s of %s (other thread)", key, channel);
            try {
                key.cancel();
                if (block) {
                    final SelectNowTask task = new SelectNowTask();
                    queueTask(task);
                    selector.wakeup();
                    // block until the selector is actually deregistered
                    task.doWait();
                } else {
                    selector.wakeup();
                }
            } catch (Throwable t) {
                log.logf(FQCN, Logger.Level.TRACE, t, "Error cancelling key %s of %s (other thread)", key, channel);
            }
        }
    }

    void setOps(final SelectionKey key, final int ops) {
        if (currentThread() == this) {
            try {
                synchronized(key) {
                    key.interestOps(key.interestOps() | ops);
                }
            } catch (CancelledKeyException ignored) {}
        } else if (OLD_LOCKING) {
            final SynchTask task = new SynchTask();
            queueTask(task);
            try {
                // Prevent selector from sleeping until we're done!
                selector.wakeup();
                synchronized(key) {
                    key.interestOps(key.interestOps() | ops);
                }
            } catch (CancelledKeyException ignored) {
            } finally {
                task.done();
            }
        } else {
            try {
                synchronized(key) {
                    key.interestOps(key.interestOps() | ops);
                }
                if (polling) selector.wakeup();
            } catch (CancelledKeyException ignored) {
            }
        }
    }

    void clearOps(final SelectionKey key, final int ops) {
        if (currentThread() == this || ! OLD_LOCKING) {
            try {
                synchronized(key) {
                    key.interestOps(key.interestOps() & ~ops);
                }
            } catch (CancelledKeyException ignored) {}
        } else {
            final SynchTask task = new SynchTask();
            queueTask(task);
            try {
                // Prevent selector from sleeping until we're done!
                selector.wakeup();
                synchronized(key) {
                    key.interestOps(key.interestOps() & ~ops);
                }
            } catch (CancelledKeyException ignored) {
            } finally {
                task.done();
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

    static final AtomicLong seqGen = new AtomicLong();

    final class TimeKey implements XnioExecutor.Key, Comparable<TimeKey> {
        private final long deadline;
        private final long seq = seqGen.incrementAndGet();
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
            int r = Long.signum(deadline - o.deadline);
            if (r == 0) r = Long.signum(seq - o.seq);
            return r;
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

    final class SelectNowTask implements Runnable {
        final Thread thread = Thread.currentThread();
        volatile boolean done;

        void doWait() {
            while (! done) {
                park();
            }
        }

        public void run() {
            try {
                selector.selectNow();
            } catch (IOException ignored) {
            }
            done = true;
            unpark(thread);
        }
    }
}
