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
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import org.xnio.ChannelListener;
import org.xnio.ChannelPipe;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.StreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * {@link XnioExecutor} mock.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class XnioExecutorMock extends XnioIoThread implements XnioExecutor {

    public XnioExecutorMock(final XnioWorker worker) {
        super(worker, 0);
    }

    @Override
    public void execute(Runnable command) {
        command.run();
    }

    @Override
    public Key executeAfter(Runnable command, long time, TimeUnit unit) {
        throw new RuntimeException("Not implemented");
    }

    public IoFuture<StreamConnection> openStreamConnection(final SocketAddress destination, final ChannelListener<? super StreamConnection> openListener, final OptionMap optionMap) {
        return null;
    }

    public IoFuture<StreamConnection> openStreamConnection(final SocketAddress destination, final ChannelListener<? super StreamConnection> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        return null;
    }

    public IoFuture<StreamConnection> openStreamConnection(final SocketAddress bindAddress, final SocketAddress destination, final ChannelListener<? super StreamConnection> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        return null;
    }

    public IoFuture<StreamConnection> acceptStreamConnection(final SocketAddress destination, final ChannelListener<? super StreamConnection> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        return null;
    }

    public ChannelPipe<StreamChannel, StreamChannel> createFullDuplexPipe() throws IOException {
        return null;
    }

    public ChannelPipe<StreamConnection, StreamConnection> createFullDuplexPipeConnection() throws IOException {
        return null;
    }

    public ChannelPipe<StreamSourceChannel, StreamSinkChannel> createHalfDuplexPipe() throws IOException {
        return null;
    }
}
