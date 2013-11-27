package org.xnio.ssl;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Set;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.SslClientAuthMode;
import org.xnio.StreamConnection;

final class JsseSslConnection extends SslConnection {
    private final StreamConnection streamConnection;
    private final JsseStreamConduit conduit;

    private final ChannelListener.SimpleSetter<SslConnection> handshakeSetter = new ChannelListener.SimpleSetter<>();

    JsseSslConnection(final StreamConnection streamConnection, final SSLEngine engine, final Pool<ByteBuffer> socketBufferPool, final Pool<ByteBuffer> applicationBufferPool) {
        super(streamConnection.getIoThread());
        this.streamConnection = streamConnection;
        conduit = new JsseStreamConduit(this, engine, streamConnection.getSourceChannel().getConduit(), streamConnection.getSinkChannel().getConduit(), socketBufferPool, applicationBufferPool);
        setSourceConduit(conduit);
        setSinkConduit(conduit);
    }

    public void startHandshake() throws IOException {
        conduit.beginHandshake();
    }

    public SSLSession getSslSession() {
        return conduit.getSslSession();
    }

    protected void closeAction() throws IOException {
        final SSLEngine engine = conduit.getEngine();
        conduit.markTerminated();
        engine.closeOutbound();
        try {
            engine.closeInbound();
        } catch (IOException e1) {
            try {
                streamConnection.close();
            } catch (IOException e2) {
                e1.addSuppressed(e2);
            }
            throw e1;
        }
        streamConnection.close();
    }

    protected void notifyWriteClosed() {}

    protected void notifyReadClosed() {}

    public SocketAddress getPeerAddress() {
        return streamConnection.getPeerAddress();
    }

    public SocketAddress getLocalAddress() {
        return streamConnection.getLocalAddress();
    }

    public ChannelListener.Setter<? extends SslConnection> getHandshakeSetter() {
        return handshakeSetter;
    }

    void invokeHandshakeListener() {
        ChannelListeners.invokeChannelListener(this, handshakeSetter.get());
    }

    /** {@inheritDoc} */
    @Override
    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        if (option == Options.SSL_CLIENT_AUTH_MODE) {
            final SSLEngine engine = conduit.getEngine();
            try {
                return option.cast(engine.getNeedClientAuth() ? SslClientAuthMode.REQUIRED : engine.getWantClientAuth() ? SslClientAuthMode.REQUESTED : SslClientAuthMode.NOT_REQUESTED);
            } finally {
                engine.setNeedClientAuth(value == SslClientAuthMode.REQUIRED);
                engine.setWantClientAuth(value == SslClientAuthMode.REQUESTED);
            }
        } else if (option == Options.SECURE) {
            throw new IllegalArgumentException();
        } else {
            return streamConnection.setOption(option, value);
        }
    }

    /** {@inheritDoc} */
    @Override
    public <T> T getOption(final Option<T> option) throws IOException {
        if (option == Options.SSL_CLIENT_AUTH_MODE) {
            final SSLEngine engine = conduit.getEngine();
            return option.cast(engine.getNeedClientAuth() ? SslClientAuthMode.REQUIRED : engine.getWantClientAuth() ? SslClientAuthMode.REQUESTED : SslClientAuthMode.NOT_REQUESTED);
        } else {
            return option == Options.SECURE ? option.cast(Boolean.valueOf(conduit.isTls())) : streamConnection.getOption(option);
        }
    }

    private static final Set<Option<?>> SUPPORTED_OPTIONS = Option.setBuilder().add(Options.SECURE, Options.SSL_CLIENT_AUTH_MODE).create();

    /** {@inheritDoc} */
    @Override
    public boolean supportsOption(final Option<?> option) {
        return SUPPORTED_OPTIONS.contains(option) || streamConnection.supportsOption(option);
    }

}
