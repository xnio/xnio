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
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.xnio.LocalSocketAddress;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * Super class common to all TCP server tests.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public abstract class TcpServerTest {

    protected static final int SERVER_PORT = 12345;

    private static int workerWriteThreadsValue;
    private static int workerReadThreadsValue;

    private static SocketAddress bindAddress;
    protected static Xnio xnio;
    private static XnioWorker worker;

    protected AcceptingChannel<? extends ConnectedStreamChannel> server;

    @BeforeClass
    public static void createWorker() throws IOException {
        int readThreads = (int) Math.round(Math.random() * 10);
        if (readThreads == 0) {
            readThreads = 1;
        }
        workerReadThreadsValue = readThreads;
        int writeThreads = (int) Math.round(Math.random() * 10);
        if (writeThreads == 0) {
            writeThreads = 1;
        }
        workerWriteThreadsValue = writeThreads;
        xnio = Xnio.getInstance("nio", TcpServerTest.class.getClassLoader());
        worker = xnio.createWorker(OptionMap.create(Options.WORKER_WRITE_THREADS, workerWriteThreadsValue, Options.WORKER_READ_THREADS, workerReadThreadsValue));
        bindAddress = new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT);
    }

    @AfterClass
    public static void destroyWorker() throws InterruptedException {
        worker.shutdown();
        worker.awaitTermination(1L, TimeUnit.MINUTES);
    }

    /**
     * Utility method to create the server. The field server is updated with the value of the server created.
     * <p> Every server created by this method will be automatically closed after the test executes, or as soon as a 
     * new request to create a server is made.
     *  
     * @param optionMap     the options that will be used to create the server
     */
    protected void createServer(OptionMap optionMap) throws IOException {
        createServer(worker, optionMap);
    }

    /**
     * Utility method to create the server. The field server is updated with the value of the server created.
     * <p> Every server created by this method will be automatically closed after the test executes, or as soon as a 
     * new request to create a server is made.
     * 
     * @param worker      the worker
     * @param optionMap   the options that will be used to create the server
     */
    protected void createServer(XnioWorker worker, OptionMap optionMap) throws IOException {
        if (server != null) {
            server.close();
            assertFalse(server.isOpen());
        }
        server = worker.createStreamServer(bindAddress, null, optionMap);
        assertTrue(server.isOpen());
        assertNotNull(server);
        assertEquals(bindAddress, server.getLocalAddress());
        assertEquals(bindAddress, server.getLocalAddress(InetSocketAddress.class));
        assertNull(server.getLocalAddress(LocalSocketAddress.class));
    }

    /**
     * Returns the number of write threads in the worker, which is randomly generated at every execution. 
     */
    protected static int getWorkerWriteThreads() {
        return workerWriteThreadsValue;
    }

    /**
     * Returns the number of readthreads in the worker, which is randomly generated at every execution. 
     */
    protected static int getWorkerReadThreads() {
        return workerReadThreadsValue;
    }

    @After
    public void closeServer() throws IOException {
        if (server != null) {
            server.close();
            assertFalse(server.isOpen());
        }
    }
}
