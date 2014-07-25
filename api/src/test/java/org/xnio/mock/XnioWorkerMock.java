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
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.LocalSocketAddress;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.MulticastMessageChannel;

/**
 * {@link XnioWorker} mock.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public class XnioWorkerMock extends XnioWorker {

    /**
     * Extra info set on created channel mocks if the channel mock was created as a tcp channel.
     * @see Mock#getInfo()
     */
    public static final String TCP_CHANNEL_INFO = "tcp";

    /**
     * Extra info set on created channel mocks if the channel mock was created as a udp channel.
     * @see Mock#getInfo()
     */
    public static final String UDP_CHANNEL_INFO = "udp";

    /**
     * Extra info set on created channel mocks if the channel mock was created as a local channel.
     * @see Mock#getInfo()
     */
    public static final String LOCAL_CHANNEL_INFO = "local";

    /**
     * The thread mock.
     */
    private final XnioIoThreadMock mockThread = new XnioIoThreadMock(this);

    // indicates which action should be taken if a request to connect is performed
    enum ConnectBehavior {SUCCEED, FAIL, CANCEL}

    // by default, every request to connectil will succeed
    private ConnectBehavior connectBehavior = ConnectBehavior.SUCCEED;

    // has this worker been shut down
    private boolean shutdown;

    public XnioWorkerMock() {
        this(Xnio.getInstance(), null, OptionMap.EMPTY, null);
    }

    protected XnioWorkerMock(ThreadGroup threadGroup, OptionMap optionMap, Runnable terminationTask) {
        super(Xnio.getInstance(), threadGroup, optionMap, terminationTask);
    }

    protected XnioWorkerMock(Xnio xnio, ThreadGroup threadGroup, OptionMap optionMap, Runnable terminationTask) {
        super(xnio, threadGroup, optionMap, terminationTask);
    }

    @Override
    public int getIoThreadCount() {
        return 0;
    }

    @Override
    public XnioIoThreadMock chooseThread() {
        return mockThread;
    }

    /**
     * Returns the connect behavior of this worker mock.
     */
    ConnectBehavior getConnectBehavior() {
        return this.connectBehavior;
    }

    /**
     * Sets this worker mock to fail every request to connect. Used for emulating failure to connect behavior.
     */
    public void failConnection() {
        connectBehavior = ConnectBehavior.FAIL;
    }

    /**
     * Sets this worker mock to behave as if every request to connect has been cancelled by a third party, whenever
     * applicable. Used for emulating cancellation of connection futures. 
     */
    public void cancelConnection() {
        connectBehavior = ConnectBehavior.CANCEL;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private AcceptingChannel<StreamConnection> internalCreateStreamServer(SocketAddress bindAddress, ChannelListener<? super AcceptingChannel<StreamConnection>> acceptListener, OptionMap optionMap, String channelInfo) throws IOException {
        AcceptingChannelMock channel = new AcceptingChannelMock();
        channel.setLocalAddress(bindAddress);
        channel.setOptionMap(optionMap);
        channel.setWorker(this);
        channel.setInfo(channelInfo);
        if (connectBehavior == ConnectBehavior.FAIL) channel.enableAcceptance(false);
        ((AcceptingChannel)channel).getAcceptSetter().set(acceptListener);
        return channel;
    }

    @Override
    protected AcceptingChannel<StreamConnection> createTcpConnectionServer(InetSocketAddress bindAddress, ChannelListener<? super AcceptingChannel<StreamConnection>> acceptListener, OptionMap optionMap) throws IOException {
        return internalCreateStreamServer(bindAddress, acceptListener, optionMap, TCP_CHANNEL_INFO);
    }

    @Override
    protected AcceptingChannel<StreamConnection> createLocalStreamConnectionServer(LocalSocketAddress bindAddress, ChannelListener<? super AcceptingChannel<StreamConnection>> acceptListener, OptionMap optionMap) throws IOException {
        return internalCreateStreamServer(bindAddress, acceptListener, optionMap, LOCAL_CHANNEL_INFO);
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
