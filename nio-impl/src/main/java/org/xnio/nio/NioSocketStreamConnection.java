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

package org.xnio.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Set;
import org.xnio.Option;
import org.xnio.Options;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class NioSocketStreamConnection extends AbstractNioStreamConnection {

    private final ChannelClosed closedHandle;
    private final NioSocketConduit conduit;

    NioSocketStreamConnection(final WorkerThread workerThread, final SelectionKey key, final ChannelClosed closedHandle) {
        super(workerThread);
        conduit = new NioSocketConduit(workerThread, key, this);
        key.attach(conduit);
        this.closedHandle = closedHandle;
        setSinkConduit(conduit);
        setSourceConduit(conduit);
    }

    public SocketAddress getPeerAddress() {
        final Socket socket = conduit.getSocketChannel().socket();
        return new InetSocketAddress(socket.getInetAddress(), socket.getPort());
    }

    public SocketAddress getLocalAddress() {
        final Socket socket = conduit.getSocketChannel().socket();
        return new InetSocketAddress(socket.getLocalAddress(), socket.getLocalPort());
    }

    private static final Set<Option<?>> OPTIONS = Option.setBuilder()
            .add(Options.CLOSE_ABORT)
            .add(Options.IP_TRAFFIC_CLASS)
            .add(Options.KEEP_ALIVE)
            .add(Options.READ_TIMEOUT)
            .add(Options.RECEIVE_BUFFER)
            .add(Options.SEND_BUFFER)
            .add(Options.TCP_NODELAY)
            .add(Options.TCP_OOB_INLINE)
            .add(Options.WRITE_TIMEOUT)
            .create();

    public boolean supportsOption(final Option<?> option) {
        return OPTIONS.contains(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        if (option == Options.CLOSE_ABORT) {
            return option.cast(Boolean.valueOf(conduit.getSocketChannel().socket().getSoLinger() == 0));
        } else if (option == Options.IP_TRAFFIC_CLASS) {
            return option.cast(Integer.valueOf(conduit.getSocketChannel().socket().getTrafficClass()));
        } else if (option == Options.KEEP_ALIVE) {
            return option.cast(Boolean.valueOf(conduit.getSocketChannel().socket().getKeepAlive()));
        } else if (option == Options.READ_TIMEOUT) {
            return option.cast(Integer.valueOf(conduit.getReadTimeout()));
        } else if (option == Options.RECEIVE_BUFFER) {
            return option.cast(Integer.valueOf(conduit.getSocketChannel().socket().getReceiveBufferSize()));
        } else if (option == Options.SEND_BUFFER) {
            return option.cast(Integer.valueOf(conduit.getSocketChannel().socket().getSendBufferSize()));
        } else if (option == Options.TCP_NODELAY) {
            return option.cast(Boolean.valueOf(conduit.getSocketChannel().socket().getTcpNoDelay()));
        } else if (option == Options.TCP_OOB_INLINE) {
            return option.cast(Boolean.valueOf(conduit.getSocketChannel().socket().getOOBInline()));
        } else if (option == Options.WRITE_TIMEOUT) {
            return option.cast(Integer.valueOf(conduit.getWriteTimeout()));
        } else {
            return null;
        }
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        T result;
        if (option == Options.CLOSE_ABORT) {
            result = option.cast(Boolean.valueOf(conduit.getSocketChannel().socket().getSoLinger() == 0));
            conduit.getSocketChannel().socket().setSoLinger(Options.CLOSE_ABORT.cast(value, Boolean.FALSE).booleanValue(), 0);
        } else if (option == Options.IP_TRAFFIC_CLASS) {
            result = option.cast(Integer.valueOf(conduit.getSocketChannel().socket().getTrafficClass()));
            conduit.getSocketChannel().socket().setTrafficClass(Options.IP_TRAFFIC_CLASS.cast(value).intValue());
        } else if (option == Options.KEEP_ALIVE) {
            result = option.cast(Boolean.valueOf(conduit.getSocketChannel().socket().getKeepAlive()));
            conduit.getSocketChannel().socket().setKeepAlive(Options.KEEP_ALIVE.cast(value, Boolean.FALSE).booleanValue());
        } else if (option == Options.READ_TIMEOUT) {
            result = option.cast(Integer.valueOf(conduit.getAndSetReadTimeout(value == null ? 0 : Options.READ_TIMEOUT.cast(value).intValue())));
        } else if (option == Options.RECEIVE_BUFFER) {
            result = option.cast(Integer.valueOf(conduit.getSocketChannel().socket().getReceiveBufferSize()));
            conduit.getSocketChannel().socket().setReceiveBufferSize(Options.RECEIVE_BUFFER.cast(value).intValue());
        } else if (option == Options.SEND_BUFFER) {
            result = option.cast(Integer.valueOf(conduit.getSocketChannel().socket().getSendBufferSize()));
            conduit.getSocketChannel().socket().setSendBufferSize(Options.SEND_BUFFER.cast(value).intValue());
        } else if (option == Options.TCP_NODELAY) {
            result = option.cast(Boolean.valueOf(conduit.getSocketChannel().socket().getTcpNoDelay()));
            conduit.getSocketChannel().socket().setTcpNoDelay(Options.TCP_NODELAY.cast(value, Boolean.FALSE).booleanValue());
        } else if (option == Options.TCP_OOB_INLINE) {
            result = option.cast(Boolean.valueOf(conduit.getSocketChannel().socket().getOOBInline()));
            conduit.getSocketChannel().socket().setOOBInline(Options.TCP_OOB_INLINE.cast(value, Boolean.FALSE).booleanValue());
        } else if (option == Options.WRITE_TIMEOUT) {
            result = option.cast(Integer.valueOf(conduit.getAndSetWriteTimeout(value == null ? 0 : Options.WRITE_TIMEOUT.cast(value).intValue())));
        } else {
            return null;
        }
        return result;
    }

    protected void closeAction() throws IOException {
        try {
            conduit.cancelKey(false);
            conduit.getSocketChannel().close();
        } catch (ClosedChannelException ignored) {
        } finally {
            final ChannelClosed closedHandle = this.closedHandle;
            if (closedHandle!= null) closedHandle.channelClosed();
        }
    }

    protected void notifyWriteClosed() {
        conduit.writeTerminated();
    }

    protected void notifyReadClosed() {
        conduit.readTerminated();
    }

    SocketChannel getChannel() {
        return conduit.getSocketChannel();
    }

    NioSocketConduit getConduit() {
        return conduit;
    }
}
