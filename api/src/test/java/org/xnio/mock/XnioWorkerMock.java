/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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
package org.xnio.mock;

import java.io.IOException;
import java.net.InetSocketAddress;
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
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.ssl.mock.AcceptingChannelMock;

/**
 * {@link XnioWorker} mock.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class XnioWorkerMock extends XnioWorker {

    private enum ConnectBehavior {SUCCEED, FAIL, CANCEL}
    private boolean shutdown;
    private ConnectBehavior connectBehavior = ConnectBehavior.SUCCEED;

    protected XnioWorkerMock(Xnio xnio, ThreadGroup threadGroup, OptionMap optionMap, Runnable terminationTask) {
        super(xnio, threadGroup, optionMap, terminationTask);
    }

    protected XnioWorkerMock(ThreadGroup threadGroup, OptionMap optionMap, Runnable terminationTask) {
        super(Xnio.getInstance(), threadGroup, optionMap, terminationTask);
    }

    @Override
    protected IoFuture<ConnectedStreamChannel> connectTcpStream(final InetSocketAddress bindAddress, final InetSocketAddress destinationAddress, final ChannelListener<? super ConnectedStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        switch(connectBehavior) {
            case SUCCEED: {
                ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
                channel.setLocalAddress(bindAddress);
                channel.setPeerAddress(destinationAddress);
                ChannelListeners.invokeChannelListener(channel, bindListener);
                ChannelListeners.invokeChannelListener(channel, openListener);
                channel.setWorker(this);
                channel.setOptionMap(optionMap);
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
    @Override
    protected AcceptingChannel<? extends ConnectedStreamChannel> createTcpServer(InetSocketAddress bindAddress, ChannelListener<? super AcceptingChannel<ConnectedStreamChannel>> acceptListener, OptionMap optionMap) throws IOException {
        AcceptingChannelMock channel = new AcceptingChannelMock();
        channel.setLocalAddress(bindAddress);
        channel.setOptionMap(optionMap);
        channel.setWorker(this);
        ((AcceptingChannel)channel).getAcceptSetter().set((ChannelListener<AcceptingChannel<ConnectedStreamChannel>>) acceptListener);
        return channel;
    }

    @Override
    public void shutdown() {
        shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        //return null;
        throw new RuntimeException("not implemented");
    }

    @Override
    public boolean isShutdown() {
        //return false;
        throw new RuntimeException("not implemented");
    }

    @Override
    public boolean isTerminated() {
        //return false;
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
