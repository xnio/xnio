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
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.channels.SslConnection;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

/**
 * StreamConnection with SSL support.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public final class JsseSslStreamConnection extends SslConnection {

    /**
     * Delegate connection.
     */
    private final StreamConnection connection;
    /**
     * The conduit SSl engine.
     */
    private final JsseSslConduitEngine sslConduitEngine;
    /**
     * Indicates if tls is enabled.
     */
    private volatile boolean tls;
    /**
     * Callback for notification of a handshake being finished.
     */
    private final ChannelListener.SimpleSetter<SslConnection> handshakeSetter = new ChannelListener.SimpleSetter<SslConnection>();

    JsseSslStreamConnection(StreamConnection connection, SSLEngine sslEngine, final Pool<ByteBuffer> socketBufferPool, final Pool<ByteBuffer> applicationBufferPool, final boolean startTls) {
        super(connection.getIoThread());
        this.connection = connection;
        final StreamSinkConduit sinkConduit = connection.getSinkChannel().getConduit();
        final StreamSourceConduit sourceConduit = connection.getSourceChannel().getConduit();
        sslConduitEngine = new JsseSslConduitEngine(this, sinkConduit, sourceConduit, sslEngine, socketBufferPool, applicationBufferPool);
        tls = ! startTls;
        setSinkConduit(new JsseSslStreamSinkConduit(sinkConduit, sslConduitEngine, tls));
        setSourceConduit(new JsseSslStreamSourceConduit(sourceConduit, sslConduitEngine, tls));
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void startHandshake() throws IOException {
        if (! tls) {
            tls = true;
            ((JsseSslStreamSourceConduit) getSourceChannel().getConduit()).enableTls();
            ((JsseSslStreamSinkConduit) getSinkChannel().getConduit()).enableTls();
        }
        sslConduitEngine.beginHandshake();
    }

    /** {@inheritDoc} */
    @Override
    public SocketAddress getPeerAddress() {
        return connection.getPeerAddress();
    }


    /** {@inheritDoc} */
    @Override
    public SocketAddress getLocalAddress() {
        return connection.getLocalAddress();
    }

    /** {@inheritDoc} */
    @Override
    protected void closeAction() throws IOException {
        if (tls) {
            sslConduitEngine.closeOutbound();
            sslConduitEngine.closeInbound();
        }
        connection.close();
    }

    /** {@inheritDoc} */
    @Override
    protected void notifyWriteClosed() {}

    /** {@inheritDoc} */
    @Override
    protected void notifyReadClosed() {}

    /** {@inheritDoc} */
    @Override
    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return connection.setOption(option, value);
    }

    /** {@inheritDoc} */
    @Override
    public <T> T getOption(final Option<T> option) throws IOException {
        return option == Options.SECURE ? option.cast(Boolean.valueOf(tls)) : connection.getOption(option);
    }

    /** {@inheritDoc} */
    @Override
    public boolean supportsOption(final Option<?> option) {
        return option == Options.SECURE || connection.supportsOption(option);
    }

    /** {@inheritDoc} */
    @Override
    public SSLSession getSslSession() {
        return tls ? sslConduitEngine.getSession() : null;
    }

    @Override
    public org.xnio.ChannelListener.Setter<? extends SslConnection> getHandshakeSetter() {
        return handshakeSetter;
    }

    SSLEngine getEngine() {
        return sslConduitEngine.getEngine();
    }

    /**
     * Callback method for notification of handshake finished.
     */
    protected void handleHandshakeFinished() {
        final ChannelListener<? super SslConnection> listener = handshakeSetter.get();
        if (listener == null) {
            return;
        }
        ChannelListeners.<SslConnection>invokeChannelListener(this, listener);
    }

    public SSLEngine getSslEngine() {
        return sslConduitEngine.getEngine();
    }

}
