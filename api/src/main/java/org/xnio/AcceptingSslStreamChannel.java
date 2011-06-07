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

package org.xnio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.StandardConnectedSslStreamChannel;

final class AcceptingSslStreamChannel implements AcceptingChannel<ConnectedSslStreamChannel> {
    private final SSLContext sslContext;
    private final AcceptingChannel<? extends ConnectedStreamChannel> tcpServer;
    private final Pool<ByteBuffer> bufferPool;

    private volatile SslClientAuthMode clientAuthMode;
    private volatile int useClientMode;
    private volatile int enableSessionCreation;
    private volatile String[] cipherSuites;
    private volatile String[] protocols;

    private static final AtomicReferenceFieldUpdater<AcceptingSslStreamChannel, SslClientAuthMode> clientAuthModeUpdater = AtomicReferenceFieldUpdater.newUpdater(AcceptingSslStreamChannel.class, SslClientAuthMode.class, "clientAuthMode");
    private static final AtomicIntegerFieldUpdater<AcceptingSslStreamChannel> useClientModeUpdater = AtomicIntegerFieldUpdater.newUpdater(AcceptingSslStreamChannel.class, "useClientMode");
    private static final AtomicIntegerFieldUpdater<AcceptingSslStreamChannel> enableSessionCreationUpdater = AtomicIntegerFieldUpdater.newUpdater(AcceptingSslStreamChannel.class, "enableSessionCreation");
    private static final AtomicReferenceFieldUpdater<AcceptingSslStreamChannel, String[]> cipherSuitesUpdater = AtomicReferenceFieldUpdater.newUpdater(AcceptingSslStreamChannel.class, String[].class, "cipherSuites");
    private static final AtomicReferenceFieldUpdater<AcceptingSslStreamChannel, String[]> protocolsUpdater = AtomicReferenceFieldUpdater.newUpdater(AcceptingSslStreamChannel.class, String[].class, "protocols");

    private final ChannelListener.Setter<AcceptingChannel<ConnectedSslStreamChannel>> closeSetter;
    private final ChannelListener.Setter<AcceptingChannel<ConnectedSslStreamChannel>> acceptSetter;

    // FIXME support executor or eliminate it?
    AcceptingSslStreamChannel(final SSLContext sslContext, final AcceptingChannel<? extends ConnectedStreamChannel> tcpServer, final Executor executor, final OptionMap optionMap, final Pool<ByteBuffer> bufferPool) {
        this.tcpServer = tcpServer;
        this.sslContext = sslContext;
        this.bufferPool = bufferPool;
        clientAuthMode = optionMap.get(Options.SSL_CLIENT_AUTH_MODE);
        useClientMode = optionMap.get(Options.SSL_USE_CLIENT_MODE, false) ? 1 : 0;
        enableSessionCreation = optionMap.get(Options.SSL_ENABLE_SESSION_CREATION, true) ? 1 : 0;
        final Sequence<String> enabledCipherSuites = optionMap.get(Options.SSL_ENABLED_CIPHER_SUITES);
        cipherSuites = enabledCipherSuites != null ? enabledCipherSuites.toArray(new String[enabledCipherSuites.size()]) : null;
        final Sequence<String> enabledProtocols = optionMap.get(Options.SSL_ENABLED_PROTOCOLS);
        protocols = enabledProtocols != null ? enabledProtocols.toArray(new String[enabledProtocols.size()]) : null;
        //noinspection ThisEscapedInObjectConstruction
        closeSetter = ChannelListeners.<AcceptingChannel<ConnectedSslStreamChannel>>getDelegatingSetter(tcpServer.getCloseSetter(), this);
        //noinspection ThisEscapedInObjectConstruction
        acceptSetter = ChannelListeners.<AcceptingChannel<ConnectedSslStreamChannel>>getDelegatingSetter(tcpServer.getAcceptSetter(), this);
    }

