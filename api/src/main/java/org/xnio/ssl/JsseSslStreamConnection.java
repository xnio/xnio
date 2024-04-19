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
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import org.xnio.ByteBufferPool;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.SslClientAuthMode;
import org.xnio.StreamConnection;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

import static org.xnio.Bits.allAreSet;
import static org.xnio.IoUtils.safeClose;

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

    @SuppressWarnings("unused")
    private volatile int state;

    private static final int FLAG_READ_CLOSE_REQUESTED           = 0b0001;
    private static final int FLAG_WRITE_CLOSE_REQUESTED          = 0b0010;
    private static final int FLAG_READ_CLOSED                    = 0b0100;
    private static final int FLAG_WRITE_CLOSED                   = 0b1000;

    private static final AtomicIntegerFieldUpdater<JsseSslStreamConnection> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(JsseSslStreamConnection.class, "state");

    public JsseSslStreamConnection(StreamConnection connection, SSLEngine sslEngine, final boolean startTls) {
        this(connection, sslEngine, JsseXnioSsl.bufferPool, JsseXnioSsl.bufferPool, startTls);
    }

    JsseSslStreamConnection(StreamConnection connection, SSLEngine sslEngine, final ByteBufferPool socketBufferPool, final ByteBufferPool applicationBufferPool, final boolean startTls) {
        super(connection.getIoThread());
        this.connection = connection;
        final StreamSinkConduit sinkConduit = connection.getSinkChannel().getConduit();
        final StreamSourceConduit sourceConduit = connection.getSourceChannel().getConduit();
        sslConduitEngine = new JsseSslConduitEngine(this, sinkConduit, sourceConduit, sslEngine, socketBufferPool, applicationBufferPool);
        tls = ! startTls;
        setSinkConduit(new JsseSslStreamSinkConduit(sinkConduit, sslConduitEngine, tls));
        setSourceConduit(new JsseSslStreamSourceConduit(sourceConduit, sslConduitEngine, tls));
        getSourceChannel().setCloseListener(channel -> readClosed());
        getSinkChannel().setCloseListener(channel -> writeClosed());
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
        // when invoked from the outside world, like an attempt to close the connection
        // we need to first invoke close on the engine
        // when the engine is closed, it will invoke closeAction again, then we enter else block below
        if (!sslConduitEngine.isClosed())
            sslConduitEngine.close();
        else {
            if (tls) {
                try {
                    getSinkChannel().getConduit().terminateWrites();
                } catch (IOException e) {
                    try {
                        getSourceChannel().getConduit().terminateReads();
                    } catch (IOException ignored) {
                    }
                    safeClose(connection);
                    throw e;
                }
                try {
                    getSourceChannel().getConduit().terminateReads();
                } catch (IOException e) {
                    safeClose(connection);
                    throw e;
                }
                super.closeAction();
            }
            connection.close();
        }
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
        if (option == Options.SSL_CLIENT_AUTH_MODE) {
            final SSLEngine engine = sslConduitEngine.getEngine();
            try {
                return option.cast(engine.getNeedClientAuth() ? SslClientAuthMode.REQUIRED : engine.getWantClientAuth() ? SslClientAuthMode.REQUESTED : SslClientAuthMode.NOT_REQUESTED);
            } finally {
                engine.setNeedClientAuth(value == SslClientAuthMode.REQUIRED);
                engine.setWantClientAuth(value == SslClientAuthMode.REQUESTED);
            }
        } else if (option == Options.SECURE) {
            throw new IllegalArgumentException();
        } else {
            return connection.setOption(option, value);
        }
    }

    /** {@inheritDoc} */
    @Override
    public <T> T getOption(final Option<T> option) throws IOException {
        if (option == Options.SSL_CLIENT_AUTH_MODE) {
            final SSLEngine engine = sslConduitEngine.getEngine();
            return option.cast(engine.getNeedClientAuth() ? SslClientAuthMode.REQUIRED : engine.getWantClientAuth() ? SslClientAuthMode.REQUESTED : SslClientAuthMode.NOT_REQUESTED);
        } else {
            return option == Options.SECURE ? option.cast(Boolean.valueOf(tls)) : connection.getOption(option);
        }
    }

    private static final Set<Option<?>> SUPPORTED_OPTIONS = Option.setBuilder().add(Options.SECURE, Options.SSL_CLIENT_AUTH_MODE).create();

    /** {@inheritDoc} */
    @Override
    public boolean supportsOption(final Option<?> option) {
        return SUPPORTED_OPTIONS.contains(option) || connection.supportsOption(option);
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

    protected boolean readClosed() {
        final boolean closeRequestedNow;
        synchronized(this) {
            int oldVal, newVal;
            do {
                oldVal = state;
                if (allAreSet(oldVal, FLAG_READ_CLOSE_REQUESTED)) {
                    break;
                }
                newVal = oldVal | FLAG_READ_CLOSE_REQUESTED;
            } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
            closeRequestedNow = allAreSet(oldVal, FLAG_READ_CLOSE_REQUESTED);
            if (sslConduitEngine.isClosed() || !tls/* || sslConduitEngine.isFirstHandshake()*/) {
                do {
                    oldVal = state;
                    if (allAreSet(oldVal, FLAG_READ_CLOSED)) {
                        return false;
                    }
                    newVal = oldVal | FLAG_READ_CLOSED;
                } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
                if (allAreSet(oldVal, FLAG_READ_CLOSED)) {
                    return false;
                }
            } else return closeRequestedNow;
        }
        // invoke super.readClosed when we know that engine is closed, with previous state check we make sure we don't invoke this twice
        super.readClosed();
        return closeRequestedNow;
    }

    protected boolean writeClosed() {
        final boolean closeRequestedNow;
        synchronized(this) {
            int oldVal, newVal;
            do {
                oldVal = state;
                if (allAreSet(oldVal, FLAG_WRITE_CLOSE_REQUESTED)) {
                    break;
                }
                newVal = oldVal | FLAG_WRITE_CLOSE_REQUESTED;
            } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
            closeRequestedNow = allAreSet(oldVal, FLAG_WRITE_CLOSE_REQUESTED);
            if (sslConduitEngine.isClosed()  || !tls/* || sslConduitEngine.isFirstHandshake()*/) {
                do {
                    oldVal = state;
                    if (allAreSet(oldVal, FLAG_WRITE_CLOSED)) {
                        return false;
                    }
                    newVal = oldVal | FLAG_WRITE_CLOSED;
                } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
                if (allAreSet(oldVal, FLAG_WRITE_CLOSED)) {
                    return false;
                }
            } else return closeRequestedNow;
        }
        // invoke super.writeClosed when we know that engine is closed, with previous state check we make sure we don't invoke this twice,
        // and we also get the appropriate return value, that is independent if we internally invoke writeClosed or not
        super.writeClosed();
        return closeRequestedNow;
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

    @Override
    public boolean isReadShutdown() {
        return allAreSet(state, FLAG_READ_CLOSE_REQUESTED);
    }

    /**
     * Determine whether writes have been shut down on this connection.
     *
     * @return {@code true} if writes were shut down
     */
    public boolean isWriteShutdown() {
        return allAreSet(state, FLAG_WRITE_CLOSE_REQUESTED);
    }
}
