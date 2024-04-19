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

package org.xnio.ssl;

import java.io.IOException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.xnio.ByteBufferPool;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.channels.AcceptingChannel;

/**
 * Accepting channel for StreamConnections with SSL.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
final class JsseAcceptingSslStreamConnection extends AbstractAcceptingSslChannel<SslConnection, StreamConnection> {

    JsseAcceptingSslStreamConnection(final SSLContext sslContext, final AcceptingChannel<? extends StreamConnection> tcpServer, final OptionMap optionMap, final ByteBufferPool socketBufferPool, final ByteBufferPool applicationBufferPool, final boolean startTls) {
        super(sslContext, tcpServer, optionMap, socketBufferPool, applicationBufferPool, startTls);
    }

    @Override
    public SslConnection accept(StreamConnection tcpConnection, SSLEngine engine) throws IOException {
        if (! JsseXnioSsl.NEW_IMPL) {
            return new JsseSslStreamConnection(tcpConnection, engine, socketBufferPool, applicationBufferPool, startTls);
        }
        JsseSslConnection connection = new JsseSslConnection(tcpConnection, engine, socketBufferPool, applicationBufferPool);
        if (!startTls) {
            try {
                connection.startHandshake();
            } catch (IOException e) {
                IoUtils.safeClose(connection);
                throw e;
            }
        }
        return connection;
    }
}
