/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
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
package org.xnio.nio.test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import org.xnio.ChannelListener;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * Superclass for ssl tcp test cases to verify if
 * {@link javax.net.ssl.SSLEngineResult.Status#BUFFER_OVERFLOW}
 * is handled appropriately.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public abstract class AbstractNioSslBufferExpansionTcpTest<T extends ConnectedChannel, R extends StreamSourceChannel, W extends StreamSinkChannel> extends AbstractNioSslTcpTest<T, R, W> {

    @Override
    protected AcceptingChannel<? extends T> startServer(XnioWorker worker, final ChannelListener<? super T> serverHandler) throws
            IOException {
        AcceptingChannel<? extends T> server = super.startServer(worker, serverHandler);
        // server attack
        Socket socket = new Socket("127.0.0.1", SERVER_PORT);
        OutputStream outputStream = socket.getOutputStream();
        outputStream.write(new byte[]{0x16, 0x3, 0x3, 0x71, 0x41}, 0, 5);
        return server;
    }
}
