/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates, and individual
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
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.xnio.AbstractIoFuture;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.FailedIoFuture;
import org.xnio.FinishedIoFuture;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.FramedMessageChannel;
import org.xnio.channels.MulticastMessageChannel;

/**
 * {@link XnioWorker} mock.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class XnioWorkerMock extends XnioWorker {

    /**
     * Extra info set on created channel mocks if the channel mock was created as a tcp channel.
     * @see ChannelMock#getInfo()
     */
    public static final String TCP_CHANNEL_INFO = "tcp";

    /**
     * Extra info set on created channel mocks if the channel mock was created as a udp channel.
     * @see ChannelMock#getInfo()
     */
    public static final String UDP_CHANNEL_INFO = "udp";

    /**
     * Extra info set on created channel mocks if the channel mock was created as a local channel.
     * @see ChannelMock#getInfo()
     */
    public static final String LOCAL_CHANNEL_INFO = "tcp";
    private final XnioExecutorMock mockThread = new XnioExecutorMock(this);

    private enum ConnectBehavior {SUCCEED, FAIL, CANCEL}
    private boolean shutdown;
    private ConnectBehavior connectBehavior = ConnectBehavior.SUCCEED;

    protected XnioWorkerMock(Xnio xnio, ThreadGroup threadGroup, OptionMap optionMap, Runnable terminationTask) {
        super(xnio, threadGroup, optionMap, terminationTask);
    }

    protected XnioWorkerMock(ThreadGroup threadGroup, OptionMap optionMap, Runnable terminationTask) {
        super(Xnio.getInstance(), threadGroup, optionMap, terminationTask);
    }

    public int getIoThreadCount() {
        return 0;
    }

    protected XnioIoThread chooseThread() {
        return mockThread;
    }

    protected IoFuture<ConnectedStreamChannel> internalConnectStream(final SocketAddress bindAddress, final SocketAddress destinationAddress, final ChannelListener<? super ConnectedStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap, final String channelInfo) {
        switch(connectBehavior) {
            case SUCCEED: {
                ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
                channel.setLocalAddress(bindAddress);
                channel.setPeerAddress(destinationAddress);
                ChannelListeners.invokeChannelListener(channel, bindListener);
                ChannelListeners.invokeChannelListener(channel, openListener);
                channel.setWorker(this);
                channel.setOptionMap(optionMap);
                channel.setInfo(channelInfo);
                return new FinishedIoFuture<ConnectedStreamChannel>(channel);
            }
            case FAIL:
                return new FailedIoFuture<ConnectedStreamChannel>(new IOException("dummy exception"));
            case CANCEL:
                return new AbstractIoFuture<ConnectedStreamChannel>() {
                    {
                        setCancelled();
                    }
                };
           default:
               throw new IllegalStateException("Unexpected ConnectBehavior");
        }
    }

    public void failConnection() {
        connectBehavior = ConnectBehavior.FAIL;
    }

    public void cancelConnection() {
        connectBehavior = ConnectBehavior.CANCEL;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private AcceptingChannel<? extends ConnectedStreamChannel> internalCreateStreamServer(SocketAddress bindAddress, ChannelListener<? super AcceptingChannel<ConnectedStreamChannel>> acceptListener, OptionMap optionMap, String channelInfo) throws IOException {
        AcceptingChannelMock channel = new AcceptingChannelMock();
        channel.setLocalAddress(bindAddress);
        channel.setOptionMap(optionMap);
        channel.setWorker(this);
        channel.setInfo(channelInfo);
        ((AcceptingChannel)channel).getAcceptSetter().set(acceptListener);
        return channel;
    }

    private IoFuture<ConnectedStreamChannel> internalAcceptStream(SocketAddress destination, ChannelListener<? super ConnectedStreamChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap, String channelInfo) {
        switch(connectBehavior) {
            case SUCCEED: {
                ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
                channel.setPeerAddress(destination);
                ChannelListeners.invokeChannelListener(channel, bindListener);
                ChannelListeners.invokeChannelListener(channel, openListener);
                channel.setWorker(this);
                channel.setOptionMap(optionMap);
                channel.setInfo(channelInfo);
                return new FinishedIoFuture<ConnectedStreamChannel>(channel);
            }
            case FAIL:
                return new FailedIoFuture<ConnectedStreamChannel>(new IOException("dummy exception"));
            case CANCEL:
                return new AbstractIoFuture<ConnectedStreamChannel>() {
                    {
                        setCancelled();
                    }
                };
           default:
               throw new IllegalStateException("Unexpected ConnectBehavior");
        }
    }

    private IoFuture<ConnectedMessageChannel> internalConnectDatagram(SocketAddress bindAddress, SocketAddress destination, ChannelListener<? super ConnectedMessageChannel> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap, String channelInfo) {
        switch(connectBehavior) {
            case SUCCEED: {
                final ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
                channel.setLocalAddress(bindAddress);
                channel.setPeerAddress(destination);
                channel.setWorker(this);
                channel.setOptionMap(optionMap);
                channel.setInfo(channelInfo);

                final FramedMessageChannel messageChannel = new FramedMessageChannel(channel, ByteBuffer.allocate(10), ByteBuffer.allocate(10));
                ChannelListeners.invokeChannelListener(messageChannel, bindListener);
                ChannelListeners.invokeChannelListener(messageChannel, openListener);
                return new FinishedIoFuture<ConnectedMessageChannel>(messageChannel);
            }
            case FAIL:
                return new FailedIoFuture<ConnectedMessageChannel>(new IOException("dummy exception"));
            case CANCEL:
                return new AbstractIoFuture<ConnectedMessageChannel>() {
                    {
                        setCancelled();
                    }
                };
           default:
               throw new IllegalStateException("Unexpected ConnectBehavior");
        }
    }

    @Override
    public MulticastMessageChannel createUdpServer(InetSocketAddress bindAddress, ChannelListener<? super MulticastMessageChannel> bindListener, OptionMap optionMap) throws IOException {
        MulticastMessageChannel channel = new MulticastMessageChannelMock(bindAddress, optionMap);
        ChannelListeners.invokeChannelListener(channel, bindListener);
        return channel;
    }

    @Override
    public void shutdown() {
        shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        throw new RuntimeException("not implemented");
    }

    @Override
    public boolean isShutdown() {
        throw new RuntimeException("not implemented");
    }

    @Override
    public boolean isTerminated() {
        throw new RuntimeException("not implemented");
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (shutdown) {
            return true;
        }
        return false;
    }

    @Override
    public void awaitTermination() throws InterruptedException {
    }
}
