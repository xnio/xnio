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
package org.xnio.nio.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.junit.Test;
import org.xnio.FileAccess;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Property;
import org.xnio.Sequence;
import org.xnio.SslClientAuthMode;
import org.xnio.XnioWorker;
import org.xnio.sasl.SaslQop;
import org.xnio.sasl.SaslStrength;

/**
 * Test for setting connection options at the TCP server.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class ConnectionOptionSetupTestCase extends TcpServerTest {

    @Test
    public void defaultOptionValues() throws IOException {
        createServer(OptionMap.EMPTY);

        assertNotNull(server);

        assertTrue(server.getOption(Options.REUSE_ADDRESSES));
        assertTrue((int) server.getOption(Options.RECEIVE_BUFFER) > 0);
        assertEquals(0x10000, (int) server.getOption(Options.SEND_BUFFER));
        assertFalse(server.getOption(Options.KEEP_ALIVE));
        assertFalse(server.getOption(Options.TCP_OOB_INLINE));
        assertFalse(server.getOption(Options.TCP_NODELAY));
        assertEquals(0, (int) server.getOption(Options.READ_TIMEOUT));
        assertEquals(0, (int) server.getOption(Options.WRITE_TIMEOUT));
        assertTrue((int) server.getOption(Options.CONNECTION_LOW_WATER) > 0);
        assertTrue((int) server.getOption(Options.CONNECTION_HIGH_WATER) > 0);
        assertTrue((int) server.getOption(Options.CONNECTION_LOW_WATER) <= server.getOption(Options.CONNECTION_HIGH_WATER));
        server.close();
    }

    @Test
    public void optionSetupOnServerCreation() throws IOException {
        final OptionMap.Builder optionMapBuilder = OptionMap.builder();
        optionMapBuilder.set(Options.WORKER_ACCEPT_THREADS, getWorkerWriteThreads());
        optionMapBuilder.set(Options.WORKER_ESTABLISH_WRITING, true);
        optionMapBuilder.set(Options.REUSE_ADDRESSES, true);
        optionMapBuilder.set(Options.RECEIVE_BUFFER, 50000);
        optionMapBuilder.set(Options.SEND_BUFFER, 49999);
        optionMapBuilder.set(Options.KEEP_ALIVE, true);
        optionMapBuilder.set(Options.TCP_OOB_INLINE, true);
        optionMapBuilder.set(Options.TCP_NODELAY, true);
        optionMapBuilder.set(Options.READ_TIMEOUT, 300000);
        optionMapBuilder.set(Options.WRITE_TIMEOUT, 250000);
        optionMapBuilder.set(Options.CONNECTION_LOW_WATER, 12345);
        optionMapBuilder.set(Options.CONNECTION_HIGH_WATER, 23456);

        createServer(optionMapBuilder.getMap());

        assertTrue(server.getOption(Options.REUSE_ADDRESSES));
        assertEquals(50000, (int) server.getOption(Options.RECEIVE_BUFFER));
        assertEquals(49999, (int) server.getOption(Options.SEND_BUFFER));
        assertTrue(server.getOption(Options.KEEP_ALIVE));
        assertTrue(server.getOption(Options.TCP_OOB_INLINE));
        assertTrue(server.getOption(Options.TCP_NODELAY));
        assertEquals(300000, (int) server.getOption(Options.READ_TIMEOUT));
        assertEquals(250000, (int) server.getOption(Options.WRITE_TIMEOUT));
        assertEquals(12345, (int) server.getOption(Options.CONNECTION_LOW_WATER));
        assertEquals(23456, (int) server.getOption(Options.CONNECTION_HIGH_WATER));
        server.close();
    }

    @Test
    public void resetOptions() throws IOException {
        final OptionMap.Builder optionMapBuilder = OptionMap.builder();
        optionMapBuilder.set(Options.WORKER_ACCEPT_THREADS, getWorkerWriteThreads());
        optionMapBuilder.set(Options.WORKER_ESTABLISH_WRITING, true);
        optionMapBuilder.set(Options.REUSE_ADDRESSES, true);
        optionMapBuilder.set(Options.RECEIVE_BUFFER, 10);
        optionMapBuilder.set(Options.SEND_BUFFER, 1000000);
        optionMapBuilder.set(Options.KEEP_ALIVE, true);
        optionMapBuilder.set(Options.TCP_OOB_INLINE, true);
        optionMapBuilder.set(Options.TCP_NODELAY, true);
        optionMapBuilder.set(Options.READ_TIMEOUT, 1000000);
        optionMapBuilder.set(Options.WRITE_TIMEOUT, 1000000);
        optionMapBuilder.set(Options.CONNECTION_HIGH_WATER, 1000000);
        optionMapBuilder.set(Options.CONNECTION_LOW_WATER, 1000000);

        createServer(optionMapBuilder.getMap());

        assertTrue(server.getOption(Options.REUSE_ADDRESSES));
        assertTrue(server.getOption(Options.RECEIVE_BUFFER) > 0);
        assertEquals(1000000, (int) server.getOption(Options.SEND_BUFFER));
        assertTrue(server.getOption(Options.KEEP_ALIVE));
        assertTrue(server.getOption(Options.TCP_OOB_INLINE));
        assertTrue(server.getOption(Options.TCP_NODELAY));
        assertEquals(1000000, (int) server.getOption(Options.READ_TIMEOUT));
        assertEquals(1000000, (int) server.getOption(Options.WRITE_TIMEOUT));
        assertEquals(1000000, (int) server.getOption(Options.CONNECTION_HIGH_WATER));
        assertEquals(1000000, (int) server.getOption(Options.CONNECTION_LOW_WATER));

        assertTrue(server.setOption(Options.REUSE_ADDRESSES, false));
        assertTrue(server.setOption(Options.RECEIVE_BUFFER, 20000) > 0);
        assertEquals(1000000, (int) server.setOption(Options.SEND_BUFFER, 2000000));
        assertTrue(server.setOption(Options.KEEP_ALIVE, false));
        assertTrue(server.setOption(Options.TCP_OOB_INLINE, false));
        assertTrue(server.setOption(Options.TCP_NODELAY, false));
        assertEquals(1000000, (int) server.setOption(Options.READ_TIMEOUT, 2000000));
        assertEquals(1000000, (int) server.setOption(Options.WRITE_TIMEOUT, 2000000));
        assertEquals(1000000, (int) server.setOption(Options.CONNECTION_HIGH_WATER, 200000)); // this will automatically update low water
        assertEquals(200000, (int) server.setOption(Options.CONNECTION_LOW_WATER, 20000));

        assertFalse(server.getOption(Options.REUSE_ADDRESSES));
        assertEquals(20000, (int) server.getOption(Options.RECEIVE_BUFFER));
        assertEquals(2000000, (int) server.getOption(Options.SEND_BUFFER));
        assertFalse(server.getOption(Options.KEEP_ALIVE));
        assertFalse(server.getOption(Options.TCP_OOB_INLINE));
        assertFalse(server.getOption(Options.TCP_NODELAY));
        assertEquals(2000000, (int) server.getOption(Options.READ_TIMEOUT));
        assertEquals(2000000, (int) server.getOption(Options.WRITE_TIMEOUT));
        assertEquals(200000, (int) server.getOption(Options.CONNECTION_HIGH_WATER));
        assertEquals(20000, (int) server.getOption(Options.CONNECTION_LOW_WATER));

        assertFalse(server.setOption(Options.REUSE_ADDRESSES, true));
        assertEquals(20000, (int) server.setOption(Options.RECEIVE_BUFFER, 2000));
        assertEquals(2000000, (int) server.setOption(Options.SEND_BUFFER, 2200));
        assertFalse(server.setOption(Options.KEEP_ALIVE, true));
        assertFalse(server.setOption(Options.TCP_OOB_INLINE, true));
        assertFalse(server.setOption(Options.TCP_NODELAY, true));
        assertEquals(2000000, (int) server.setOption(Options.READ_TIMEOUT, 2220000));
        assertEquals(2000000, (int) server.setOption(Options.WRITE_TIMEOUT, 2222000));
        assertEquals(200000, (int) server.setOption(Options.CONNECTION_HIGH_WATER, 222220)); // this will automatically update low water
        assertEquals(20000, (int) server.setOption(Options.CONNECTION_LOW_WATER, 22222));

        assertTrue(server.getOption(Options.REUSE_ADDRESSES));
        assertEquals(2000, (int) server.getOption(Options.RECEIVE_BUFFER));
        assertEquals(2200, (int) server.getOption(Options.SEND_BUFFER));
        assertTrue(server.getOption(Options.KEEP_ALIVE));
        assertTrue(server.getOption(Options.TCP_OOB_INLINE));
        assertTrue(server.getOption(Options.TCP_NODELAY));
        assertEquals(2220000, (int) server.getOption(Options.READ_TIMEOUT));
        assertEquals(2222000, (int) server.getOption(Options.WRITE_TIMEOUT));
        assertEquals(222220, (int) server.getOption(Options.CONNECTION_HIGH_WATER));
        assertEquals(22222, (int) server.getOption(Options.CONNECTION_LOW_WATER));

        // clear all options
        assertTrue(server.setOption(Options.REUSE_ADDRESSES, null));
        assertEquals(2000, (int) server.setOption(Options.RECEIVE_BUFFER, null));
        assertEquals(2200, (int) server.setOption(Options.SEND_BUFFER, null));
        assertTrue(server.setOption(Options.KEEP_ALIVE, null));
        assertTrue(server.setOption(Options.TCP_OOB_INLINE, null));
        assertTrue(server.setOption(Options.TCP_NODELAY, null));
        assertEquals(2220000, (int) server.setOption(Options.READ_TIMEOUT, null));
        assertEquals(2222000, (int) server.setOption(Options.WRITE_TIMEOUT, null));
        assertEquals(222220, (int) server.setOption(Options.CONNECTION_HIGH_WATER, null)); // this will automatically update low water
        assertEquals(22222, (int) server.setOption(Options.CONNECTION_LOW_WATER, null));

        // check all default values have been set
        assertFalse(server.getOption(Options.REUSE_ADDRESSES));
        assertEquals(0x10000, (int) server.getOption(Options.RECEIVE_BUFFER));
        assertEquals(0x10000, (int) server.getOption(Options.SEND_BUFFER));
        assertFalse(server.getOption(Options.KEEP_ALIVE));
        assertFalse(server.getOption(Options.TCP_OOB_INLINE));
        assertFalse(server.getOption(Options.TCP_NODELAY));
        assertEquals(0, (int) server.getOption(Options.READ_TIMEOUT));
        assertEquals(0, (int) server.getOption(Options.WRITE_TIMEOUT));
        assertTrue((int) server.getOption(Options.CONNECTION_LOW_WATER) > 0);
        assertTrue((int) server.getOption(Options.CONNECTION_HIGH_WATER) > 0);
        assertTrue(server.getOption(Options.CONNECTION_LOW_WATER) <= server.getOption(Options.CONNECTION_HIGH_WATER));

        assertFalse(server.setOption(Options.REUSE_ADDRESSES, null));
       // FIXME XNIO-171 assertEquals(0x10000, (int) server.setOption(Options.RECEIVE_BUFFER, null));
        assertEquals(0x10000, (int) server.setOption(Options.SEND_BUFFER, null));
        assertFalse(server.setOption(Options.KEEP_ALIVE, null));
        assertFalse(server.setOption(Options.TCP_OOB_INLINE, null));
        assertFalse(server.setOption(Options.TCP_NODELAY, null));
        assertEquals(0, (int) server.setOption(Options.READ_TIMEOUT, null));
        assertEquals(0, (int) server.setOption(Options.WRITE_TIMEOUT, null));
        assertTrue((int) server.setOption(Options.CONNECTION_LOW_WATER, null) > 0);
        assertTrue((int) server.setOption(Options.CONNECTION_HIGH_WATER, null) > 0);
        server.close();
    }

    @Test
    public void resetAllOptions() throws IOException {
        final Option<?>[] unsupportedOptions = OptionHelper.getNotSupportedOptions(Options.WORKER_ACCEPT_THREADS, 
                Options.WORKER_ESTABLISH_WRITING, Options.REUSE_ADDRESSES, Options.RECEIVE_BUFFER,  Options.SEND_BUFFER,
                Options.KEEP_ALIVE, Options.TCP_OOB_INLINE, Options.TCP_NODELAY, Options.READ_TIMEOUT,
                Options.WRITE_TIMEOUT, Options.CONNECTION_HIGH_WATER, Options.CONNECTION_LOW_WATER);

        // supported options
        final OptionMap.Builder optionMapBuilder = OptionMap.builder();
        optionMapBuilder.set(Options.WORKER_ACCEPT_THREADS, getWorkerReadThreads() == 1? 1: getWorkerReadThreads() - 1);
        optionMapBuilder.set(Options.WORKER_ESTABLISH_WRITING, false);
        optionMapBuilder.set(Options.REUSE_ADDRESSES, true);
        optionMapBuilder.set(Options.RECEIVE_BUFFER, 30000);
        optionMapBuilder.set(Options.SEND_BUFFER, 29000);
        optionMapBuilder.set(Options.KEEP_ALIVE, false);
        optionMapBuilder.set(Options.TCP_OOB_INLINE, false);
        optionMapBuilder.set(Options.TCP_NODELAY, false);
        optionMapBuilder.set(Options.READ_TIMEOUT, 28);
        optionMapBuilder.set(Options.WRITE_TIMEOUT, 27);
        optionMapBuilder.set(Options.CONNECTION_HIGH_WATER, 26);
        optionMapBuilder.set(Options.CONNECTION_LOW_WATER, 25);

        // unsupported options
        optionMapBuilder.set(Options.ALLOW_BLOCKING, true);
        optionMapBuilder.set(Options.BACKLOG, 10000);
        optionMapBuilder.set(Options.BROADCAST, false);
        optionMapBuilder.set(Options.CLOSE_ABORT, true);
        optionMapBuilder.set(Options.CORK, false);
        optionMapBuilder.set(Options.FILE_ACCESS, FileAccess.READ_ONLY);
        optionMapBuilder.set(Options.IP_TRAFFIC_CLASS, 20000);
        optionMapBuilder.set(Options.MAX_INBOUND_MESSAGE_SIZE, 30000);
        optionMapBuilder.set(Options.MAX_OUTBOUND_MESSAGE_SIZE, 40000);
        optionMapBuilder.set(Options.MULTICAST, true);
        optionMapBuilder.set(Options.MULTICAST_TTL, 50000);
        optionMapBuilder.set(Options.SASL_DISALLOWED_MECHANISMS, Sequence.<String>empty());
        optionMapBuilder.set(Options.SASL_MECHANISMS, Sequence.<String>of("mech1"));
        optionMapBuilder.set(Options.SASL_POLICY_FORWARD_SECRECY, true);
        optionMapBuilder.set(Options.SASL_POLICY_NOACTIVE, false);
        optionMapBuilder.set(Options.SASL_POLICY_NOANONYMOUS, true);
        optionMapBuilder.set(Options.SASL_POLICY_NODICTIONARY, false);
        optionMapBuilder.set(Options.SASL_POLICY_NOPLAINTEXT, true);
        optionMapBuilder.set(Options.SASL_POLICY_PASS_CREDENTIALS, false);
        optionMapBuilder.set(Options.SASL_PROPERTIES, Sequence.<Property>empty());
        optionMapBuilder.set(Options.SASL_QOP, Sequence.<SaslQop>of(SaslQop.AUTH_CONF));
        optionMapBuilder.set(Options.SASL_REUSE, true);
        optionMapBuilder.set(Options.SASL_SERVER_AUTH, false);
        optionMapBuilder.set(Options.SASL_STRENGTH, SaslStrength.MEDIUM);
        optionMapBuilder.set(Options.SECURE, true);
        optionMapBuilder.set(Options.SSL_APPLICATION_BUFFER_REGION_SIZE, 60000);
        optionMapBuilder.set(Options.SSL_APPLICATION_BUFFER_SIZE, 70000);
        optionMapBuilder.set(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.NOT_REQUESTED);
        optionMapBuilder.set(Options.SSL_CLIENT_SESSION_CACHE_SIZE, 80000);
        optionMapBuilder.set(Options.SSL_CLIENT_SESSION_TIMEOUT, 90000);
        optionMapBuilder.set(Options.SSL_ENABLE_SESSION_CREATION, false);
        optionMapBuilder.set(Options.SSL_ENABLED, true);
        optionMapBuilder.set(Options.SSL_ENABLED_CIPHER_SUITES, Sequence.<String>of("cipher foo"));
        optionMapBuilder.set(Options.SSL_ENABLED_PROTOCOLS, Sequence.<String>of("foo", "dummy"));
        optionMapBuilder.set(Options.SSL_JSSE_KEY_MANAGER_CLASSES, Sequence.<Class<? extends KeyManager>>empty());
        optionMapBuilder.set(Options.SSL_JSSE_TRUST_MANAGER_CLASSES, Sequence.<Class<? extends TrustManager>>empty());
        optionMapBuilder.set(Options.SSL_PACKET_BUFFER_REGION_SIZE, 100000);
        optionMapBuilder.set(Options.SSL_PACKET_BUFFER_SIZE, 110000);
        optionMapBuilder.set(Options.SSL_PEER_HOST_NAME, "none");
        optionMapBuilder.set(Options.SSL_PEER_PORT, 0);
        optionMapBuilder.set(Options.SSL_PROTOCOL, "any");
        optionMapBuilder.set(Options.SSL_PROVIDER, "-");
        optionMapBuilder.set(Options.SSL_RNG_OPTIONS, OptionMap.EMPTY);
        optionMapBuilder.set(Options.SSL_SERVER_SESSION_CACHE_SIZE, 120000);
        optionMapBuilder.set(Options.SSL_SERVER_SESSION_TIMEOUT, 130000);
        optionMapBuilder.set(Options.SSL_STARTTLS, false);
        optionMapBuilder.set(Options.SSL_SUPPORTED_CIPHER_SUITES, Sequence.<String>empty());
        optionMapBuilder.set(Options.SSL_SUPPORTED_PROTOCOLS, Sequence.<String>empty());
        optionMapBuilder.set(Options.SSL_USE_CLIENT_MODE, true);
        optionMapBuilder.set(Options.STACK_SIZE, 140000);
        optionMapBuilder.set(Options.THREAD_DAEMON, false);
        optionMapBuilder.set(Options.THREAD_PRIORITY, 15);
        optionMapBuilder.set(Options.USE_DIRECT_BUFFERS, true);
        optionMapBuilder.set(Options.WORKER_NAME, "worker");
        optionMapBuilder.set(Options.WORKER_READ_THREADS, 16);
        optionMapBuilder.set(Options.WORKER_TASK_CORE_THREADS, 17);
        optionMapBuilder.set(Options.WORKER_TASK_KEEPALIVE, 18);
        optionMapBuilder.set(Options.WORKER_TASK_LIMIT, 19);
        optionMapBuilder.set(Options.WORKER_TASK_MAX_THREADS, 20);
        optionMapBuilder.set(Options.WORKER_WRITE_THREADS, 21);

        createServer(optionMapBuilder.getMap());

        assertTrue(server.getOption(Options.REUSE_ADDRESSES));
        assertEquals(30000, (int) server.getOption(Options.RECEIVE_BUFFER));
        assertEquals(29000, (int) server.getOption(Options.SEND_BUFFER));
        assertFalse(server.getOption(Options.KEEP_ALIVE));
        assertFalse(server.getOption(Options.TCP_OOB_INLINE));
        assertFalse(server.getOption(Options.TCP_NODELAY));
        assertEquals(28, (int) server.getOption(Options.READ_TIMEOUT));
        assertEquals(27, (int) server.getOption(Options.WRITE_TIMEOUT));
        assertEquals(26, (int) server.getOption(Options.CONNECTION_HIGH_WATER));
        assertEquals(25, (int) server.getOption(Options.CONNECTION_LOW_WATER));

        for (Option<?> option: unsupportedOptions) {
            assertNull("Non null value for option " + option + ": " + server.getOption(option), server.getOption(option));
            assertFalse(server.supportsOption(option));
        }

        assertTrue(server.supportsOption(Options.REUSE_ADDRESSES));
        assertTrue(server.supportsOption(Options.RECEIVE_BUFFER));
        assertTrue(server.supportsOption(Options.SEND_BUFFER));
        assertTrue(server.supportsOption(Options.KEEP_ALIVE));
        assertTrue(server.supportsOption(Options.TCP_NODELAY));
        assertTrue(server.supportsOption(Options.TCP_OOB_INLINE));
        assertTrue(server.supportsOption(Options.READ_TIMEOUT));
        assertTrue(server.supportsOption(Options.WRITE_TIMEOUT));
        assertTrue(server.supportsOption(Options.CONNECTION_LOW_WATER));
        assertTrue(server.supportsOption(Options.CONNECTION_HIGH_WATER));

        assertTrue(server.setOption(Options.REUSE_ADDRESSES, false));
        assertEquals(30000, (int) server.setOption(Options.RECEIVE_BUFFER, 24000));
        assertEquals(29000, (int) server.setOption(Options.SEND_BUFFER, 23000));
        assertFalse(server.setOption(Options.KEEP_ALIVE, true));
        assertFalse(server.setOption(Options.TCP_OOB_INLINE, true));
        assertFalse(server.setOption(Options.TCP_NODELAY, true));
        assertEquals(28, (int) server.setOption(Options.READ_TIMEOUT, 22000));
        assertEquals(27, (int) server.setOption(Options.WRITE_TIMEOUT, 21000000));
        assertEquals(25, (int) server.setOption(Options.CONNECTION_LOW_WATER, 190));
        assertEquals(190, (int) server.setOption(Options.CONNECTION_HIGH_WATER, 200));

        // unsupported options
        assertNull(server.setOption(Options.ALLOW_BLOCKING, true));
        assertNull(server.setOption(Options.BACKLOG, 10000));
        assertNull(server.setOption(Options.BROADCAST, false));
        assertNull(server.setOption(Options.CLOSE_ABORT, true));
        assertNull(server.setOption(Options.CORK, false));
        assertNull(server.setOption(Options.FILE_ACCESS, FileAccess.READ_ONLY));
        assertNull(server.setOption(Options.IP_TRAFFIC_CLASS, 20000));
        assertNull(server.setOption(Options.MAX_INBOUND_MESSAGE_SIZE, 30000));
        assertNull(server.setOption(Options.MAX_OUTBOUND_MESSAGE_SIZE, 40000));
        assertNull(server.setOption(Options.MULTICAST, true));
        assertNull(server.setOption(Options.MULTICAST_TTL, 50000));
        assertNull(server.setOption(Options.SASL_DISALLOWED_MECHANISMS, Sequence.<String>empty()));
        assertNull(server.setOption(Options.SASL_MECHANISMS, Sequence.<String>of("mech1")));
        assertNull(server.setOption(Options.SASL_POLICY_FORWARD_SECRECY, true));
        assertNull(server.setOption(Options.SASL_POLICY_NOACTIVE, false));
        assertNull(server.setOption(Options.SASL_POLICY_NOANONYMOUS, true));
        assertNull(server.setOption(Options.SASL_POLICY_NODICTIONARY, false));
        assertNull(server.setOption(Options.SASL_POLICY_NOPLAINTEXT, true));
        assertNull(server.setOption(Options.SASL_POLICY_PASS_CREDENTIALS, false));
        assertNull(server.setOption(Options.SASL_PROPERTIES, Sequence.<Property>empty()));
        assertNull(server.setOption(Options.SASL_QOP, Sequence.<SaslQop>of(SaslQop.AUTH_CONF)));
        assertNull(server.setOption(Options.SASL_REUSE, true));
        assertNull(server.setOption(Options.SASL_SERVER_AUTH, false));
        assertNull(server.setOption(Options.SASL_STRENGTH, SaslStrength.MEDIUM));
        assertNull(server.setOption(Options.SECURE, true));
        assertNull(server.setOption(Options.SSL_APPLICATION_BUFFER_REGION_SIZE, 60000));
        assertNull(server.setOption(Options.SSL_APPLICATION_BUFFER_SIZE, 70000));
        assertNull(server.setOption(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.NOT_REQUESTED));
        assertNull(server.setOption(Options.SSL_CLIENT_SESSION_CACHE_SIZE, 80000));
        assertNull(server.setOption(Options.SSL_CLIENT_SESSION_TIMEOUT, 90000));
        assertNull(server.setOption(Options.SSL_ENABLE_SESSION_CREATION, false));
        assertNull(server.setOption(Options.SSL_ENABLED, true));
        assertNull(server.setOption(Options.SSL_ENABLED_CIPHER_SUITES, Sequence.<String>of("cipher foo")));
        assertNull(server.setOption(Options.SSL_ENABLED_PROTOCOLS, Sequence.<String>of("foo", "dummy")));
        assertNull(server.setOption(Options.SSL_JSSE_KEY_MANAGER_CLASSES, Sequence.<Class<? extends KeyManager>>empty()));
        assertNull(server.setOption(Options.SSL_JSSE_TRUST_MANAGER_CLASSES, Sequence.<Class<? extends TrustManager>>empty()));
        assertNull(server.setOption(Options.SSL_PACKET_BUFFER_REGION_SIZE, 100000));
        assertNull(server.setOption(Options.SSL_PACKET_BUFFER_SIZE, 110000));
        assertNull(server.setOption(Options.SSL_PEER_HOST_NAME, "none"));
        assertNull(server.setOption(Options.SSL_PEER_PORT, 0));
        assertNull(server.setOption(Options.SSL_PROTOCOL, "any"));
        assertNull(server.setOption(Options.SSL_PROVIDER, "-"));
        assertNull(server.setOption(Options.SSL_RNG_OPTIONS, OptionMap.EMPTY));
        assertNull(server.setOption(Options.SSL_SERVER_SESSION_CACHE_SIZE, 120000));
        assertNull(server.setOption(Options.SSL_SERVER_SESSION_TIMEOUT, 130000));
        assertNull(server.setOption(Options.SSL_STARTTLS, false));
        assertNull(server.setOption(Options.SSL_SUPPORTED_CIPHER_SUITES, Sequence.<String>empty()));
        assertNull(server.setOption(Options.SSL_SUPPORTED_PROTOCOLS, Sequence.<String>empty()));
        assertNull(server.setOption(Options.SSL_USE_CLIENT_MODE, true));
        assertNull(server.setOption(Options.STACK_SIZE, 140000l));
        assertNull(server.setOption(Options.THREAD_DAEMON, false));
        assertNull(server.setOption(Options.THREAD_PRIORITY, 15));
        assertNull(server.setOption(Options.USE_DIRECT_BUFFERS, true));
        assertNull(server.setOption(Options.WORKER_NAME, "worker"));
        assertNull(server.setOption(Options.WORKER_READ_THREADS, 16));
        assertNull(server.setOption(Options.WORKER_TASK_CORE_THREADS, 17));
        assertNull(server.setOption(Options.WORKER_TASK_KEEPALIVE, 18));
        assertNull(server.setOption(Options.WORKER_TASK_LIMIT, 19));
        assertNull(server.setOption(Options.WORKER_TASK_MAX_THREADS, 20));
        assertNull(server.setOption(Options.WORKER_WRITE_THREADS, 21));

        assertFalse(server.getOption(Options.REUSE_ADDRESSES));
        assertEquals(24000, (int) server.getOption(Options.RECEIVE_BUFFER));
        assertEquals(23000, (int) server.getOption(Options.SEND_BUFFER));
        assertTrue(server.getOption(Options.KEEP_ALIVE));
        assertTrue(server.getOption(Options.TCP_OOB_INLINE));
        assertTrue(server.getOption(Options.TCP_NODELAY));
        assertEquals(22000, (int) server.getOption(Options.READ_TIMEOUT));
        assertEquals(21000000, (int) server.getOption(Options.WRITE_TIMEOUT));
        assertEquals(200, (int) server.getOption(Options.CONNECTION_HIGH_WATER));
        assertEquals(190, (int) server.getOption(Options.CONNECTION_LOW_WATER));

        for (Option<?> option: unsupportedOptions) {
            assertNull("Non null value for option " + option + ": " + server.getOption(option), server.getOption(option));
            assertFalse(server.supportsOption(option));
        }
        server.close();
    }

    @Test
    public void largeNumberOfThreads() throws IllegalArgumentException, IOException {
        final XnioWorker worker1 = xnio.createWorker(OptionMap.create(Options.WORKER_WRITE_THREADS, 30,
                Options.WORKER_READ_THREADS, 50));
        //make sure that a bigger number of threads is handled without any errors
        createServer(worker1, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, true, Options.WORKER_ACCEPT_THREADS, 12));
        createServer(worker1, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, false, Options.WORKER_ACCEPT_THREADS, 40));
        createServer(worker1, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, false, Options.WORKER_ACCEPT_THREADS, 20));
        createServer(worker1, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, false, Options.WORKER_ACCEPT_THREADS, 10));

        // huge number of threads
        final XnioWorker worker2 = xnio.createWorker(OptionMap.create(Options.WORKER_WRITE_THREADS, 100,
                Options.WORKER_READ_THREADS, 70));
        //make sure that a bigger number of threads is handled without any errors
        createServer(worker2, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, true, Options.WORKER_ACCEPT_THREADS, 12));
        createServer(worker2, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, true, Options.WORKER_ACCEPT_THREADS, 80));
        createServer(worker2, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, true, Options.WORKER_ACCEPT_THREADS, 100));
        createServer(worker2, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, false, Options.WORKER_ACCEPT_THREADS, 10));
        createServer(worker2, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, false, Options.WORKER_ACCEPT_THREADS, 50));
        createServer(worker2, OptionMap.create(Options.WORKER_ESTABLISH_WRITING, false, Options.WORKER_ACCEPT_THREADS, 69));
        server.close();
    }
}
