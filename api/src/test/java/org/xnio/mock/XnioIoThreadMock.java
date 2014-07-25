/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
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

package org.xnio.mock;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.xnio.AbstractIoFuture;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.ChannelPipe;
import org.xnio.FailedIoFuture;
import org.xnio.FinishedIoFuture;
import org.xnio.IoFuture;
import org.xnio.LocalSocketAddress;
import org.xnio.MessageConnection;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoFactory;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.StreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * {@link XnioExecutor} mock.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public class XnioIoThreadMock extends XnioIoThread implements XnioExecutor {

    private Runnable command;
    private static final Runnable CLOSE_THREAD = new Runnable() {public void run() {}};

    public XnioIoThreadMock(final XnioWorker worker) {
        super(worker, 0, "XNIO IO THREAD MOCK");
    }

    @Override
    public void run() {
        while(true) {
            Runnable currentCommand;
            synchronized(this) {
                currentCommand = command;
                command = null;
            }
            if (currentCommand != null) {
                currentCommand.run();
            }
            if (currentCommand == CLOSE_THREAD) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void execute(final Runnable command) {
        assert this.isAlive(): "Before executing a commnad, the thread must be started.";
        if (Thread.currentThread() == this) {
            command.run();
            return;
        }
        final CountDownLatch latch = new CountDownLatch(1);
        synchronized (this) {
            while (this.command != null) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
            assert this.isAlive(): "Thread is closed.";
            this.command = new Runnable() {
                public void run() {
                    try {
                        command.run();
                    } finally {
                        latch.countDown();
                    }
                }
            };
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public Key executeAfter(Runnable command, long time, TimeUnit unit) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Key executeAtInterval(final Runnable command, final long time, final TimeUnit unit) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    protected IoFuture<StreamConnection> acceptLocalStreamConnection(LocalSocketAddress destination, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        return internalAcceptStream(destination, openListener, bindListener, optionMap, XnioWorkerMock.LOCAL_CHANNEL_INFO);
    }

    @Override
    protected IoFuture<StreamConnection> acceptTcpStreamConnection(InetSocketAddress destination, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        return internalAcceptStream(destination, openListener, bindListener, optionMap, XnioWorkerMock.TCP_CHANNEL_INFO);
    }

    private IoFuture<StreamConnection> internalAcceptStream(SocketAddress destination, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap, String channelInfo) {
        switch(getWorkerMock().getConnectBehavior()) {
            case SUCCEED: {
                final ConduitMock conduit = new ConduitMock(getWorker(), this);
                final StreamConnectionMock connection = new StreamConnectionMock(conduit);
                connection.setPeerAddress(destination);
                ChannelListeners.invokeChannelListener(connection, bindListener);
                connection.setOptionMap(optionMap);
                connection.setInfo(channelInfo);
                ChannelListeners.invokeChannelListener(connection, openListener);
                return new FinishedIoFuture<StreamConnection>(connection);
            }
            case FAIL:
                return new FailedIoFuture<StreamConnection>(new IOException("dummy exception"));
            case CANCEL:
                return new AbstractIoFuture<StreamConnection>() {
                    {
                        setCancelled();
                    }
                };
           default:
               throw new IllegalStateException("Unexpected ConnectBehavior");
        }
    }
    

    protected IoFuture<StreamConnection> internalOpenStreamConnection(final SocketAddress bindAddress, final SocketAddress destinationAddress, final ChannelListener<? super StreamConnection> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap, final String channelInfo) {
        switch(getWorkerMock().getConnectBehavior()) {
            case SUCCEED: {
                final ConduitMock conduit = new ConduitMock(getWorker(), this);
                final StreamConnectionMock connection = new StreamConnectionMock(conduit);
                connection.setLocalAddress(bindAddress);
                connection.setPeerAddress(destinationAddress);
                ChannelListeners.invokeChannelListener(connection, bindListener);
                connection.setOptionMap(optionMap);
                connection.setInfo(channelInfo);
                ChannelListeners.invokeChannelListener(connection, openListener);
                return new FinishedIoFuture<StreamConnection>(connection);
            }
            case FAIL:
                return new FailedIoFuture<StreamConnection>(new IOException("dummy exception"));
            case CANCEL:
                return new AbstractIoFuture<StreamConnection>() {
                    {
                        setCancelled();
                    }
                };
           default:
               throw new IllegalStateException("Unexpected ConnectBehavior");
        }
    }

    @Override
    protected IoFuture<StreamConnection> openTcpStreamConnection(InetSocketAddress bindAddress, InetSocketAddress destinationAddress, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        return internalConnectStream(bindAddress, destinationAddress, openListener, bindListener, optionMap, XnioWorkerMock.TCP_CHANNEL_INFO);
    }

    @Override
    protected IoFuture<StreamConnection> openLocalStreamConnection(LocalSocketAddress bindAddress, LocalSocketAddress destinationAddress, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        return internalConnectStream(bindAddress, destinationAddress, openListener, bindListener, optionMap, XnioWorkerMock.LOCAL_CHANNEL_INFO);
    }

    protected IoFuture<StreamConnection> internalConnectStream(final SocketAddress bindAddress, final SocketAddress destinationAddress, final ChannelListener<? super StreamConnection> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap, final String channelInfo) {
        switch(getWorkerMock().getConnectBehavior()) {
            case SUCCEED: {
                ConduitMock conduit = new ConduitMock(getWorkerMock(), this);
                StreamConnectionMock connection = new StreamConnectionMock(conduit);
                connection.setLocalAddress(bindAddress);
                connection.setPeerAddress(destinationAddress);
                ChannelListeners.invokeChannelListener(connection, bindListener);
                conduit.setWorker(getWorker());
                connection.setOptionMap(optionMap);
                connection.setInfo(channelInfo);
                ChannelListeners.invokeChannelListener(connection, openListener);
                return new FinishedIoFuture<StreamConnection>(connection);
            }
            case FAIL:
                return new FailedIoFuture<StreamConnection>(new IOException("dummy exception"));
            case CANCEL:
                return new AbstractIoFuture<StreamConnection>() {
                    {
                        setCancelled();
                    }
                };
           default:
               throw new IllegalStateException("Unexpected ConnectBehavior");
        }
    }

    @Override
    protected IoFuture<MessageConnection> openLocalMessageConnection(LocalSocketAddress bindAddress, LocalSocketAddress destinationAddress, ChannelListener<? super MessageConnection> openListener, OptionMap optionMap) {
        switch(getWorkerMock().getConnectBehavior()) {
            case SUCCEED: {
                MessageConnectionMock messageConnection = new MessageConnectionMock(this, bindAddress, destinationAddress, optionMap);
                messageConnection.setInfo(XnioWorkerMock.LOCAL_CHANNEL_INFO);
                ChannelListeners.invokeChannelListener(messageConnection, openListener);
                return new FinishedIoFuture<MessageConnection>(messageConnection);
            }
            case FAIL:
                return new FailedIoFuture<MessageConnection>(new IOException("dummy exception"));
            case CANCEL:
                return new AbstractIoFuture<MessageConnection>() {
                    {
                        setCancelled();
                    }
                };
           default:
               throw new IllegalStateException("Unexpected ConnectBehavior");
        }
    }

    protected IoFuture<MessageConnection> acceptLocalMessageConnection(LocalSocketAddress destination, ChannelListener<? super MessageConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        return openLocalMessageConnection(null, destination, openListener, optionMap);
    }

    @Override
    public ChannelPipe<StreamChannel, StreamChannel> createFullDuplexPipe() throws IOException {
        throw new RuntimeException("method not implemented by mock");
    }

    @Override
    public ChannelPipe<StreamConnection, StreamConnection> createFullDuplexPipeConnection(final XnioIoFactory peer) throws IOException {
        throw new RuntimeException("method not implemented by mock");
    }

    @Override
    public ChannelPipe<StreamSourceChannel, StreamSinkChannel> createHalfDuplexPipe(final XnioIoFactory peer) throws IOException {
        throw new RuntimeException("method not implemented by mock");
    }

    private XnioWorkerMock getWorkerMock() {
        return (XnioWorkerMock) super.getWorker();
    }

    public void closeIoThread() {
        execute(CLOSE_THREAD);
    }
}
