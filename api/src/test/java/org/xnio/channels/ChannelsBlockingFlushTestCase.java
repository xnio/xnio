/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2020 Red Hat, Inc. and/or its affiliates, and individual
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
package org.xnio.channels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 * Test for {@link Channels} blocking flush operations with timeouts.
 *
 * @author Carter Kozak
 */
public class ChannelsBlockingFlushTestCase {

    @Test
    public void testFlushBlockingSpuriousReturn() throws IOException {
        List<String> invocations = new ArrayList<String>();
        SuspendableWriteChannel stubChannel = (SuspendableWriteChannel) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{SuspendableWriteChannel.class},
                new InvocationHandler() {
                    int flushes = 0;
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        invocations.add(method.getName());
                        if ("flush".equals(method.getName())) {
                            return flushes++ > 1;
                        } else if ("awaitWritable".equals(method.getName())) {
                            return null;
                        }
                        throw new IllegalStateException("Unexpected method invocation: "
                                + method + " with args " + Arrays.toString(args));
                    }
                });
        assertTrue(Channels.flushBlocking(stubChannel,1, TimeUnit.SECONDS));
        // Validate that awaitReadable was called multiple times, and is always followed by a read.
        assertEquals(Arrays.asList("flush", "awaitWritable", "flush", "awaitWritable", "flush"), invocations);
    }

    @Test
    public void testFlushBlockingTimeout() throws IOException {
        List<String> invocations = new ArrayList<String>();
        SuspendableWriteChannel stubChannel = (SuspendableWriteChannel) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{SuspendableWriteChannel.class},
                new InvocationHandler() {
                    int flushes = 0;
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        invocations.add(method.getName());
                        if ("flush".equals(method.getName())) {
                            return flushes++ > 1;
                        } else if ("awaitWritable".equals(method.getName())) {
                            // Sleep twenty milliseconds, which exceeds our ten millisecond timeout
                            Thread.sleep(20);
                            return null;
                        }
                        throw new IllegalStateException("Unexpected method invocation: "
                                + method + " with args " + Arrays.toString(args));
                    }
                });
        assertFalse(Channels.flushBlocking(stubChannel,10, TimeUnit.MILLISECONDS));
        // Validate that awaitReadable was called multiple times, and is always followed by a read.
        assertEquals(Arrays.asList("flush", "awaitWritable", "flush"), invocations);
    }

    @Test
    public void testShutdownWritesBlockingSpuriousReturn() throws IOException {
        List<String> invocations = new ArrayList<String>();
        SuspendableWriteChannel stubChannel = (SuspendableWriteChannel) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{SuspendableWriteChannel.class},
                new InvocationHandler() {
                    int flushes = 0;
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        invocations.add(method.getName());
                        if ("flush".equals(method.getName())) {
                            return flushes++ > 1;
                        } else if ("awaitWritable".equals(method.getName())) {
                            return null;
                        } else if ("shutdownWrites".equals(method.getName())) {
                            return null;
                        }
                        throw new IllegalStateException("Unexpected method invocation: "
                                + method + " with args " + Arrays.toString(args));
                    }
                });
        assertTrue(Channels.shutdownWritesBlocking(stubChannel,1, TimeUnit.SECONDS));
        // Validate that awaitReadable was called multiple times, and is always followed by a read.
        assertEquals(Arrays.asList("shutdownWrites", "flush", "awaitWritable", "flush", "awaitWritable", "flush"), invocations);
    }

    @Test
    public void testShutdownWritesBlockingTimeout() throws IOException {
        List<String> invocations = new ArrayList<String>();
        SuspendableWriteChannel stubChannel = (SuspendableWriteChannel) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{SuspendableWriteChannel.class},
                new InvocationHandler() {
                    int flushes = 0;
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        invocations.add(method.getName());
                        if ("flush".equals(method.getName())) {
                            return flushes++ > 1;
                        } else if ("awaitWritable".equals(method.getName())) {
                            // Sleep twenty milliseconds, which exceeds our ten millisecond timeout
                            Thread.sleep(20);
                            return null;
                        } else if ("shutdownWrites".equals(method.getName())) {
                            return null;
                        }
                        throw new IllegalStateException("Unexpected method invocation: "
                                + method + " with args " + Arrays.toString(args));
                    }
                });
        assertFalse(Channels.shutdownWritesBlocking(stubChannel,10, TimeUnit.MILLISECONDS));
        // Validate that awaitReadable was called multiple times, and is always followed by a read.
        assertEquals(Arrays.asList("shutdownWrites", "flush", "awaitWritable", "flush"), invocations);
    }
}
