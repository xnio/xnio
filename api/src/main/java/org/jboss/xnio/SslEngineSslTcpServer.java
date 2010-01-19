/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.xnio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Executor;
import org.jboss.xnio.channels.BoundChannel;
import org.jboss.xnio.channels.Channels;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.channels.SslTcpChannel;
import org.jboss.xnio.channels.TcpChannel;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

final class SslEngineSslTcpServer implements SslTcpServer {
    private final SSLContext sslContext;
    private final TcpServer tcpServer;
    private final Executor sslExecutor;

    private volatile SslClientAuthMode clientAuthMode;
    private volatile boolean useClientMode;
    private volatile boolean enableSessionCreation;
    private volatile String[] cipherSuites;
    private volatile String[] protocols;

    private final ChannelListener.Setter<SslTcpServer> closeSetter;
    private final ChannelListener.Setter<SslTcpChannel> openSetter = new ChannelListener.Setter<SslTcpChannel>() {
        public void set(final ChannelListener<? super SslTcpChannel> channelListener) {
            tcpServer.getOpenSetter().set(channelListener == null ? null : new ChannelListener<TcpChannel>() {
                @SuppressWarnings({ "deprecation" })
                public void handleEvent(final TcpChannel tcpChannel) {
                    final InetSocketAddress peerAddress = tcpChannel.getPeerAddress();
                    final SSLEngine engine = sslContext.createSSLEngine(peerAddress.getHostName(), peerAddress.getPort());
                    final boolean clientMode = useClientMode;
                    engine.setUseClientMode(clientMode);
                    if (! clientMode) {
                        final SslClientAuthMode clientAuthMode = SslEngineSslTcpServer.this.clientAuthMode;
                        if (clientAuthMode != null) switch (clientAuthMode) {
                            case NOT_REQUESTED:
                                engine.setNeedClientAuth(false);
                                engine.setWantClientAuth(false);
                                break;
                            case REQUESTED:
                                engine.setWantClientAuth(true);
                                break;
                            case REQUIRED:
                                engine.setNeedClientAuth(true);
                                break;
                            default: throw new IllegalStateException();
                        }
                    }
                    engine.setEnableSessionCreation(enableSessionCreation);
                    final String[] cipherSuites = SslEngineSslTcpServer.this.cipherSuites;
                    if (cipherSuites != null) {
                        engine.setEnabledCipherSuites(cipherSuites);
                    } else {
                        engine.setEnabledCipherSuites(engine.getSupportedCipherSuites());
                    }
                    final String[] protocols = SslEngineSslTcpServer.this.protocols;
                    if (protocols != null) {
                        engine.setEnabledProtocols(protocols);
                    }
                    channelListener.handleEvent(Channels.createSslTcpChannel(tcpChannel, engine, sslExecutor));
                }
            });
        }
    };

    SslEngineSslTcpServer(final SSLContext sslContext, final TcpServer tcpServer, final Executor executor, final OptionMap optionMap) {
        this.tcpServer = tcpServer;
        sslExecutor = executor;
        this.sslContext = sslContext;
        clientAuthMode = optionMap.get(Options.SSL_CLIENT_AUTH_MODE);
        useClientMode = optionMap.get(Options.SSL_USE_CLIENT_MODE, false);
        enableSessionCreation = optionMap.get(Options.SSL_ENABLE_SESSION_CREATION, true);
        final Sequence<String> enabledCipherSuites = optionMap.get(Options.SSL_ENABLED_CIPHER_SUITES);
        cipherSuites = enabledCipherSuites != null ? enabledCipherSuites.toArray(new String[enabledCipherSuites.size()]) : null;
        final Sequence<String> enabledProtocols = optionMap.get(Options.SSL_ENABLED_PROTOCOLS);
        protocols = enabledProtocols != null ? enabledProtocols.toArray(new String[enabledProtocols.size()]) : null;
        //noinspection ThisEscapedInObjectConstruction
        closeSetter = IoUtils.<SslTcpServer>getDelegatingSetter(tcpServer.getCloseSetter(), this);
    }

    private static final Set<Option<?>> SUPPORTED_OPTIONS = Option.setBuilder()
            .add(Options.SSL_CLIENT_AUTH_MODE)
            .add(Options.SSL_USE_CLIENT_MODE)
            .add(Options.SSL_ENABLE_SESSION_CREATION)
            .add(Options.SSL_ENABLED_CIPHER_SUITES)
            .add(Options.SSL_ENABLED_PROTOCOLS)
            .create();

    public <T> Configurable setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        if (option == Options.SSL_CLIENT_AUTH_MODE) {
            clientAuthMode = Options.SSL_CLIENT_AUTH_MODE.cast(value);
        } else if (option == Options.SSL_USE_CLIENT_MODE) {
            final Boolean valueObject = Options.SSL_USE_CLIENT_MODE.cast(value);
            if (valueObject != null) useClientMode = valueObject.booleanValue();
        } else if (option == Options.SSL_ENABLE_SESSION_CREATION) {
            final Boolean valueObject = Options.SSL_ENABLE_SESSION_CREATION.cast(value);
            if (valueObject != null) enableSessionCreation = valueObject.booleanValue();
        } else if (option == Options.SSL_ENABLED_CIPHER_SUITES) {
            final Sequence<String> seq = Options.SSL_ENABLED_CIPHER_SUITES.cast(value);
            cipherSuites = seq == null ? null : seq.toArray(new String[seq.size()]);
        } else if (option == Options.SSL_ENABLED_PROTOCOLS) {
            final Sequence<String> seq = Options.SSL_ENABLED_PROTOCOLS.cast(value);
            protocols = seq == null ? null : seq.toArray(new String[seq.size()]);
        } else {
            tcpServer.setOption(option, value);
        }
        return this;
    }

    public ChannelListener.Setter<? extends BoundChannel<InetSocketAddress>> getBindSetter() {
        return tcpServer.getBindSetter();
    }

    public ChannelListener.Setter<? extends SslTcpServer> getCloseSetter() {
        return closeSetter;
    }

    public ChannelListener.Setter<? extends SslTcpChannel> getOpenSetter() {
        return openSetter;
    }

    public Collection<? extends BoundChannel<InetSocketAddress>> getChannels() {
        return tcpServer.getChannels();
    }

    public IoFuture<? extends BoundChannel<InetSocketAddress>> bind(final InetSocketAddress address) {
        return tcpServer.bind(address);
    }

    public boolean isOpen() {
        return tcpServer.isOpen();
    }

    public void close() throws IOException {
        tcpServer.close();
    }

    public boolean supportsOption(final Option<?> option) {
        return SUPPORTED_OPTIONS.contains(option) || tcpServer.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        if (option == Options.SSL_CLIENT_AUTH_MODE) {
            return option.cast(clientAuthMode);
        } else if (option == Options.SSL_USE_CLIENT_MODE) {
            return option.cast(Boolean.valueOf(useClientMode));
        } else if (option == Options.SSL_ENABLE_SESSION_CREATION) {
            return option.cast(Boolean.valueOf(enableSessionCreation));
        } else if (option == Options.SSL_ENABLED_CIPHER_SUITES) {
            final String[] cipherSuites = this.cipherSuites;
            return cipherSuites == null ? null : option.cast(Sequence.of(cipherSuites));
        } else if (option == Options.SSL_ENABLED_PROTOCOLS) {
            final String[] protocols = this.protocols;
            return protocols == null ? null : option.cast(Sequence.of(protocols));
        } else {
            return tcpServer.getOption(option);
        }
    }
}