    private static final Set<Option<?>> SUPPORTED_OPTIONS = Option.setBuilder()
            .add(Options.SSL_CLIENT_AUTH_MODE)
            .add(Options.SSL_USE_CLIENT_MODE)
            .add(Options.SSL_ENABLE_SESSION_CREATION)
            .add(Options.SSL_ENABLED_CIPHER_SUITES)
            .add(Options.SSL_ENABLED_PROTOCOLS)
            .create();

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        if (option == Options.SSL_CLIENT_AUTH_MODE) {
            return option.cast(clientAuthModeUpdater.getAndSet(this, Options.SSL_CLIENT_AUTH_MODE.cast(value)));
        } else if (option == Options.SSL_USE_CLIENT_MODE) {
            final Boolean valueObject = Options.SSL_USE_CLIENT_MODE.cast(value);
            if (valueObject != null) return option.cast(Boolean.valueOf(useClientModeUpdater.getAndSet(this, valueObject.booleanValue() ? 1 : 0) != 0));
        } else if (option == Options.SSL_ENABLE_SESSION_CREATION) {
            final Boolean valueObject = Options.SSL_ENABLE_SESSION_CREATION.cast(value);
            if (valueObject != null) return option.cast(Boolean.valueOf(enableSessionCreationUpdater.getAndSet(this, valueObject.booleanValue() ? 1 : 0) != 0));
        } else if (option == Options.SSL_ENABLED_CIPHER_SUITES) {
            final Sequence<String> seq = Options.SSL_ENABLED_CIPHER_SUITES.cast(value);
            return option.cast(cipherSuitesUpdater.getAndSet(this, seq == null ? null : seq.toArray(new String[seq.size()])));
        } else if (option == Options.SSL_ENABLED_PROTOCOLS) {
            final Sequence<String> seq = Options.SSL_ENABLED_PROTOCOLS.cast(value);
            return option.cast(protocolsUpdater.getAndSet(this, seq == null ? null : seq.toArray(new String[seq.size()])));
        } else {
            return tcpServer.setOption(option, value);
        }
        throw new IllegalArgumentException("value is null");
    }

    public ConnectedSslStreamChannel accept(final ReadChannelThread readThread, final WriteChannelThread writeThread) throws IOException {
        final ConnectedStreamChannel tcpChannel = tcpServer.accept(readThread, writeThread);
        if (tcpChannel == null) {
            return null;
        }
        final InetSocketAddress peerAddress = tcpChannel.getPeerAddress(InetSocketAddress.class);
        final SSLEngine engine = sslContext.createSSLEngine(peerAddress.getHostName(), peerAddress.getPort());
        final boolean clientMode = useClientMode != 0;
        engine.setUseClientMode(clientMode);
        if (! clientMode) {
            final SslClientAuthMode clientAuthMode = AcceptingSslStreamChannel.this.clientAuthMode;
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
        engine.setEnableSessionCreation(enableSessionCreation != 0);
        final String[] cipherSuites = AcceptingSslStreamChannel.this.cipherSuites;
        if (cipherSuites != null) {
            final Set<String> supported = new HashSet<String>(Arrays.asList(engine.getSupportedCipherSuites()));
            final List<String> finalList = new ArrayList<String>();
            for (String name : cipherSuites) {
                if (supported.contains(name)) {
                    finalList.add(name);
                }
            }
            engine.setEnabledCipherSuites(finalList.toArray(new String[finalList.size()]));
        }
        final String[] protocols = AcceptingSslStreamChannel.this.protocols;
        if (protocols != null) {
            final Set<String> supported = new HashSet<String>(Arrays.asList(engine.getSupportedProtocols()));
            final List<String> finalList = new ArrayList<String>();
            for (String name : protocols) {
                if (supported.contains(name)) {
                    finalList.add(name);
                }
            }
            engine.setEnabledProtocols(finalList.toArray(new String[finalList.size()]));
        }
        return new StandardConnectedSslStreamChannel(tcpChannel, engine, false, bufferPool, bufferPool);
    }

    public ChannelListener.Setter<? extends AcceptingChannel<ConnectedSslStreamChannel>> getCloseSetter() {
        return closeSetter;
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
            return option.cast(Boolean.valueOf(useClientMode != 0));
        } else if (option == Options.SSL_ENABLE_SESSION_CREATION) {
            return option.cast(Boolean.valueOf(enableSessionCreation != 0));
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

    public ChannelListener.Setter<? extends AcceptingChannel<ConnectedSslStreamChannel>> getAcceptSetter() {
        return acceptSetter;
    }

    public SocketAddress getLocalAddress() {
        return tcpServer.getLocalAddress();
    }

    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        return tcpServer.getLocalAddress(type);
    }

    public void suspendAccepts() {
        tcpServer.suspendAccepts();
    }

    public void resumeAccepts() {
        tcpServer.resumeAccepts();
    }

    public void awaitAcceptable() throws IOException {
        tcpServer.awaitAcceptable();
    }

    public void awaitAcceptable(final long time, final TimeUnit timeUnit) throws IOException {
        tcpServer.awaitAcceptable(time, timeUnit);
    }

    public void setAcceptThread(final ConnectionChannelThread thread) throws IllegalArgumentException {
        tcpServer.setAcceptThread(thread);
    }

    public ConnectionChannelThread getAcceptThread() {
        return tcpServer.getAcceptThread();
    }
}
