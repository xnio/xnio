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
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Set;
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.XnioWorker;

import static org.xnio.IoUtils.safeClose;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class NioSocketStreamConnection extends AbstractNioStreamConnection {

    private final SocketChannel channel;
    private final NioTcpServer server;
    private NioSocketSourceConduit sourceConduit;
    private NioSocketSinkConduit sinkConduit;

    NioSocketStreamConnection(final XnioWorker worker, final SocketChannel channel, final NioTcpServer server) {
        super(worker);
        this.channel = channel;
        this.server = server;
    }

    public SocketAddress getPeerAddress() {
        return channel.socket().getRemoteSocketAddress();
    }

    public SocketAddress getLocalAddress() {
        return channel.socket().getLocalSocketAddress();
    }

    protected void setSourceConduit(final NioSocketSourceConduit conduit) {
        this.sourceConduit = conduit;
        super.setSourceConduit(conduit);
    }

    protected void setSinkConduit(final NioSocketSinkConduit conduit) {
        this.sinkConduit = conduit;
        super.setSinkConduit(conduit);
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
            return option.cast(Boolean.valueOf(channel.socket().getSoLinger() == 0));
        } else if (option == Options.IP_TRAFFIC_CLASS) {
            return option.cast(Integer.valueOf(channel.socket().getTrafficClass()));
        } else if (option == Options.KEEP_ALIVE) {
            return option.cast(Boolean.valueOf(channel.socket().getKeepAlive()));
        } else if (option == Options.READ_TIMEOUT) {
            return option.cast(Integer.valueOf(sourceConduit.getReadTimeout()));
        } else if (option == Options.RECEIVE_BUFFER) {
            return option.cast(Integer.valueOf(channel.socket().getReceiveBufferSize()));
        } else if (option == Options.SEND_BUFFER) {
            return option.cast(Integer.valueOf(channel.socket().getSendBufferSize()));
        } else if (option == Options.TCP_NODELAY) {
            return option.cast(Boolean.valueOf(channel.socket().getTcpNoDelay()));
        } else if (option == Options.TCP_OOB_INLINE) {
            return option.cast(Boolean.valueOf(channel.socket().getOOBInline()));
        } else if (option == Options.WRITE_TIMEOUT) {
            return option.cast(Integer.valueOf(sinkConduit.getWriteTimeout()));
        } else {
            return null;
        }
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        T result;
        if (option == Options.CLOSE_ABORT) {
            result = option.cast(Boolean.valueOf(channel.socket().getSoLinger() == 0));
            channel.socket().setSoLinger(Options.CLOSE_ABORT.cast(value, Boolean.FALSE).booleanValue(), 0);
        } else if (option == Options.IP_TRAFFIC_CLASS) {
            result = option.cast(Integer.valueOf(channel.socket().getTrafficClass()));
            channel.socket().setTrafficClass(Options.IP_TRAFFIC_CLASS.cast(value).intValue());
        } else if (option == Options.KEEP_ALIVE) {
            result = option.cast(Boolean.valueOf(channel.socket().getKeepAlive()));
            channel.socket().setKeepAlive(Options.KEEP_ALIVE.cast(value, Boolean.FALSE).booleanValue());
        } else if (option == Options.READ_TIMEOUT) {
            result = option.cast(Integer.valueOf(sourceConduit.getAndSetReadTimeout(Options.READ_TIMEOUT.cast(value).intValue())));
        } else if (option == Options.RECEIVE_BUFFER) {
            result = option.cast(Integer.valueOf(channel.socket().getReceiveBufferSize()));
            channel.socket().setReceiveBufferSize(Options.RECEIVE_BUFFER.cast(value).intValue());
        } else if (option == Options.SEND_BUFFER) {
            result = option.cast(Integer.valueOf(channel.socket().getSendBufferSize()));
            channel.socket().setSendBufferSize(Options.SEND_BUFFER.cast(value).intValue());
        } else if (option == Options.TCP_NODELAY) {
            result = option.cast(Boolean.valueOf(channel.socket().getTcpNoDelay()));
            channel.socket().setTcpNoDelay(Options.TCP_NODELAY.cast(value, Boolean.FALSE).booleanValue());
        } else if (option == Options.TCP_OOB_INLINE) {
            result = option.cast(Boolean.valueOf(channel.socket().getOOBInline()));
            channel.socket().setOOBInline(Options.TCP_OOB_INLINE.cast(value, Boolean.FALSE).booleanValue());
        } else if (option == Options.WRITE_TIMEOUT) {
            result = option.cast(Integer.valueOf(sinkConduit.getAndSetWriteTimeout(Options.WRITE_TIMEOUT.cast(value).intValue())));
        } else {
            return null;
        }
        return result;
    }

    protected void closeAction() {
        safeClose(channel);
        server.channelClosed();
    }

    SocketChannel getChannel() {
        return channel;
    }
}
