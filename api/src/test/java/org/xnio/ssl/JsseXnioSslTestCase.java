/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.channels.Channel;
import java.util.concurrent.CancellationException;

import javax.net.ssl.SSLContext;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.xnio.ChannelListener;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.mock.XnioWorkerMock;

/**
 * Test for {@link JsseXnioSsl}.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
@SuppressWarnings("deprecation")
public class JsseXnioSslTestCase {
    private static final String KEY_STORE_PROPERTY = "javax.net.ssl.keyStore";
    private static final String KEY_STORE_PASSWORD_PROPERTY = "javax.net.ssl.keyStorePassword";
    private static final String TRUST_STORE_PROPERTY = "javax.net.ssl.trustStore";
    private static final String TRUST_STORE_PASSWORD_PROPERTY = "javax.net.ssl.trustStorePassword";
    private static final String DEFAULT_KEY_STORE = "keystore.jks";
    private static final String DEFAULT_KEY_STORE_PASSWORD = "apiTest";

    private static final int SERVER_PORT = 23456;

    @BeforeClass
    public static void setKeyStoreAndTrustStore() {
        final URL storePath = JsseXnioSslTestCase.class.getClassLoader().getResource(DEFAULT_KEY_STORE);
        if (System.getProperty(KEY_STORE_PROPERTY) == null) {
            System.setProperty(KEY_STORE_PROPERTY, storePath.getFile());
        }
        if (System.getProperty(KEY_STORE_PASSWORD_PROPERTY) == null) {
            System.setProperty(KEY_STORE_PASSWORD_PROPERTY, DEFAULT_KEY_STORE_PASSWORD);
        }
        if (System.getProperty(TRUST_STORE_PROPERTY) == null) {
            System.setProperty(TRUST_STORE_PROPERTY, storePath.getFile());
        }
        if (System.getProperty(TRUST_STORE_PASSWORD_PROPERTY) == null) {
            System.setProperty(TRUST_STORE_PASSWORD_PROPERTY, DEFAULT_KEY_STORE_PASSWORD);
        }
    }

    private InetSocketAddress serverAddress;
    private Xnio xnio;
    private XnioWorkerMock worker;
    private XnioSsl xnioSsl;

    @Before
    public void init() throws Exception {
        serverAddress = new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT);
        xnio = Xnio.getInstance("xnio-mock", JsseXnioSslTestCase.class.getClassLoader());
        worker = (XnioWorkerMock) xnio.createWorker(OptionMap.EMPTY);
        xnioSsl = xnio.getSslProvider(OptionMap.EMPTY);
    }
    @Test
    public void testCreateSslConnectionServer() throws Exception {
        final TestChannelListener<AcceptingChannel<SslConnection>> openListener = new TestChannelListener<AcceptingChannel<SslConnection>>();
        // IDEA thinks this is unchecked because of http://youtrack.jetbrains.net/issue/IDEA-59290
        @SuppressWarnings("unchecked")
        final AcceptingChannel<? extends SslConnection> server = xnioSsl.createSslConnectionServer(worker, serverAddress,
                openListener, OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE, Options.WORKER_NAME, "dummy"));
        assertNotNull(server);

        // try accepting to make sure the listener is invoked
        assertNotNull(server.accept());
        assertSame(server, openListener.getChannel());

        assertEquals(serverAddress, server.getLocalAddress());
        assertSame(worker, server.getWorker());
        assertTrue(server.getOption(Options.REUSE_ADDRESSES));
        assertEquals("dummy", server.getOption(Options.WORKER_NAME));
        assertNull(server.getOption(Options.BROADCAST));
    }

    @Test
    public void testCreateSslTcpServer() throws Exception {
        final TestChannelListener<AcceptingChannel<ConnectedSslStreamChannel>> openListener = new TestChannelListener<AcceptingChannel<ConnectedSslStreamChannel>>();
        // IDEA thinks this is unchecked because of http://youtrack.jetbrains.net/issue/IDEA-59290
        @SuppressWarnings("unchecked")
        final AcceptingChannel<? extends ConnectedStreamChannel> server = xnioSsl.createSslTcpServer(worker, serverAddress,
                openListener, OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE, Options.WORKER_NAME, "dummy"));
        assertNotNull(server);
        
        // try accepting to make sure the listener is correctly invoked
        final ConnectedStreamChannel channel = server.accept();
        assertNotNull(channel);
        assertSame(server, openListener.getChannel());

        assertSame(server, openListener.getChannel());
        assertEquals(serverAddress, server.getLocalAddress());
        assertSame(worker, server.getWorker());
        assertTrue(server.getOption(Options.REUSE_ADDRESSES));
        assertEquals("dummy", server.getOption(Options.WORKER_NAME));
        assertNull(server.getOption(Options.BROADCAST));
    }

    @Test
    public void testCreateSslConnectionServerWithDirectBuffers() throws Exception {
        //for (Provider p: Security.getProviders()) {
        //    System.out.println("Provider " + p + " - " +  p.getName());
        //}
        //final TestListener<ConnectedStreamChannel> listener = new TestListener<ConnectedStreamChannel>();
        xnioSsl = xnio.getSslProvider(OptionMap.create(Options.USE_DIRECT_BUFFERS, true));
        // IDEA thinks this is unchecked because of http://youtrack.jetbrains.net/issue/IDEA-59290
        @SuppressWarnings("unchecked")
        final AcceptingChannel<? extends SslConnection> server = xnioSsl.createSslConnectionServer(worker, serverAddress,
                null, OptionMap.create(Options.REUSE_ADDRESSES, Boolean.FALSE));
        assertNotNull(server);
        final StreamConnection connection = server.accept();
        assertNotNull(connection);
        // try calling the listener to make sure that openListener is invoked, i.e., testing that the openlistener is set correctly:
        assertEquals(serverAddress, server.getLocalAddress());
        assertSame(worker, server.getWorker());
        assertFalse(server.getOption(Options.REUSE_ADDRESSES));
        assertNull(server.getOption(Options.WORKER_NAME));
    }

    @Test
    public void testCreateSslTcpServerWithDirectBuffers() throws Exception {
        //for (Provider p: Security.getProviders()) {
        //    System.out.println("Provider " + p + " - " +  p.getName());
        //}
        //final TestListener<ConnectedStreamChannel> listener = new TestListener<ConnectedStreamChannel>();
        xnioSsl = xnio.getSslProvider(OptionMap.create(Options.USE_DIRECT_BUFFERS, true));
        // IDEA thinks this is unchecked because of http://youtrack.jetbrains.net/issue/IDEA-59290
        @SuppressWarnings("unchecked")
        final AcceptingChannel<? extends ConnectedStreamChannel> server = xnioSsl.createSslTcpServer(worker, serverAddress,
                null, OptionMap.create(Options.REUSE_ADDRESSES, Boolean.FALSE));
        assertNotNull(server);
        ConnectedStreamChannel channel = server.accept();
        assertNotNull(channel);
        // try calling the listener to make sure that openListener is invoked, i.e., testing that the openlistener is set correctly:
        assertEquals(serverAddress, server.getLocalAddress());
        assertSame(worker, server.getWorker());
        assertFalse(server.getOption(Options.REUSE_ADDRESSES));
        assertNull(server.getOption(Options.WORKER_NAME));
    }

    @Test
    public void connectSsl1() throws Exception {
        final TestChannelListener<StreamConnection> openListener = new TestChannelListener<StreamConnection>();
        final IoFuture<? extends StreamConnection> ioFuture = xnioSsl.openSslConnection(worker, serverAddress,
                openListener, OptionMap.EMPTY);
        final StreamConnection connection = ioFuture.get();
        assertNotNull(connection);
        assertSame(connection, openListener.getChannel());
        assertSame(worker, connection.getWorker());
        assertNotNull(connection.getLocalAddress());
        assertEquals(serverAddress, connection.getPeerAddress());
        assertNull(connection.getOption(Options.ALLOW_BLOCKING));
        assertNull(connection.getOption(Options.FILE_ACCESS));
        assertNull(connection.getOption(Options.RECEIVE_BUFFER));
        assertNull(connection.getOption(Options.STACK_SIZE));
    }

    @Test
    public void connectSsl2() throws Exception {
        final TestChannelListener<StreamConnection> openListener = new TestChannelListener<StreamConnection>();
        final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
        xnioSsl = xnio.getSslProvider(OptionMap.create(Options.SSL_PROTOCOL, "TLSv1.2")); 
        final IoFuture<? extends StreamConnection> ioFuture = xnioSsl.openSslConnection(worker, serverAddress,
                            openListener, bindListener, OptionMap.create(Options.KEEP_ALIVE, true));
        final StreamConnection connection = ioFuture.get();
        assertNotNull(connection);
        assertSame(connection, openListener.getChannel());
        final BoundChannel boundChannel = bindListener.getChannel();
        assertNotNull(boundChannel);
        assertSame(worker, connection.getWorker());
        assertNotNull(connection.getLocalAddress());
        assertSame(connection.getLocalAddress(), boundChannel.getLocalAddress());
        assertEquals(serverAddress, connection.getPeerAddress());
        assertTrue(connection.getOption(Options.KEEP_ALIVE));
        assertTrue(boundChannel.getOption(Options.KEEP_ALIVE));
        assertNull(connection.getOption(Options.ALLOW_BLOCKING));
        assertNull(boundChannel.getOption(Options.ALLOW_BLOCKING));
        assertNull(connection.getOption(Options.FILE_ACCESS));
        assertNull(boundChannel.getOption(Options.FILE_ACCESS));
        assertNull(connection.getOption(Options.RECEIVE_BUFFER));
        assertNull(boundChannel.getOption(Options.RECEIVE_BUFFER));
        assertNull(connection.getOption(Options.STACK_SIZE));
        assertNull(boundChannel.getOption(Options.STACK_SIZE));
    }

    @Test
    public void connectSsl3() throws Exception {
        final InetSocketAddress localAddress = new InetSocketAddress(500);
        final OptionMap.Builder builder = OptionMap.builder();
        builder.set(Options.SSL_PROTOCOL, "TLSv1");
        builder.set(Options.SSL_PROVIDER, "SunJSSE");
        builder.set(Options.SSL_CLIENT_SESSION_CACHE_SIZE, 20000);
        builder.set(Options.SSL_CLIENT_SESSION_TIMEOUT, 80);
        builder.set(Options.SSL_SERVER_SESSION_CACHE_SIZE, 30000);
        builder.set(Options.SSL_SERVER_SESSION_TIMEOUT, 130);
        final TestChannelListener<StreamConnection> openListener = new TestChannelListener<StreamConnection>();
        xnioSsl = xnio.getSslProvider(builder.getMap());
        final IoFuture<? extends StreamConnection> ioFuture = xnioSsl.openSslConnection(worker, localAddress ,
                serverAddress, openListener, OptionMap.create(Options.TCP_NODELAY, true));
        final StreamConnection connection = ioFuture.get();
        assertNotNull(connection);
        assertSame(connection, openListener.getChannel());
        assertSame(worker, connection.getWorker());
        assertEquals(localAddress, connection.getLocalAddress());
        assertEquals(serverAddress, connection.getPeerAddress());
        assertTrue(connection.getOption(Options.TCP_NODELAY));
    }

    @Test
    @Ignore
    public void connectSsl4() throws Exception {
        final InetSocketAddress localAddress = new InetSocketAddress(600);
        final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
        final TestChannelListener<StreamConnection> openListener = new TestChannelListener<StreamConnection>();
        final String[] enabledCipherSuites = new String[] {"SSL_DH_anon_WITH_RC4_128_MD5", "TLS_RSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_RC4_128_MD5", "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
                "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_3DES_EDE_CBC_SHA", "nothing"};
        final String[] validCipherSuites = new String[] {"SSL_DH_anon_WITH_RC4_128_MD5", "TLS_RSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_RC4_128_MD5", "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
                "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_3DES_EDE_CBC_SHA"};
        final String[] enabledProtocols = new String[] {"SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2", "inexistent"};
        final String[] validProtocols = new String[] {"SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"};
        final OptionMap.Builder optionMapBuilder = OptionMap.builder();
        optionMapBuilder.set(Options.SSL_ENABLE_SESSION_CREATION, false);
        optionMapBuilder.set(Options.SSL_ENABLED_CIPHER_SUITES, Sequence.of(enabledCipherSuites));
        optionMapBuilder.set(Options.MULTICAST, true);
        optionMapBuilder.set(Options.READ_TIMEOUT, 1000);
        optionMapBuilder.set(Options.SSL_ENABLED_PROTOCOLS, Sequence.of(enabledProtocols));
        final IoFuture<? extends StreamConnection> ioFuture = xnioSsl.openSslConnection(worker, localAddress,
                serverAddress, openListener, bindListener, optionMapBuilder.getMap());
        final StreamConnection connection = ioFuture.get();
        assertNotNull(connection);

        final BoundChannel boundChannel = bindListener.getChannel();
        assertNotNull(boundChannel);

        assertSame(connection, openListener.getChannel());
        assertSame(worker, connection.getWorker());
        assertEquals(localAddress, connection.getLocalAddress());
        assertEquals(localAddress, boundChannel.getLocalAddress());
        assertTrue(connection.getOption(Options.MULTICAST));
        assertTrue(boundChannel.getOption(Options.MULTICAST));
        assertEquals(1000, (int) connection.getOption(Options.READ_TIMEOUT));
        assertEquals(1000, (int) boundChannel.getOption(Options.READ_TIMEOUT));
    }

    @Test
    public void connectSslChannel1() throws Exception {
        final TestChannelListener<ConnectedSslStreamChannel> openListener = new TestChannelListener<ConnectedSslStreamChannel>();
        final IoFuture<? extends ConnectedStreamChannel> ioFuture = xnioSsl.connectSsl(worker, serverAddress,
                openListener, OptionMap.EMPTY);
        final ConnectedStreamChannel channel = ioFuture.get();
        assertNotNull(channel);
        assertSame(channel, openListener.getChannel());
        assertSame(worker, channel.getWorker());
        assertNotNull(channel.getLocalAddress());
        assertEquals(serverAddress, channel.getPeerAddress());
        assertNull(channel.getOption(Options.ALLOW_BLOCKING));
        assertNull(channel.getOption(Options.FILE_ACCESS));
        assertNull(channel.getOption(Options.RECEIVE_BUFFER));
        assertNull(channel.getOption(Options.STACK_SIZE));
    }

    @Test
    public void connectSslChannel2() throws Exception {
        final TestChannelListener<ConnectedSslStreamChannel> openListener = new TestChannelListener<ConnectedSslStreamChannel>();
        final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
        xnioSsl = xnio.getSslProvider(OptionMap.create(Options.SSL_PROTOCOL, "TLSv1.2")); 
        final IoFuture<? extends ConnectedStreamChannel> ioFuture = xnioSsl.connectSsl(worker, serverAddress,
                            openListener, bindListener, OptionMap.create(Options.KEEP_ALIVE, true));
        final ConnectedStreamChannel channel = ioFuture.get();
        assertNotNull(channel);
        assertSame(channel, openListener.getChannel());
        final BoundChannel boundChannel = bindListener.getChannel();
        assertNotNull(boundChannel);
        assertSame(worker, channel.getWorker());
        assertNotNull(channel.getLocalAddress());
        assertSame(channel.getLocalAddress(), boundChannel.getLocalAddress());
        assertEquals(serverAddress, channel.getPeerAddress());
        assertTrue(channel.getOption(Options.KEEP_ALIVE));
        assertTrue(boundChannel.getOption(Options.KEEP_ALIVE));
        assertNull(channel.getOption(Options.ALLOW_BLOCKING));
        assertNull(boundChannel.getOption(Options.ALLOW_BLOCKING));
        assertNull(channel.getOption(Options.FILE_ACCESS));
        assertNull(boundChannel.getOption(Options.FILE_ACCESS));
        assertNull(channel.getOption(Options.RECEIVE_BUFFER));
        assertNull(boundChannel.getOption(Options.RECEIVE_BUFFER));
        assertNull(channel.getOption(Options.STACK_SIZE));
        assertNull(boundChannel.getOption(Options.STACK_SIZE));
    }

    @Test
    public void connectSslChannel3() throws Exception {
        final InetSocketAddress localAddress = new InetSocketAddress(500);
        final OptionMap.Builder builder = OptionMap.builder();
        builder.set(Options.SSL_PROTOCOL, "TLSv1");
        builder.set(Options.SSL_PROVIDER, "SunJSSE");
        builder.set(Options.SSL_CLIENT_SESSION_CACHE_SIZE, 20000);
        builder.set(Options.SSL_CLIENT_SESSION_TIMEOUT, 80);
        builder.set(Options.SSL_SERVER_SESSION_CACHE_SIZE, 30000);
        builder.set(Options.SSL_SERVER_SESSION_TIMEOUT, 130);
        final TestChannelListener<ConnectedSslStreamChannel> openListener = new TestChannelListener<ConnectedSslStreamChannel>();
        xnioSsl = xnio.getSslProvider(builder.getMap());
        final IoFuture<? extends ConnectedStreamChannel> ioFuture = xnioSsl.connectSsl(worker, localAddress ,
                serverAddress, openListener, OptionMap.create(Options.TCP_NODELAY, true));
        final ConnectedStreamChannel channel = ioFuture.get();
        assertNotNull(channel);
        assertSame(channel, openListener.getChannel());
        assertSame(worker, channel.getWorker());
        assertEquals(localAddress, channel.getLocalAddress());
        assertEquals(serverAddress, channel.getPeerAddress());
        assertTrue(channel.getOption(Options.TCP_NODELAY));
    }

    @Test
    public void connectSslChannel4() throws Exception {
        final InetSocketAddress localAddress = new InetSocketAddress(600);
        final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
        final TestChannelListener<ConnectedSslStreamChannel> openListener = new TestChannelListener<ConnectedSslStreamChannel>();
        final String[] enabledCipherSuites = new String[] {"SSL_DH_anon_WITH_RC4_128_MD5", "TLS_RSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_RC4_128_MD5", "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
                "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_3DES_EDE_CBC_SHA", "nothing"};
        final String[] validCipherSuites = new String[] {"SSL_DH_anon_WITH_RC4_128_MD5", "TLS_RSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_RC4_128_MD5", "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
                "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_3DES_EDE_CBC_SHA"};
        final String[] enabledProtocols = new String[] {"SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2", "inexistent"};
        final String[] validProtocols = new String[] {"SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"};
        final OptionMap.Builder optionMapBuilder = OptionMap.builder();
        optionMapBuilder.set(Options.SSL_ENABLE_SESSION_CREATION, false);
        optionMapBuilder.set(Options.SSL_ENABLED_CIPHER_SUITES, Sequence.of(enabledCipherSuites));
        optionMapBuilder.set(Options.MULTICAST, true);
        optionMapBuilder.set(Options.READ_TIMEOUT, 1000);
        optionMapBuilder.set(Options.SSL_ENABLED_PROTOCOLS, Sequence.of(enabledProtocols));
        final IoFuture<? extends ConnectedStreamChannel> ioFuture = xnioSsl.connectSsl(worker, localAddress,
                serverAddress, openListener, bindListener, optionMapBuilder.getMap());
        final ConnectedStreamChannel channel = ioFuture.get();
        assertNotNull(channel);

        final BoundChannel boundChannel = bindListener.getChannel();
        assertNotNull(boundChannel);

        assertSame(channel, openListener.getChannel());
        assertSame(worker, channel.getWorker());
        assertEquals(localAddress, channel.getLocalAddress());
        assertEquals(localAddress, boundChannel.getLocalAddress());
        assertTrue(channel.getOption(Options.MULTICAST));
        assertTrue(boundChannel.getOption(Options.MULTICAST));
        assertEquals(1000, (int) channel.getOption(Options.READ_TIMEOUT));
        assertEquals(1000, (int) boundChannel.getOption(Options.READ_TIMEOUT));
    }

    @Test
    public void failedConnection() throws Exception {
        worker.failConnection();

        final IoFuture<? extends StreamConnection> connectionFuture = xnioSsl.openSslConnection(worker,
                new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT),
                            new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT),
                            null, null, OptionMap.EMPTY);
        boolean failed = false;
        try {
            connectionFuture.get();
        } catch (IOException e) {
            failed = true;
        }
        assertTrue(failed);

        failed = false;
        final IoFuture<? extends ConnectedStreamChannel> channelFuture = xnioSsl.connectSsl(worker,
                new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT),
                            new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT),
                            null, null, OptionMap.EMPTY);
        try {
            channelFuture.get();
        } catch (IOException e) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void cancelledConnection() throws Exception {
        worker.cancelConnection();

        final IoFuture<? extends StreamConnection> connectionFuture = xnioSsl.openSslConnection(worker,
                new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT),
                            new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT),
                            null, null, OptionMap.EMPTY);
        boolean cancelled = false;
        try {
            connectionFuture.get();
        } catch (CancellationException e) {
            cancelled = true;
        }
        assertTrue(cancelled);

        cancelled = false;
        final IoFuture<? extends ConnectedStreamChannel> channelFuture = xnioSsl.connectSsl(worker,
                new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT),
                            new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT),
                            null, null, OptionMap.EMPTY);
        try {
            channelFuture.get();
        } catch (CancellationException e) {
            cancelled = true;
        }
        assertTrue(cancelled);
    }

    @Test
    public void getSslContext() throws Exception {
        final Xnio xnio = Xnio.getInstance("xnio-mock", JsseXnioSslTestCase.class.getClassLoader());
        final JsseXnioSsl xnioSsl = (JsseXnioSsl) xnio.getSslProvider(OptionMap.EMPTY);
        SSLContext context = xnioSsl.getSslContext();
        assertNotNull(context);
    }

    private static class TestChannelListener<C extends Channel> implements ChannelListener<C> {

        private C channel;

        @Override
        public void handleEvent(C channel) {
            this.channel = channel;
        }
        
        public C getChannel() {
            return channel;
        }
    }
}
