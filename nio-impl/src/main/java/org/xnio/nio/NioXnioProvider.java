/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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
import java.nio.channels.Pipe;
import java.nio.channels.SocketChannel;
import org.xnio.Xnio;
import org.xnio.XnioProvider;
import org.xnio.XnioWorker;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * The NIO XNIO provider.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class NioXnioProvider implements XnioProvider {
    private static final Xnio INSTANCE = new NioXnio();

    /** {@inheritDoc} */
    public Xnio getInstance() {
        return INSTANCE;
    }

    /** {@inheritDoc} */
    public String getName() {
        return INSTANCE.getName();
    }

    /**
     * Adopt an NIO channel into an NIO XNIO channel.
     *
     * @param worker an XNIO worker created from this provider
     * @param socketChannel the NIO socket channel
     * @return the XNIO connected stream channel
     * @throws IOException if the adoption failed
     */
    public ConnectedStreamChannel adopt(XnioWorker worker, SocketChannel socketChannel) throws IOException {
        NioXnioWorker nioWorker = (NioXnioWorker) worker;
        socketChannel.configureBlocking(false);
        NioTcpChannel channel = new NioTcpChannel(nioWorker, null, socketChannel);
        channel.start();
        return channel;
    }

    /**
     * Adopt an NIO channel into an NIO XNIO channel.
     *
     * @param worker an XNIO worker created from this provider
     * @param pipeChannel the NIO pipe source channel
     * @return the XNIO stream source channel
     * @throws IOException if the adoption failed
     */
    public StreamSourceChannel adopt(XnioWorker worker, Pipe.SourceChannel pipeChannel) throws IOException {
        NioXnioWorker nioWorker = (NioXnioWorker) worker;
        pipeChannel.configureBlocking(false);
        NioPipeSourceChannel channel = new NioPipeSourceChannel(nioWorker, pipeChannel);
        channel.start();
        return channel;
    }

    /**
     * Adopt an NIO channel into an NIO XNIO channel.
     *
     * @param worker an XNIO worker created from this provider
     * @param pipeChannel the NIO pipe sink channel
     * @return the XNIO stream sink channel
     * @throws IOException if the adoption failed
     */
    public StreamSinkChannel adopt(XnioWorker worker, Pipe.SinkChannel pipeChannel) throws IOException {
        NioXnioWorker nioWorker = (NioXnioWorker) worker;
        pipeChannel.configureBlocking(false);
        NioPipeSinkChannel channel = new NioPipeSinkChannel(nioWorker, pipeChannel);
        channel.start();
        return channel;
    }


}
