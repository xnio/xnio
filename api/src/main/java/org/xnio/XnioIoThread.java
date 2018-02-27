/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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

package org.xnio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import org.wildfly.common.Assert;
import org.xnio.channels.AssembledStreamChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.StreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import static org.xnio._private.Messages.msg;

/**
 * An XNIO thread.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings("unused")
public abstract class XnioIoThread extends Thread implements XnioExecutor, XnioIoFactory {
    private final XnioWorker worker;
    private final int number;

    /**
     * Construct a new instance.
     *
     * @param worker the XNIO worker to associate with
     * @param number the thread number
     */
    protected XnioIoThread(final XnioWorker worker, final int number) {
        this.number = number;
        this.worker = worker;
    }

    /**
     * Construct a new instance.
     *
     * @param worker the XNIO worker to associate with
     * @param number the thread number
     * @param name the thread name
     */
    protected XnioIoThread(final XnioWorker worker, final int number, final String name) {
        super(name);
        this.number = number;
        this.worker = worker;
    }

    /**
     * Construct a new instance.
     *
     * @param worker the XNIO worker to associate with
     * @param number the thread number
     * @param group the thread group
     * @param name the thread name
     */
    protected XnioIoThread(final XnioWorker worker, final int number, final ThreadGroup group, final String name) {
        super(group, name);
        this.number = number;
        this.worker = worker;
    }

    /**
     * Construct a new instance.
     *
     * @param worker the XNIO worker to associate with
     * @param number the thread number
     * @param group the thread group
     * @param name the thread name
     * @param stackSize the thread stack size
     */
    protected XnioIoThread(final XnioWorker worker, final int number, final ThreadGroup group, final String name, final long stackSize) {
        super(group, null, name, stackSize);
        this.number = number;
        this.worker = worker;
    }

    /**
     * Get the current XNIO thread.  If the current thread is not an XNIO thread, {@code null} is returned.
     *
     * @return the current XNIO thread
     */
    public static XnioIoThread currentThread() {
        final Thread thread = Thread.currentThread();
        if (thread instanceof XnioIoThread) {
            return (XnioIoThread) thread;
        } else {
            return null;
        }
    }

    /**
     * Get the current XNIO thread.  If the current thread is not an XNIO thread, an {@link IllegalStateException} is
     * thrown.
     *
     * @return the current XNIO thread
     * @throws IllegalStateException if the current thread is not an XNIO thread
     */
    public static XnioIoThread requireCurrentThread() throws IllegalStateException {
        final XnioIoThread thread = currentThread();
        if (thread == null) {
            throw msg.xnioThreadRequired();
        }
        return thread;
    }

    /**
     * Get the number of this thread.  In each XNIO worker, every IO thread is given a unique, sequential number.
     *
     * @return the number of this thread
     */
    public int getNumber() {
        return number;
    }

    /**
     * Get the XNIO worker associated with this thread.
     *
     * @return the XNIO worker
     */
    public XnioWorker getWorker() {
        return worker;
    }

    public IoFuture<StreamConnection> acceptStreamConnection(SocketAddress destination, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        if (destination == null) {
            throw msg.nullParameter("destination");
        }
        if (destination instanceof InetSocketAddress) {
            return acceptTcpStreamConnection((InetSocketAddress) destination, openListener, bindListener, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return acceptLocalStreamConnection((LocalSocketAddress) destination, openListener, bindListener, optionMap);
        } else {
            throw msg.badSockType(destination.getClass());
        }
    }

    /**
     * Implementation helper method to accept a local (UNIX domain) stream connection.
     *
     * @param destination the destination (bind) address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the acceptor is bound, or {@code null} for none
     * @param optionMap the option map
     *
     * @return the future connection
     */
    protected IoFuture<StreamConnection> acceptLocalStreamConnection(LocalSocketAddress destination, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw msg.unsupported("acceptLocalStreamConnection");
    }

    /**
     * Implementation helper method to accept a TCP connection.
     *
     * @param destination the destination (bind) address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the acceptor is bound, or {@code null} for none
     * @param optionMap the option map
     *
     * @return the future connection
     */
    protected IoFuture<StreamConnection> acceptTcpStreamConnection(InetSocketAddress destination, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw msg.unsupported("acceptTcpStreamConnection");
    }

    public IoFuture<MessageConnection> openMessageConnection(final SocketAddress destination, final ChannelListener<? super MessageConnection> openListener, final OptionMap optionMap) {
        if (destination == null) {
            throw msg.nullParameter("destination");
        }
        if (destination instanceof LocalSocketAddress) {
            return openLocalMessageConnection(Xnio.ANY_LOCAL_ADDRESS, (LocalSocketAddress) destination, openListener, optionMap);
        } else {
            throw msg.badSockType(destination.getClass());
        }
    }

    public IoFuture<MessageConnection> acceptMessageConnection(final SocketAddress destination, final ChannelListener<? super MessageConnection> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        if (destination == null) {
            throw msg.nullParameter("destination");
        }
        if (destination instanceof LocalSocketAddress) {
            return acceptLocalMessageConnection((LocalSocketAddress) destination, openListener, bindListener, optionMap);
        } else {
            throw msg.badSockType(destination.getClass());
        }
    }

    /**
     * Implementation helper method to accept a local (UNIX domain) datagram connection.
     *
     * @param destination the destination (bind) address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the acceptor is bound, or {@code null} for none
     * @param optionMap the option map
     *
     * @return the future connection
     */
    protected IoFuture<MessageConnection> acceptLocalMessageConnection(LocalSocketAddress destination, ChannelListener<? super MessageConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw msg.unsupported("acceptLocalMessageConnection");
    }

    public IoFuture<StreamConnection> openStreamConnection(SocketAddress destination, ChannelListener<? super StreamConnection> openListener, OptionMap optionMap) {
        Assert.checkNotNullParam("destination", destination);
        if (destination instanceof InetSocketAddress) {
            return internalOpenTcpStreamConnection((InetSocketAddress) destination, openListener, null, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return openLocalStreamConnection(Xnio.ANY_LOCAL_ADDRESS, (LocalSocketAddress) destination, openListener, null, optionMap);
        } else {
            throw msg.badSockType(destination.getClass());
        }
    }

    public IoFuture<StreamConnection> openStreamConnection(SocketAddress destination, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        Assert.checkNotNullParam("destination", destination);
        if (destination instanceof InetSocketAddress) {
            return internalOpenTcpStreamConnection((InetSocketAddress) destination, openListener, bindListener, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return openLocalStreamConnection(Xnio.ANY_LOCAL_ADDRESS, (LocalSocketAddress) destination, openListener, bindListener, optionMap);
        } else {
            throw msg.badSockType(destination.getClass());
        }
    }
    
    private IoFuture<StreamConnection> internalOpenTcpStreamConnection(InetSocketAddress destination, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        if (destination.isUnresolved()) {
            try {
                destination = new InetSocketAddress(InetAddress.getByName(destination.getHostString()), destination.getPort());
            } catch (UnknownHostException e) {
                return new FailedIoFuture<>(e);
            }
        }
        InetSocketAddress bindAddress = getWorker().getBindAddressTable().get(destination.getAddress());
        return openTcpStreamConnection(bindAddress == null ? Xnio.ANY_INET_ADDRESS : bindAddress, destination, openListener, bindListener, optionMap);
    }

    public IoFuture<StreamConnection> openStreamConnection(SocketAddress bindAddress, SocketAddress destination, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        Assert.checkNotNullParam("bindAddress", bindAddress);
        Assert.checkNotNullParam("destination", destination);
        if (bindAddress.getClass() != destination.getClass()) {
            throw msg.mismatchSockType(bindAddress.getClass(), destination.getClass());
        }
        if (destination instanceof InetSocketAddress) {
            return openTcpStreamConnection((InetSocketAddress) bindAddress, (InetSocketAddress) destination, openListener, bindListener, optionMap);
        } else if (destination instanceof LocalSocketAddress) {
            return openLocalStreamConnection((LocalSocketAddress) bindAddress, (LocalSocketAddress) destination, openListener, bindListener, optionMap);
        } else {
            throw msg.badSockType(destination.getClass());
        }
    }

    /**
     * Implementation helper method to connect to a TCP server.
     *
     * @param bindAddress the bind address
     * @param destinationAddress the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    protected IoFuture<StreamConnection> openTcpStreamConnection(InetSocketAddress bindAddress, InetSocketAddress destinationAddress, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw msg.unsupported("openTcpStreamConnection");
    }

    /**
     * Implementation helper method to connect to a local (UNIX domain) server.
     *
     * @param bindAddress the bind address
     * @param destinationAddress the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    protected IoFuture<StreamConnection> openLocalStreamConnection(LocalSocketAddress bindAddress, LocalSocketAddress destinationAddress, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap) {
        throw msg.unsupported("openLocalStreamConnection");
    }

    /**
     * Implementation helper method to connect to a local (UNIX domain) server.
     *
     * @param bindAddress the bind address
     * @param destinationAddress the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    protected IoFuture<MessageConnection> openLocalMessageConnection(LocalSocketAddress bindAddress, LocalSocketAddress destinationAddress, ChannelListener<? super MessageConnection> openListener, OptionMap optionMap) {
        throw msg.unsupported("openLocalMessageConnection");
    }

    public ChannelPipe<StreamChannel, StreamChannel> createFullDuplexPipe() throws IOException {
        final ChannelPipe<StreamConnection, StreamConnection> connection = createFullDuplexPipeConnection();
        final StreamChannel left = new AssembledStreamChannel(connection.getLeftSide(), connection.getLeftSide().getSourceChannel(), connection.getLeftSide().getSinkChannel());
        final StreamChannel right = new AssembledStreamChannel(connection.getRightSide(), connection.getRightSide().getSourceChannel(), connection.getRightSide().getSinkChannel());
        return new ChannelPipe<StreamChannel, StreamChannel>(left, right);
    }

    public ChannelPipe<StreamConnection, StreamConnection> createFullDuplexPipeConnection() throws IOException {
        return createFullDuplexPipeConnection(this);
    }

    public ChannelPipe<StreamSourceChannel, StreamSinkChannel> createHalfDuplexPipe() throws IOException {
        return createHalfDuplexPipe(this);
    }

    public ChannelPipe<StreamConnection, StreamConnection> createFullDuplexPipeConnection(final XnioIoFactory peer) throws IOException {
        throw msg.unsupported("createFullDuplexPipeConnection");
    }

    public ChannelPipe<StreamSourceChannel, StreamSinkChannel> createHalfDuplexPipe(final XnioIoFactory peer) throws IOException {
        throw msg.unsupported("createHalfDuplexPipe");
    }
}
