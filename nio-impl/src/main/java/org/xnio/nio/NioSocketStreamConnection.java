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

import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import org.xnio.XnioWorker;

import static org.xnio.IoUtils.safeClose;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class NioSocketStreamConnection extends AbstractNioStreamConnection {

    private final SocketChannel channel;
    private final NioTcpServer server;

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

    protected void closeAction() {
        safeClose(channel);
        server.channelClosed();
    }

    SocketChannel getChannel() {
        return channel;
    }
}
