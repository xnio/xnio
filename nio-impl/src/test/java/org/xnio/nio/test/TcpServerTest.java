/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
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

    @SuppressWarnings("deprecation")
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
    @SuppressWarnings("deprecation")
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
