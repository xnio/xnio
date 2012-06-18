/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.xnio.ssl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.LocalSocketAddress;
import org.xnio.OptionMap;
import org.xnio.OptionMap.Builder;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.Sequence;
import org.xnio.SslClientAuthMode;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.mock.AcceptingChannelMock;
import org.xnio.mock.ConnectedStreamChannelMock;
import org.xnio.ssl.mock.SSLContextMock;
import org.xnio.ssl.mock.SSLEngineMock;

/**
 * Test for {@link JsseAcceptingSslStreamChannel}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class JsseAcceptingSslStreamChannelTestCase {

    private Mockery context;
    private SSLEngineMock engineMock;
    private Pool<ByteBuffer> socketBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
    private Pool<ByteBuffer> applicationBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);

    @Before
    public void init() {
        context = new JUnit4Mockery();
        engineMock = new SSLEngineMock(context);
        socketBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
        applicationBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 17000, 17000 * 16);
    }

    @After
    public void setMockeryIsSatisfied() {
        context.assertIsSatisfied();
    }

    @Test
    public void testNullTcpServer() throws Exception {
        final OptionMap optionMap = OptionMap.create(Options.SSL_USE_CLIENT_MODE, true);
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(){
                    @Override
                    public ConnectedStreamChannelMock accept() throws IOException {
                        return null;
                    }
                }, optionMap, socketBufferPool, applicationBufferPool, true);
        assertNull(channel.accept());
    }

    @Test
    public void testClientMode() throws Exception {
        final OptionMap optionMap = OptionMap.create(Options.SSL_USE_CLIENT_MODE, true);
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), optionMap, socketBufferPool, applicationBufferPool, true);
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertTrue(engineMock.getUseClientMode());
        assertTrue(engineMock.getEnableSessionCreation());
        assertNull(engineMock.getEnabledCipherSuites());
        assertNull(engineMock.getEnabledProtocols());
    }

    @Test
    public void testClientModeSessionCreationEnabled() throws Exception {
        final OptionMap optionMap = OptionMap.create(Options.SSL_USE_CLIENT_MODE, true, Options.SSL_ENABLE_SESSION_CREATION, true);
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), optionMap, socketBufferPool, applicationBufferPool, true);
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertTrue(engineMock.getUseClientMode());
        assertTrue(engineMock.getEnableSessionCreation());
        assertNull(engineMock.getEnabledCipherSuites());
        assertNull(engineMock.getEnabledProtocols());
    }

    @Test
    public void testClientModeSessionCreationDisabled() throws Exception {
        final String[] enabledCypherSuites = new String[] {"TLS_EMPTY_RENEGOTIATION_INFO_SCSV", "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA"};
        final Builder optionMapBuilder = OptionMap.builder();
        optionMapBuilder.set(Options.SSL_USE_CLIENT_MODE, true);
        optionMapBuilder.set(Options.SSL_ENABLE_SESSION_CREATION, false);
        optionMapBuilder.set(Options.SSL_ENABLED_CIPHER_SUITES, Sequence.of(enabledCypherSuites));
        final OptionMap optionMap = optionMapBuilder.getMap();
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), optionMap, socketBufferPool, applicationBufferPool, true);
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertTrue(engineMock.getUseClientMode());
        assertFalse(engineMock.getEnableSessionCreation());
        assertEquals(0, engineMock.getEnabledCipherSuites().length);
        assertNull(engineMock.getEnabledProtocols());
    }

    @Test
    public void testServerModeDefaultAuthMode() throws Exception {
        final String[] enabledCipherSuites = new String[] {"TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA", " SSL_RSA_WITH_RC4_128_MD5", "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256"};
        final OptionMap optionMap = OptionMap.create(Options.SSL_ENABLED_CIPHER_SUITES, Sequence.of(enabledCipherSuites));
        engineMock.setSupportedCipherSuites("TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256", " TLS_EMPTY_RENEGOTIATION_INFO_SCSV", "TLS_KRB5_WITH_RC4_128_MD5");
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), optionMap, socketBufferPool, applicationBufferPool, true);
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertFalse(engineMock.getUseClientMode());
        assertFalse(engineMock.getNeedClientAuth());
        assertFalse(engineMock.getWantClientAuth());
        assertTrue(engineMock.getEnableSessionCreation());
        final String[] engineEnabledCipherSuites = engineMock.getEnabledCipherSuites();
        assertNotNull(engineEnabledCipherSuites);
        assertEquals(1, engineEnabledCipherSuites.length);
        assertEquals("TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256", engineEnabledCipherSuites[0]);
        assertNull(engineMock.getEnabledProtocols());
    }

    @Test
    public void testServerModeDefaultAuthMode_SessionCreationEnabled() throws Exception {
        final String[] enabledProtocols = new String[] {" TLSv1"};
        final OptionMap optionMap = OptionMap.create(Options.SSL_ENABLE_SESSION_CREATION, true, Options.SSL_ENABLED_PROTOCOLS, Sequence.of(enabledProtocols));
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), optionMap, socketBufferPool, applicationBufferPool, true);
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertFalse(engineMock.getUseClientMode());
        assertFalse(engineMock.getNeedClientAuth());
        assertFalse(engineMock.getWantClientAuth());
        assertTrue(engineMock.getEnableSessionCreation());
        assertNull(engineMock.getEnabledCipherSuites());
        assertEquals(0, engineMock.getEnabledProtocols().length);
    }

    @Test
    public void testServerModeDefaultAuthMode_SessionCreationDisabled() throws Exception {
        final String[] enabledProtocols = new String[] {" TLSv1.2", "SSLv2Hello"};
        engineMock.setSupportedProtocols("SSLv2Hello", "SSLv3");
        final OptionMap optionMap = OptionMap.create(Options.SSL_ENABLE_SESSION_CREATION, false, Options.SSL_ENABLED_PROTOCOLS, Sequence.of(enabledProtocols));
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), optionMap, socketBufferPool, applicationBufferPool, true);
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertFalse(engineMock.getUseClientMode());
        assertFalse(engineMock.getNeedClientAuth());
        assertFalse(engineMock.getWantClientAuth());
        assertFalse(engineMock.getEnableSessionCreation());
        assertNull(engineMock.getEnabledCipherSuites());
        final String[] engineEnabledProtocols = engineMock.getEnabledProtocols();
        assertNotNull(engineEnabledProtocols);
        assertEquals(1, engineEnabledProtocols.length);
        assertEquals("SSLv2Hello", engineEnabledProtocols[0]);
    }

    @Test
    public void testServerModeNotRequestedAuth() throws Exception {
        final String[] enabledCipherSuites = new String[] {"TLS_KRB5_WITH_3DES_EDE_CBC_MD5", "TLS_ECDH_anon_WITH_AES_128_CBC_SHA", " TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256"};
        final OptionMap optionMap = OptionMap.create(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.NOT_REQUESTED, Options.SSL_ENABLED_CIPHER_SUITES, Sequence.of(enabledCipherSuites));
        engineMock.setSupportedCipherSuites("SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_3DES_EDE_CBC_SHA");
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), optionMap, socketBufferPool, applicationBufferPool, true);
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertFalse(engineMock.getUseClientMode());
        assertFalse(engineMock.getNeedClientAuth());
        assertFalse(engineMock.getWantClientAuth());
        assertTrue(engineMock.getEnableSessionCreation());
        assertEquals(0, engineMock.getEnabledCipherSuites().length);
        assertNull(engineMock.getEnabledProtocols());
    }

    @Test
    public void testServerModeNotRequestedAuth_SessionCreationEnabled() throws Exception {
        final String[] enabledCipherSuites = new String[] {"SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA", "TLS_KRB5_WITH_3DES_EDE_CBC_SHA", "TLS_RSA_WITH_AES_128_CBC_SHA",
                "SSL_DHE_RSA_WITH_DES_CBC_SHA", "TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA"};
        final Builder optionMapBuilder = OptionMap.builder();
        optionMapBuilder.set(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.NOT_REQUESTED);
        optionMapBuilder.set(Options.SSL_ENABLE_SESSION_CREATION, true);
        optionMapBuilder.set(Options.SSL_ENABLED_CIPHER_SUITES, Sequence.of(enabledCipherSuites));
        final OptionMap optionMap = optionMapBuilder.getMap();
        engineMock.setSupportedCipherSuites("SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA", "TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA");
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), optionMap, socketBufferPool, applicationBufferPool, true);
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertFalse(engineMock.getUseClientMode());
        assertFalse(engineMock.getNeedClientAuth());
        assertFalse(engineMock.getWantClientAuth());
        assertTrue(engineMock.getEnableSessionCreation());
        final String[] engineEnabledCipherSuites = engineMock.getEnabledCipherSuites();
        assertNotNull(engineEnabledCipherSuites);
        assertArrayEquals(engineEnabledCipherSuites, engineMock.getSupportedCipherSuites());
        assertNull(engineMock.getEnabledProtocols());
    }

    @Test
    public void testServerModeNotRequestedAuth_SessionCreationDisabled() throws Exception {
        final String[] enabledProtocols = new String[] {"TLSv1", "TLSv1.1", "TLSv1.2"};
        final Builder optionMapBuilder = OptionMap.builder();
        optionMapBuilder.set(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.NOT_REQUESTED);
        optionMapBuilder.set(Options.SSL_ENABLE_SESSION_CREATION, false);
        optionMapBuilder.set(Options.SSL_ENABLED_PROTOCOLS, Sequence.of(enabledProtocols));
        final OptionMap optionMap = optionMapBuilder.getMap();
        engineMock.setSupportedProtocols("SSLv2Hello", "SSLv3");
        JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), optionMap, socketBufferPool, applicationBufferPool, true);
        ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertFalse(engineMock.getUseClientMode());
        assertFalse(engineMock.getNeedClientAuth());
        assertFalse(engineMock.getWantClientAuth());
        assertFalse(engineMock.getEnableSessionCreation());
        assertNull(engineMock.getEnabledCipherSuites());
        assertEquals(0, engineMock.getEnabledProtocols().length);
    }

    @Test
    public void testServerModeRequestedAuth() throws Exception {
        final String[] enabledProtocols = new String[] {"TLSv1", "TLSv1.1", "SSLv3"};
        final OptionMap optionMap = OptionMap.create(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.REQUESTED, Options.SSL_ENABLED_PROTOCOLS, Sequence.of(enabledProtocols));
        engineMock.setSupportedProtocols("TLSv1.1", "SSLv3");
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), optionMap, socketBufferPool, applicationBufferPool, true);
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertFalse(engineMock.getUseClientMode());
        assertFalse(engineMock.getNeedClientAuth());
        assertTrue(engineMock.getWantClientAuth());
        assertTrue(engineMock.getEnableSessionCreation());
        assertNull(engineMock.getEnabledCipherSuites());
        final String[] engineEnabledProtocols = engineMock.getEnabledProtocols();
        assertNotNull(engineEnabledProtocols);
        assertEquals(2, engineEnabledProtocols.length);
        assertArrayEquals(engineMock.getSupportedProtocols(), engineEnabledProtocols);
    }

    @Test
    public void testServerModeRequestedAuth_SessionCreationEnabled() throws Exception {
        final String[] enabledCipherSuites = new String[] {};
        final String[] enabledProtocols = new String[] {};
        final Builder optionMapBuilder = OptionMap.builder();
        optionMapBuilder.set(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.REQUESTED);
        optionMapBuilder.set(Options.SSL_ENABLE_SESSION_CREATION, true);
        optionMapBuilder.set(Options.SSL_ENABLED_CIPHER_SUITES, Sequence.of(enabledCipherSuites));
        optionMapBuilder.set(Options.SSL_ENABLED_PROTOCOLS, Sequence.of(enabledProtocols));
        final OptionMap optionMap = optionMapBuilder.getMap();
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), optionMap, socketBufferPool, applicationBufferPool, true);
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertFalse(engineMock.getUseClientMode());
        assertFalse(engineMock.getNeedClientAuth());
        assertTrue(engineMock.getWantClientAuth());
        assertTrue(engineMock.getEnableSessionCreation());
        assertEquals(0, engineMock.getEnabledCipherSuites().length);
        assertEquals(0, engineMock.getEnabledProtocols().length);
    }

    @Test
    public void testServerModeRequestedAuth_SessionCreationDisabled() throws Exception {
        final String[] enabledCipherSuites = new String[] {"TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256", "SSL_RSA_WITH_NULL_SHA",
                "TLS_ECDHE_RSA_WITH_NULL_SHA", "SSL_DH_anon_WITH_RC4_128_MD5", "TLS_RSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA"};
        final String[] enabledProtocols = new String[] {"SSLv3", "TLSv1"};
        final Builder optionMapBuilder = OptionMap.builder();
        optionMapBuilder.set(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.REQUESTED);
        optionMapBuilder.set(Options.SSL_ENABLE_SESSION_CREATION, false);
        optionMapBuilder.set(Options.SSL_ENABLED_CIPHER_SUITES, Sequence.of(enabledCipherSuites));
        optionMapBuilder.set(Options.SSL_ENABLED_PROTOCOLS, Sequence.of(enabledProtocols));
        final OptionMap optionMap = optionMapBuilder.getMap();
        engineMock.setSupportedCipherSuites("SSL_DH_anon_WITH_3DES_EDE_CBC_SHA", "TLS_RSA_WITH_AES_128_CBC_SHA",
                "SSL_DHE_RSA_WITH_DES_CBC_SHA", "TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA", "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
                "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256", "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256", "SSL_RSA_WITH_NULL_SHA",
                "TLS_ECDHE_RSA_WITH_NULL_SHA");
        engineMock.setSupportedProtocols("TLSv1.1", "TLSv1.2");
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), optionMap, socketBufferPool, applicationBufferPool, true);
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertFalse(engineMock.getUseClientMode());
        assertFalse(engineMock.getNeedClientAuth());
        assertTrue(engineMock.getWantClientAuth());
        assertFalse(engineMock.getEnableSessionCreation());
        final String[] engineEnabledCipherSuites = engineMock.getEnabledCipherSuites();
        assertNotNull(engineEnabledCipherSuites);
        assertEquals(3, engineEnabledCipherSuites.length);
        assertEquals("TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256", engineEnabledCipherSuites[0]);
        assertEquals("SSL_RSA_WITH_NULL_SHA", engineEnabledCipherSuites[1]);
        assertEquals("TLS_ECDHE_RSA_WITH_NULL_SHA", engineEnabledCipherSuites[2]);
        assertEquals(0, engineMock.getEnabledProtocols().length);
    }

    @Test
    public void testServerModeRequiredAuth() throws Exception {
        final String[] enabledCipherSuites = new String[] {"SSL_DH_anon_WITH_RC4_128_MD5", "TLS_RSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_RC4_128_MD5", "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
                "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_3DES_EDE_CBC_SHA"};
        final String[] enabledProtocols = new String[] {"SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"};
        final Builder optionMapBuilder = OptionMap.builder();
        optionMapBuilder.set(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.REQUIRED);
        optionMapBuilder.set(Options.SSL_ENABLED_CIPHER_SUITES, Sequence.of(enabledCipherSuites));
        optionMapBuilder.set(Options.SSL_ENABLED_PROTOCOLS, Sequence.of(enabledProtocols));
        final OptionMap optionMap = optionMapBuilder.getMap();
        engineMock.setSupportedCipherSuites("SSL_DH_anon_WITH_3DES_EDE_CBC_SHA", "TLS_RSA_WITH_AES_128_CBC_SHA",
                "SSL_DHE_RSA_WITH_DES_CBC_SHA", "TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA", "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
                "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256", "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256", "SSL_RSA_WITH_NULL_SHA",
                "TLS_ECDHE_RSA_WITH_NULL_SHA");
        engineMock.setSupportedProtocols("SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1");
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), optionMap, socketBufferPool, applicationBufferPool, true);
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertFalse(engineMock.getUseClientMode());
        assertTrue(engineMock.getNeedClientAuth());
        assertFalse(engineMock.getWantClientAuth());
        assertTrue(engineMock.getEnableSessionCreation());
        assertEquals(0, engineMock.getEnabledCipherSuites().length);
        final String[] engineEnabledProtocols = engineMock.getEnabledProtocols();
        assertNotNull(engineEnabledProtocols);
        assertEquals(3, engineEnabledProtocols.length);
        assertEquals("SSLv3", engineEnabledProtocols[0]);
        assertEquals("TLSv1", engineEnabledProtocols[1]);
        assertEquals("TLSv1.1", engineEnabledProtocols[2]);
    }

    @Test
    public void testServerModeRequiredAuth_SessionCreationEnabled() throws Exception {
        final String[] enabledCipherSuites = new String[] {"TLS_ECDH_anon_WITH_RC4_128_SHA"};
        final String[] enabledProtocols = new String[] {"SSLv3"};
        final Builder optionMapBuilder = OptionMap.builder();
        optionMapBuilder.set(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.REQUIRED);
        optionMapBuilder.set(Options.SSL_ENABLE_SESSION_CREATION, true);
        optionMapBuilder.set(Options.SSL_ENABLED_CIPHER_SUITES, Sequence.of(enabledCipherSuites));
        optionMapBuilder.set(Options.SSL_ENABLED_PROTOCOLS, Sequence.of(enabledProtocols));
        final OptionMap optionMap = optionMapBuilder.getMap();
        engineMock.setSupportedCipherSuites("TLS_ECDH_ECDSA_WITH_NULL_SHA", "TLS_DH_anon_WITH_AES_128_CBC_SHA256",
                "TLS_ECDH_anon_WITH_RC4_128_SHA", "TLS_DH_anon_WITH_AES_128_CBC_SHA", "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
                "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA", "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA", "TLS_KRB5_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_RC4_128_SHA",
                "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256", "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
                "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256", "TLS_KRB5_EXPORT_WITH_RC4_40_SHA",
                "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256","TLS_ECDHE_RSA_WITH_RC4_128_SHA",
                "TLS_ECDH_ECDSA_WITH_RC4_128_SHA", "TLS_ECDH_anon_WITH_NULL_SHA", "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA", "TLS_RSA_WITH_NULL_SHA256", "TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA",
                "TLS_KRB5_WITH_RC4_128_MD5", "SSL_RSA_WITH_DES_CBC_SHA", "TLS_ECDHE_ECDSA_WITH_NULL_SHA",
                "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA", "TLS_ECDH_RSA_WITH_RC4_128_SHA",
                "TLS_EMPTY_RENEGOTIATION_INFO_SCSV", "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                "TLS_KRB5_WITH_3DES_EDE_CBC_MD5", "TLS_KRB5_WITH_RC4_128_SHA", "SSL_DH_anon_WITH_DES_CBC_SHA",
                "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA", "TLS_ECDH_RSA_WITH_NULL_SHA", "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
                "SSL_RSA_EXPORT_WITH_RC4_40_MD5", "TLS_KRB5_WITH_DES_CBC_MD5", "TLS_KRB5_EXPORT_WITH_RC4_40_MD5",
                "TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5", "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",
                "TLS_ECDH_anon_WITH_AES_128_CBC_SHA", "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
                "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5", "SSL_DHE_DSS_WITH_DES_CBC_SHA",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256", "TLS_KRB5_WITH_DES_CBC_SHA", "SSL_RSA_WITH_NULL_MD5",
                "SSL_DH_anon_WITH_3DES_EDE_CBC_SHA", "TLS_RSA_WITH_AES_128_CBC_SHA", "SSL_DHE_RSA_WITH_DES_CBC_SHA",
                "TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA", "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
                "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256", "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
                "SSL_RSA_WITH_NULL_SHA", "TLS_ECDHE_RSA_WITH_NULL_SHA", "SSL_DH_anon_WITH_RC4_128_MD5",
                "TLS_RSA_WITH_AES_128_CBC_SHA256", "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_RC4_128_MD5",
                "TLS_DHE_DSS_WITH_AES_128_CBC_SHA", "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_3DES_EDE_CBC_SHA");
        engineMock.setSupportedProtocols("SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2");
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), optionMap, socketBufferPool, applicationBufferPool, true);
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertFalse(engineMock.getUseClientMode());
        assertTrue(engineMock.getNeedClientAuth());
        assertFalse(engineMock.getWantClientAuth());
        assertTrue(engineMock.getEnableSessionCreation());
        final String[] engineEnabledCipherSuites = engineMock.getEnabledCipherSuites();
        assertNotNull(engineEnabledCipherSuites);
        assertArrayEquals(engineEnabledCipherSuites, enabledCipherSuites);
        final String[] engineEnabledProtocols = engineMock.getEnabledProtocols();
        assertNotNull(engineEnabledProtocols);
        assertArrayEquals(engineEnabledProtocols, enabledProtocols);
    }

    @Test
    public void testServerModeRequiredAuth_SessionCreationDisabled() throws Exception {
        final String[] enabledCipherSuites = new String[] {"SSL_RSA_EXPORT_WITH_DES40_CBC_SHA", "invalid", "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256"};
        final String[] enabledProtocols = new String[] {"in", "valid", "SSLv3", "TLSv1.1"};
        final Builder optionMapBuilder = OptionMap.builder();
        optionMapBuilder.set(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.REQUIRED);
        optionMapBuilder.set(Options.SSL_ENABLE_SESSION_CREATION, false);
        optionMapBuilder.set(Options.SSL_ENABLED_CIPHER_SUITES, Sequence.of(enabledCipherSuites));
        optionMapBuilder.set(Options.SSL_ENABLED_PROTOCOLS, Sequence.of(enabledProtocols));
        final OptionMap optionMap = optionMapBuilder.getMap();
        engineMock.setSupportedCipherSuites("TLS_ECDH_ECDSA_WITH_NULL_SHA", "TLS_DH_anon_WITH_AES_128_CBC_SHA256",
                "TLS_ECDH_anon_WITH_RC4_128_SHA", "TLS_DH_anon_WITH_AES_128_CBC_SHA", "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
                "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA", "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA", "TLS_KRB5_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_RC4_128_SHA",
                "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256", "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
                "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256", "TLS_KRB5_EXPORT_WITH_RC4_40_SHA",
                "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256","TLS_ECDHE_RSA_WITH_RC4_128_SHA",
                "TLS_ECDH_ECDSA_WITH_RC4_128_SHA", "TLS_ECDH_anon_WITH_NULL_SHA", "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA", "TLS_RSA_WITH_NULL_SHA256", "TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA",
                "TLS_KRB5_WITH_RC4_128_MD5", "SSL_RSA_WITH_DES_CBC_SHA", "TLS_ECDHE_ECDSA_WITH_NULL_SHA",
                "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA", "TLS_ECDH_RSA_WITH_RC4_128_SHA",
                "TLS_EMPTY_RENEGOTIATION_INFO_SCSV", "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                "TLS_KRB5_WITH_3DES_EDE_CBC_MD5", "TLS_KRB5_WITH_RC4_128_SHA", "SSL_DH_anon_WITH_DES_CBC_SHA",
                "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA", "TLS_ECDH_RSA_WITH_NULL_SHA", "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
                "SSL_RSA_EXPORT_WITH_RC4_40_MD5", "TLS_KRB5_WITH_DES_CBC_MD5", "TLS_KRB5_EXPORT_WITH_RC4_40_MD5",
                "TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5", "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",
                "TLS_ECDH_anon_WITH_AES_128_CBC_SHA", "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
                "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5", "SSL_DHE_DSS_WITH_DES_CBC_SHA",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256", "TLS_KRB5_WITH_DES_CBC_SHA", "SSL_RSA_WITH_NULL_MD5",
                "SSL_DH_anon_WITH_3DES_EDE_CBC_SHA", "TLS_RSA_WITH_AES_128_CBC_SHA", "SSL_DHE_RSA_WITH_DES_CBC_SHA",
                "TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA", "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
                "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256", "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
                "SSL_RSA_WITH_NULL_SHA", "TLS_ECDHE_RSA_WITH_NULL_SHA", "SSL_DH_anon_WITH_RC4_128_MD5",
                "TLS_RSA_WITH_AES_128_CBC_SHA256", "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_RC4_128_MD5",
                "TLS_DHE_DSS_WITH_AES_128_CBC_SHA", "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_3DES_EDE_CBC_SHA");
        engineMock.setSupportedProtocols("SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2");
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), optionMap, socketBufferPool, applicationBufferPool, true);
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertFalse(engineMock.getUseClientMode());
        assertTrue(engineMock.getNeedClientAuth());
        assertFalse(engineMock.getWantClientAuth());
        assertFalse(engineMock.getEnableSessionCreation());
        final String[] engineEnabledCipherSuites = engineMock.getEnabledCipherSuites();
        assertNotNull(engineEnabledCipherSuites);
        assertEquals(2, engineEnabledCipherSuites.length);
        assertEquals("SSL_RSA_EXPORT_WITH_DES40_CBC_SHA", engineEnabledCipherSuites[0]);
        assertEquals("TLS_DHE_RSA_WITH_AES_128_CBC_SHA256", engineEnabledCipherSuites[1]);
        final String[] engineEnabledProtocols = engineMock.getEnabledProtocols();
        assertNotNull(engineEnabledProtocols);
        assertEquals(2, engineEnabledProtocols.length);
        assertEquals("SSLv3", engineEnabledProtocols[0]);
        assertEquals("TLSv1.1", engineEnabledProtocols[1]);
    }

    @Test
    public void setClientAuthModeOption() throws Exception {
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), OptionMap.EMPTY, socketBufferPool, applicationBufferPool, true);
        // set SSL_CLIENT_AUTH_MODE option to REQUESTED
        assertNull(channel.getOption(Options.SSL_CLIENT_AUTH_MODE));
        assertNull(channel.setOption(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.NOT_REQUESTED));
        assertEquals(SslClientAuthMode.NOT_REQUESTED, channel.getOption(Options.SSL_CLIENT_AUTH_MODE));
        assertEquals(SslClientAuthMode.NOT_REQUESTED, channel.setOption(Options.SSL_CLIENT_AUTH_MODE, null));
        assertNull(channel.getOption(Options.SSL_CLIENT_AUTH_MODE));
        assertNull(channel.setOption(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.REQUESTED));
        assertEquals(SslClientAuthMode.REQUESTED, channel.getOption(Options.SSL_CLIENT_AUTH_MODE));
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertFalse(engineMock.getUseClientMode());
        assertTrue(engineMock.getWantClientAuth());
        assertTrue(engineMock.getEnableSessionCreation());
        assertNull(engineMock.getEnabledCipherSuites());
        assertNull(engineMock.getEnabledProtocols());
    }

    @Test
    public void setUseClientModeOption() throws Exception {
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), OptionMap.EMPTY, socketBufferPool, applicationBufferPool, true);
        // set SSL_USE_CLIENT_MODE option to true
        assertFalse(channel.getOption(Options.SSL_USE_CLIENT_MODE));
        assertFalse(channel.setOption(Options.SSL_USE_CLIENT_MODE, true));
        assertTrue(channel.getOption(Options.SSL_USE_CLIENT_MODE));
        assertTrue(channel.setOption(Options.SSL_USE_CLIENT_MODE, false));
        assertFalse(channel.getOption(Options.SSL_USE_CLIENT_MODE));
        assertFalse(channel.setOption(Options.SSL_USE_CLIENT_MODE, true));
        assertTrue(channel.getOption(Options.SSL_USE_CLIENT_MODE));
        boolean illegalArgument = false;
        try {
            channel.setOption(Options.SSL_USE_CLIENT_MODE, null);
        } catch (IllegalArgumentException e) {
            illegalArgument = true;
        }
        assertTrue(illegalArgument);
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertTrue(engineMock.getUseClientMode());
        assertTrue(engineMock.getEnableSessionCreation());
        assertNull(engineMock.getEnabledCipherSuites());
        assertNull(engineMock.getEnabledProtocols());
    }

    @Test
    public void setEnableSessionCreationOption() throws Exception {
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), OptionMap.EMPTY, socketBufferPool, applicationBufferPool, true);
        // set SSL_ENABLE_SESSION_CREATION option to false
        assertTrue(channel.getOption(Options.SSL_ENABLE_SESSION_CREATION));
        assertTrue(channel.setOption(Options.SSL_ENABLE_SESSION_CREATION, false));
        assertFalse(channel.getOption(Options.SSL_ENABLE_SESSION_CREATION));
        assertFalse(channel.setOption(Options.SSL_ENABLE_SESSION_CREATION, true));
        assertTrue(channel.getOption(Options.SSL_ENABLE_SESSION_CREATION));
        assertTrue(channel.setOption(Options.SSL_ENABLE_SESSION_CREATION, false));
        assertFalse(channel.getOption(Options.SSL_ENABLE_SESSION_CREATION));
        boolean illegalArgument = false;
        try {
            channel.setOption(Options.SSL_ENABLE_SESSION_CREATION, null);
        } catch (IllegalArgumentException e) {
            illegalArgument = true;
        }
        assertTrue(illegalArgument);
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertFalse(engineMock.getUseClientMode());
        assertFalse(engineMock.getEnableSessionCreation());
        assertNull(engineMock.getEnabledCipherSuites());
        assertNull(engineMock.getEnabledProtocols());
    }

    @Test
    public void setEnabledCipherSuitesOption() throws Exception {
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), OptionMap.EMPTY, socketBufferPool, applicationBufferPool, true);
        // set SSL_ENABLED_CIPHER_SUITES option to to TLS_ECDH_anon_WITH_NULL_SHA
        Sequence<String> enabledCipherSuites = Sequence.of("TLS_ECDH_anon_WITH_NULL_SHA");
        assertNull(channel.getOption(Options.SSL_ENABLED_CIPHER_SUITES));
        assertNull(channel.setOption(Options.SSL_ENABLED_CIPHER_SUITES, enabledCipherSuites));
        assertEquals(enabledCipherSuites, channel.getOption(Options.SSL_ENABLED_CIPHER_SUITES));
        assertEquals(enabledCipherSuites, channel.setOption(Options.SSL_ENABLED_CIPHER_SUITES, null));
        assertNull(channel.getOption(Options.SSL_ENABLED_CIPHER_SUITES));
        assertNull(channel.setOption(Options.SSL_ENABLED_CIPHER_SUITES, enabledCipherSuites));
        assertEquals(enabledCipherSuites, channel.getOption(Options.SSL_ENABLED_CIPHER_SUITES));
        engineMock.setSupportedCipherSuites("TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256", "TLS_ECDH_anon_WITH_NULL_SHA");
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertFalse(engineMock.getUseClientMode());
        assertTrue(engineMock.getEnableSessionCreation());
        final String[] engineEnabledCipherSuites = engineMock.getEnabledCipherSuites();
        assertNotNull(engineEnabledCipherSuites);
        assertEquals(1, engineEnabledCipherSuites.length);
        assertEquals("TLS_ECDH_anon_WITH_NULL_SHA", engineEnabledCipherSuites[0]);
        assertNull(engineMock.getEnabledProtocols());
    }

    @Test
    public void setEnabledProtocolsOption() throws Exception {
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
               new AcceptingChannelMock(), OptionMap.EMPTY, socketBufferPool, applicationBufferPool, true);
        // set SSL_ENABLED_PROTOCOLS to TLSv1
        Sequence<String> enabledProtocols = Sequence.of("TLSv1");
        assertNull(channel.getOption(Options.SSL_ENABLED_PROTOCOLS));
        assertNull(channel.setOption(Options.SSL_ENABLED_PROTOCOLS, enabledProtocols));
        assertEquals(enabledProtocols, channel.getOption(Options.SSL_ENABLED_PROTOCOLS));
        assertEquals(enabledProtocols, channel.setOption(Options.SSL_ENABLED_PROTOCOLS, null));
        assertNull(channel.getOption(Options.SSL_ENABLED_PROTOCOLS));
        assertNull(channel.setOption(Options.SSL_ENABLED_PROTOCOLS, enabledProtocols));
        assertEquals(enabledProtocols, channel.getOption(Options.SSL_ENABLED_PROTOCOLS));
        engineMock.setSupportedProtocols("SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2");
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertFalse(engineMock.getUseClientMode());
        assertTrue(engineMock.getEnableSessionCreation());
        assertNull(engineMock.getEnabledCipherSuites());
        final String[] engineEnabledProtocols = engineMock.getEnabledProtocols();
        assertNotNull(engineEnabledProtocols);
        assertEquals(1, engineEnabledProtocols.length);
        assertEquals("TLSv1", engineEnabledProtocols[0]);
    }

    @Test
    public void setTcpServerOption() throws Exception {
        AcceptingChannelMock acceptingChannelMock = new AcceptingChannelMock();
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                acceptingChannelMock, OptionMap.EMPTY, socketBufferPool, applicationBufferPool, true);
        // set KEEP_ALIVE to true (plus: assert that channel is delegating this task to accepting channel mock)
        assertNull(channel.getOption(Options.KEEP_ALIVE));
        assertNull(channel.setOption(Options.KEEP_ALIVE, false));
        assertFalse(channel.getOption(Options.KEEP_ALIVE));
        assertFalse(channel.setOption(Options.KEEP_ALIVE, true));
        assertTrue(channel.getOption(Options.KEEP_ALIVE));

        // set SSL_STARTTLS to true (plus: assert that channel is delegating this task to accepting channel mock)
        assertNull(channel.getOption(Options.SSL_STARTTLS));
        assertNull(channel.setOption(Options.SSL_STARTTLS, false));
        assertFalse(channel.getOption(Options.SSL_STARTTLS));
        assertFalse(channel.setOption(Options.SSL_STARTTLS, true));
        assertTrue(channel.getOption(Options.SSL_STARTTLS));
        final ConnectedSslStreamChannel c = channel.accept();
        assertNotNull(c);
        assertFalse(engineMock.getUseClientMode());
        assertTrue(engineMock.getEnableSessionCreation());
        assertNull(engineMock.getEnabledCipherSuites());
        assertNull(engineMock.getEnabledProtocols());
    }

    @Test
    public void setNullOption() throws Exception {
        AcceptingChannelMock acceptingChannelMock = new AcceptingChannelMock();
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                acceptingChannelMock, OptionMap.EMPTY, socketBufferPool, applicationBufferPool, true);
        Exception expected = null;
        try {
            channel.setOption(null, true);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            channel.getOption(null);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void supportsOption() throws Exception {
        AcceptingChannelMock acceptingChannelMock = new AcceptingChannelMock();
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                acceptingChannelMock, OptionMap.EMPTY, socketBufferPool, applicationBufferPool, false);
        acceptingChannelMock.setSupportedOptions(Options.MAX_INBOUND_MESSAGE_SIZE, Options.MAX_OUTBOUND_MESSAGE_SIZE);
        assertTrue(channel.supportsOption(Options.SSL_USE_CLIENT_MODE));
        assertTrue(channel.supportsOption(Options.SSL_CLIENT_AUTH_MODE));
        assertTrue(channel.supportsOption(Options.SSL_ENABLE_SESSION_CREATION));
        assertTrue(channel.supportsOption(Options.SSL_ENABLED_CIPHER_SUITES));
        assertTrue(channel.supportsOption(Options.SSL_ENABLED_PROTOCOLS));
        assertTrue(channel.supportsOption(Options.MAX_INBOUND_MESSAGE_SIZE));
        assertTrue(channel.supportsOption(Options.MAX_OUTBOUND_MESSAGE_SIZE));
        assertFalse(channel.supportsOption(Options.KEEP_ALIVE));
        assertFalse(channel.supportsOption(Options.BROADCAST));
        assertFalse(channel.supportsOption(Options.SASL_MECHANISMS));
    }

    @Test
    public void close() throws Exception {
        AcceptingChannelMock acceptingChannelMock = new AcceptingChannelMock();
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                acceptingChannelMock, OptionMap.EMPTY, socketBufferPool, applicationBufferPool, false);
        assertTrue(acceptingChannelMock.isOpen());
        assertTrue(channel.isOpen());
        channel.close();
        assertFalse(acceptingChannelMock.isOpen());
        assertFalse(channel.isOpen());
    }

    @Test
    public void getLocalAddress() throws Exception {
        AcceptingChannelMock acceptingChannelMock = new AcceptingChannelMock();
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                acceptingChannelMock, OptionMap.EMPTY, socketBufferPool, applicationBufferPool, false);
        SocketAddress localAddress = new InetSocketAddress(12345);
        acceptingChannelMock.setLocalAddress(localAddress);
        assertSame(localAddress, channel.getLocalAddress());
        assertSame(localAddress, channel.getLocalAddress(SocketAddress.class));
        assertSame(localAddress, channel.getLocalAddress(InetSocketAddress.class));
        assertNull(channel.getLocalAddress(LocalSocketAddress.class));
        localAddress = new LocalSocketAddress("12345");
        acceptingChannelMock.setLocalAddress(localAddress);
        assertSame(localAddress, channel.getLocalAddress());
        assertSame(localAddress, channel.getLocalAddress(SocketAddress.class));
        assertSame(localAddress, channel.getLocalAddress(LocalSocketAddress.class));
        assertNull(channel.getLocalAddress(InetSocketAddress.class));
    }

    @Test
    public void getListenerSetters() throws Exception {
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                new AcceptingChannelMock(), OptionMap.EMPTY, socketBufferPool, applicationBufferPool, false);
        // make sure setters are non null, and always the same, to make sure that the listener set on a setter can be
        // read later by calling the getSetter again
        assertNotNull(channel.getAcceptSetter());
        assertSame(channel.getAcceptSetter(), channel.getAcceptSetter());
        assertNotNull(channel.getCloseSetter());
        assertSame(channel.getCloseSetter(), channel.getCloseSetter());
    }

    @Test
    public void suspendResume() throws Exception {
        AcceptingChannelMock acceptingChannelMock = new AcceptingChannelMock();
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                acceptingChannelMock, OptionMap.EMPTY, socketBufferPool, applicationBufferPool, false);
        // make sure all suspend/resume/wakeup actions are simply delegated to underlying channel
        channel.suspendAccepts();
        assertFalse(acceptingChannelMock.isAcceptResumed());
        channel.resumeAccepts();
        assertTrue(acceptingChannelMock.isAcceptResumed());
        channel.suspendAccepts();
        assertFalse(acceptingChannelMock.isAcceptResumed());
        channel.wakeupAccepts();
        assertTrue(acceptingChannelMock.isAcceptWokenUp());
    }

    @Test
    public void awaitAcceptable() throws Exception {
        AcceptingChannelMock acceptingChannelMock = new AcceptingChannelMock();
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                acceptingChannelMock, OptionMap.EMPTY, socketBufferPool, applicationBufferPool, false);
        // make sure all await actions are simply delegated to underlying channel
        assertFalse(acceptingChannelMock.haveWaitedAcceptable());
        channel.awaitAcceptable();
        assertTrue(acceptingChannelMock.haveWaitedAcceptable());
        acceptingChannelMock.clearWaitedAcceptable();
        channel.awaitAcceptable(100, TimeUnit.MILLISECONDS);
        assertTrue(acceptingChannelMock.haveWaitedAcceptable());
        assertEquals(100, acceptingChannelMock.getAwaitAcceptableTime());
        assertSame(TimeUnit.MILLISECONDS, acceptingChannelMock.getAwaitAcceptableTimeUnit());
        acceptingChannelMock.clearWaitedAcceptable();
        channel.awaitAcceptable(10, TimeUnit.MICROSECONDS);
        assertTrue(acceptingChannelMock.haveWaitedAcceptable());
        assertEquals(10, acceptingChannelMock.getAwaitAcceptableTime());
        assertSame(TimeUnit.MICROSECONDS, acceptingChannelMock.getAwaitAcceptableTimeUnit());
    }

    @Test
    public void getWorker() throws Exception {
        AcceptingChannelMock acceptingChannelMock = new AcceptingChannelMock();
        final JsseAcceptingSslStreamChannel channel = new JsseAcceptingSslStreamChannel(new SSLContextMock(engineMock),
                acceptingChannelMock, OptionMap.EMPTY, socketBufferPool, applicationBufferPool, false);
        // getWorker should also simply delegate to acceptingChannelMocks
        assertNull(channel.getWorker());
        XnioWorker worker = Xnio.getInstance().createWorker(OptionMap.EMPTY);
        acceptingChannelMock.setWorker(worker);
        assertSame(worker, channel.getWorker());
    }
}
