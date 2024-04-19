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

import static org.xnio._private.Messages.msg;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.xnio.ByteBufferPool;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.SslClientAuthMode;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedChannel;

/**
 * Abstract Accepting SSL channel.
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
abstract class AbstractAcceptingSslChannel<C extends ConnectedChannel, S extends ConnectedChannel> implements AcceptingChannel<C> {
    private final SSLContext sslContext;
    private final AcceptingChannel<? extends S> tcpServer;

    private volatile SslClientAuthMode clientAuthMode;
    private volatile int useClientMode;
    private volatile int enableSessionCreation;
    private volatile String[] cipherSuites;
    private volatile String[] protocols;

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<AbstractAcceptingSslChannel, SslClientAuthMode> clientAuthModeUpdater = AtomicReferenceFieldUpdater.newUpdater(AbstractAcceptingSslChannel.class, SslClientAuthMode.class, "clientAuthMode");
    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<AbstractAcceptingSslChannel> useClientModeUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractAcceptingSslChannel.class, "useClientMode");
    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<AbstractAcceptingSslChannel> enableSessionCreationUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractAcceptingSslChannel.class, "enableSessionCreation");
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<AbstractAcceptingSslChannel, String[]> cipherSuitesUpdater = AtomicReferenceFieldUpdater.newUpdater(AbstractAcceptingSslChannel.class, String[].class, "cipherSuites");
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<AbstractAcceptingSslChannel, String[]> protocolsUpdater = AtomicReferenceFieldUpdater.newUpdater(AbstractAcceptingSslChannel.class, String[].class, "protocols");

    private final ChannelListener.Setter<AcceptingChannel<C>> closeSetter;
    private final ChannelListener.Setter<AcceptingChannel<C>> acceptSetter;
    protected final boolean startTls;
    protected final ByteBufferPool socketBufferPool;
    protected final ByteBufferPool applicationBufferPool;


    AbstractAcceptingSslChannel(final SSLContext sslContext, final AcceptingChannel<? extends S> tcpServer, final OptionMap optionMap, final ByteBufferPool socketBufferPool, final ByteBufferPool applicationBufferPool, final boolean startTls) {
        this.tcpServer = tcpServer;
        this.sslContext = sslContext;
        this.socketBufferPool = socketBufferPool;
        this.applicationBufferPool = applicationBufferPool;
        this.startTls = startTls;
        clientAuthMode = optionMap.get(Options.SSL_CLIENT_AUTH_MODE);
        useClientMode = optionMap.get(Options.SSL_USE_CLIENT_MODE, false) ? 1 : 0;
        enableSessionCreation = optionMap.get(Options.SSL_ENABLE_SESSION_CREATION, true) ? 1 : 0;
        final Sequence<String> enabledCipherSuites = optionMap.get(Options.SSL_ENABLED_CIPHER_SUITES);
        cipherSuites = enabledCipherSuites != null ? enabledCipherSuites.toArray(new String[enabledCipherSuites.size()]) : null;
        final Sequence<String> enabledProtocols = optionMap.get(Options.SSL_ENABLED_PROTOCOLS);
        protocols = enabledProtocols != null ? enabledProtocols.toArray(new String[enabledProtocols.size()]) : null;
        //noinspection ThisEscapedInObjectConstruction
        closeSetter = ChannelListeners.<AcceptingChannel<C>>getDelegatingSetter(tcpServer.getCloseSetter(), this);
        //noinspection ThisEscapedInObjectConstruction
        acceptSetter = ChannelListeners.<AcceptingChannel<C>>getDelegatingSetter(tcpServer.getAcceptSetter(), this);
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
        throw msg.nullParameter("value");
    }

    public XnioWorker getWorker() {
        return tcpServer.getWorker();
    }

    public C accept() throws IOException {
        final S tcpConnection = tcpServer.accept();
        if (tcpConnection == null) {
            return null;
        }
        final InetSocketAddress peerAddress = tcpConnection.getPeerAddress(InetSocketAddress.class);
        final SSLEngine engine = sslContext.createSSLEngine(peerAddress.getHostString(), peerAddress.getPort());
        final boolean clientMode = useClientMode != 0;
        engine.setUseClientMode(clientMode);
        if (! clientMode) {
            final SslClientAuthMode clientAuthMode = AbstractAcceptingSslChannel.this.clientAuthMode;
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
        final String[] cipherSuites = AbstractAcceptingSslChannel.this.cipherSuites;
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
        final String[] protocols = AbstractAcceptingSslChannel.this.protocols;
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
        return accept(tcpConnection, engine);
    }

    protected abstract C accept(S tcpServer, SSLEngine sslEngine) throws IOException;

    public ChannelListener.Setter<? extends AcceptingChannel<C>> getCloseSetter() {
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

    public ChannelListener.Setter<? extends AcceptingChannel<C>> getAcceptSetter() {
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

    @Override
    public boolean isAcceptResumed() {
       return tcpServer.isAcceptResumed();
    }

    public void wakeupAccepts() {
        tcpServer.wakeupAccepts();
    }

    public void awaitAcceptable() throws IOException {
        tcpServer.awaitAcceptable();
    }

    public void awaitAcceptable(final long time, final TimeUnit timeUnit) throws IOException {
        tcpServer.awaitAcceptable(time, timeUnit);
    }

    @Deprecated
    public XnioExecutor getAcceptThread() {
        return tcpServer.getAcceptThread();
    }

    public XnioIoThread getIoThread() {
        return tcpServer.getIoThread();
    }
}
