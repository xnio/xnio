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

import java.nio.ByteBuffer;

import javax.net.ssl.SSLEngine;

import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

/**
 * Wraps a StreamConnection inside a new connection with added SSL support. 
 * 
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class SslConnectionWrapper {

    /**
     * Wraps {@link streamConnection} inside a connection with added SSL support
     *  
     * @param streamConnection           the connection 
     * @param sslEngine                  the SSL engine
     * @param socketBufferPool           the socket buffer pool
     * @param applicationBufferPool      the application buffer pool
     * @return the wrapped connection
     */
    public static StreamConnection wrap(StreamConnection streamConnection, final SSLEngine sslEngine, final Pool<ByteBuffer> socketBufferPool, final Pool<ByteBuffer> applicationBufferPool) {
        final StreamSinkConduit sinkConduit = streamConnection.getSinkChannel().getConduit();
        final StreamSourceConduit sourceConduit = streamConnection.getSourceChannel().getConduit();
        @SuppressWarnings("unused") // delete this once this method is completely implemented
        final SslConduitEngine sslConduitEngine = new SslConduitEngine(sinkConduit, sourceConduit, sslEngine,
                socketBufferPool, applicationBufferPool);
        //return new StreamConnection(streamConnection.getWorker(), new SslStreamSinkConduit(sinkConduit), new SslStreamSourceConduit(sourceConduit));
        throw new RuntimeException("Not implemented"); // TODO
    }
}
