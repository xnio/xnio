/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.wildfly.common.Assert;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.StreamConnection;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.channels.AcceptListenerSettable;
import org.xnio.channels.AcceptingChannel;

final class QueuedNioTcpServer2 extends AbstractNioChannel<QueuedNioTcpServer2> implements AcceptingChannel<StreamConnection>, AcceptListenerSettable<QueuedNioTcpServer2> {
    private final NioTcpServer realServer;
    private final List<Queue<StreamConnection>> acceptQueues;

    private final Runnable acceptTask = this::acceptTask;

    private volatile ChannelListener<? super QueuedNioTcpServer2> acceptListener;

    QueuedNioTcpServer2(final NioTcpServer realServer) {
        super(realServer.getWorker());
        this.realServer = realServer;
        final NioXnioWorker worker = realServer.getWorker();
        final int cnt = worker.getIoThreadCount();
        acceptQueues = new ArrayList<>(cnt);
        for (int i = 0; i < cnt; i ++) {
            acceptQueues.add(new LinkedBlockingQueue<>());
        }
        realServer.getCloseSetter().set(ignored -> invokeCloseHandler());
        realServer.getAcceptSetter().set(ignored -> handleReady());
    }

    public StreamConnection accept() throws IOException {
        final WorkerThread current = WorkerThread.getCurrent();
        if (current == null) {
            return null;
        }
        final Queue<StreamConnection> socketChannels = acceptQueues.get(current.getNumber());
        final StreamConnection connection = socketChannels.poll();
        if (connection == null) {
            if (! realServer.isOpen()) {
                throw new ClosedChannelException();
            }
        }
        return connection;
    }

    public ChannelListener<? super QueuedNioTcpServer2> getAcceptListener() {
        return acceptListener;
    }

    public void setAcceptListener(final ChannelListener<? super QueuedNioTcpServer2> listener) {
        this.acceptListener = listener;
    }

    public ChannelListener.Setter<QueuedNioTcpServer2> getAcceptSetter() {
        return new Setter<QueuedNioTcpServer2>(this);
    }

    public SocketAddress getLocalAddress() {
        return realServer.getLocalAddress();
    }

    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        return realServer.getLocalAddress(type);
    }

    public void suspendAccepts() {
        realServer.suspendAccepts();
    }

    public void resumeAccepts() {
        realServer.resumeAccepts();
    }

    public boolean isAcceptResumed() {
        return realServer.isAcceptResumed();
    }

    public void wakeupAccepts() {
        realServer.wakeupAccepts();
    }

    public void awaitAcceptable() {
        throw Assert.unsupported();
    }

    public void awaitAcceptable(final long time, final TimeUnit timeUnit) {
        throw Assert.unsupported();
    }

    @Deprecated
    public XnioExecutor getAcceptThread() {
        return getIoThread();
    }

    public void close() throws IOException {
        realServer.close();
    }

    public boolean isOpen() {
        return realServer.isOpen();
    }

    public boolean supportsOption(final Option<?> option) {
        return realServer.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return realServer.getOption(option);
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return realServer.setOption(option, value);
    }

    void handleReady() {
        final NioTcpServer realServer = this.realServer;
        NioSocketStreamConnection connection;
        try {
            connection = realServer.accept();
        } catch (ClosedChannelException e) {
            return;
        }
        XnioIoThread thread;
        if (connection != null) {
            int i = 0;
            final Runnable acceptTask = this.acceptTask;
            do {
                thread = connection.getIoThread();
                acceptQueues.get(thread.getNumber()).add(connection);
                thread.execute(acceptTask);
                if (++i == 128) {
                    // prevent starvation of other acceptors
                    return;
                }
                try {
                    connection = realServer.accept();
                } catch (ClosedChannelException e) {
                    return;
                }
            } while (connection != null);
        }
    }

    void acceptTask() {
        final WorkerThread current = WorkerThread.getCurrent();
        assert current != null;
        final Queue<StreamConnection> queue = acceptQueues.get(current.getNumber());
        ChannelListeners.invokeChannelListener(QueuedNioTcpServer2.this, getAcceptListener());
        if (! queue.isEmpty()) {
            current.execute(acceptTask);
        }
    }
}
