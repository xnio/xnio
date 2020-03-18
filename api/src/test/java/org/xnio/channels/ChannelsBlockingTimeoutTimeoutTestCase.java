package org.xnio.channels;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * Test for {@link Channels} blocking operations with timeouts.
 *
 * @author Carter Kozak
 */
public class ChannelsBlockingTimeoutTimeoutTestCase {

    @Test
    public void testSingleBufferReadBlockingSpuriousReturn() throws IOException {
        List<String> invocations = new ArrayList<String>();
        ReadableSuspendableChannel stubChannel = (ReadableSuspendableChannel) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ReadableSuspendableChannel.class},
                new InvocationHandler() {
                    int reads = 0;
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        invocations.add(method.getName());
                        if ("read".equals(method.getName())) {
                            if (reads++ <= 1) {
                                return 0;
                            } else {
                                return 1;
                            }
                        } else if ("awaitReadable".equals(method.getName())) {
                            return null;
                        }
                        throw new IllegalStateException("Unexpected method invocation: "
                                + method + " with args " + Arrays.toString(args));
                    }
                });
        int result = Channels.readBlocking(stubChannel, ByteBuffer.allocate(32), 1, TimeUnit.SECONDS);
        assertEquals(1, result);
        // Validate that awaitReadable was called multiple times, and is always followed by a read.
        assertEquals(Arrays.asList("read", "awaitReadable", "read", "awaitReadable", "read"), invocations);
    }

    @Test
    public void testSingleBufferReadBlockingSpuriousReturnReachesTimeout() throws IOException {
        List<String> invocations = new ArrayList<String>();
        ReadableSuspendableChannel stubChannel = (ReadableSuspendableChannel) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ReadableSuspendableChannel.class},
                new InvocationHandler() {
                    int reads = 0;
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        invocations.add(method.getName());
                        if ("read".equals(method.getName())) {
                            if (reads++ <= 1) {
                                return 0;
                            } else {
                                return 1;
                            }
                        } else if ("awaitReadable".equals(method.getName())) {
                            // Sleep twenty milliseconds, which exceeds our ten millisecond timeout
                            Thread.sleep(20);
                            return null;
                        }
                        throw new IllegalStateException("Unexpected method invocation: "
                                + method + " with args " + Arrays.toString(args));
                    }
                });
        // Note, use ten milliseconds to avoid issues on platforms with coarse clocks
        int result = Channels.readBlocking(stubChannel, ByteBuffer.allocate(32), 10, TimeUnit.MILLISECONDS);
        assertEquals(0, result);
        // Validate that awaitReadable was called multiple times, and is always followed by a read.
        assertEquals(Arrays.asList("read", "awaitReadable", "read"), invocations);
    }

    @Test
    public void testMultiBufferReadBlockingSpuriousReturn() throws IOException {
        List<String> invocations = new ArrayList<String>();
        ReadableSuspendableChannel stubChannel = (ReadableSuspendableChannel) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ReadableSuspendableChannel.class},
                new InvocationHandler() {
                    int reads = 0;
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        invocations.add(method.getName());
                        if ("read".equals(method.getName())) {
                            if (reads++ <= 1) {
                                return 0L;
                            } else {
                                return 1L;
                            }
                        } else if ("awaitReadable".equals(method.getName())) {
                            return null;
                        }
                        throw new IllegalStateException("Unexpected method invocation: "
                                + method + " with args " + Arrays.toString(args));
                    }
                });
        long result = Channels.readBlocking(stubChannel, new ByteBuffer[] {ByteBuffer.allocate(32)}, 0, 10, 1, TimeUnit.SECONDS);
        assertEquals(1L, result);
        // Validate that awaitReadable was called multiple times, and is always followed by a read.
        assertEquals(Arrays.asList("read", "awaitReadable", "read", "awaitReadable", "read"), invocations);
    }

    @Test
    public void testMultiBufferReadBlockingSpuriousReturnReachesTimeout() throws IOException {
        List<String> invocations = new ArrayList<String>();
        ReadableSuspendableChannel stubChannel = (ReadableSuspendableChannel) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ReadableSuspendableChannel.class},
                new InvocationHandler() {
                    int reads = 0;
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        invocations.add(method.getName());
                        if ("read".equals(method.getName())) {
                            if (reads++ <= 1) {
                                return 0L;
                            } else {
                                return 1L;
                            }
                        } else if ("awaitReadable".equals(method.getName())) {
                            // Sleep twenty milliseconds, which exceeds our ten millisecond timeout
                            Thread.sleep(20);
                            return null;
                        }
                        throw new IllegalStateException("Unexpected method invocation: "
                                + method + " with args " + Arrays.toString(args));
                    }
                });
        // Note, use ten milliseconds to avoid issues on platforms with coarse clocks
        long result = Channels.readBlocking(stubChannel, new ByteBuffer[] {ByteBuffer.allocate(32)}, 0, 10, 10, TimeUnit.MILLISECONDS);
        assertEquals(0L, result);
        // Validate that awaitReadable was called multiple times, and is always followed by a read.
        assertEquals(Arrays.asList("read", "awaitReadable", "read"), invocations);
    }

    // here

    @Test
    public void testSingleBufferReceiveBlockingSpuriousReturn() throws IOException {
        List<String> invocations = new ArrayList<String>();
        ReadableSuspendableChannel stubChannel = (ReadableSuspendableChannel) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ReadableSuspendableChannel.class},
                new InvocationHandler() {
                    int reads = 0;
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        invocations.add(method.getName());
                        if ("receive".equals(method.getName())) {
                            if (reads++ <= 1) {
                                return 0;
                            } else {
                                return 1;
                            }
                        } else if ("awaitReadable".equals(method.getName())) {
                            return null;
                        }
                        throw new IllegalStateException("Unexpected method invocation: "
                                + method + " with args " + Arrays.toString(args));
                    }
                });
        int result = Channels.receiveBlocking(stubChannel, ByteBuffer.allocate(32), 1, TimeUnit.SECONDS);
        assertEquals(1, result);
        // Validate that awaitReadable was called multiple times, and is always followed by a read.
        assertEquals(Arrays.asList("receive", "awaitReadable", "receive", "awaitReadable", "receive"), invocations);
    }

    @Test
    public void testSingleBufferReceiveBlockingSpuriousReturnReachesTimeout() throws IOException {
        List<String> invocations = new ArrayList<String>();
        ReadableSuspendableChannel stubChannel = (ReadableSuspendableChannel) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ReadableSuspendableChannel.class},
                new InvocationHandler() {
                    int reads = 0;
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        invocations.add(method.getName());
                        if ("receive".equals(method.getName())) {
                            if (reads++ <= 1) {
                                return 0;
                            } else {
                                return 1;
                            }
                        } else if ("awaitReadable".equals(method.getName())) {
                            // Sleep twenty milliseconds, which exceeds our ten millisecond timeout
                            Thread.sleep(20);
                            return null;
                        }
                        throw new IllegalStateException("Unexpected method invocation: "
                                + method + " with args " + Arrays.toString(args));
                    }
                });
        // Note, use ten milliseconds to avoid issues on platforms with coarse clocks
        int result = Channels.receiveBlocking(stubChannel, ByteBuffer.allocate(32), 10, TimeUnit.MILLISECONDS);
        assertEquals(0, result);
        // Validate that awaitReadable was called multiple times, and is always followed by a read.
        assertEquals(Arrays.asList("receive", "awaitReadable", "receive"), invocations);
    }

    @Test
    public void testMultiBufferReceiveBlockingSpuriousReturn() throws IOException {
        List<String> invocations = new ArrayList<String>();
        ReadableSuspendableChannel stubChannel = (ReadableSuspendableChannel) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ReadableSuspendableChannel.class},
                new InvocationHandler() {
                    int reads = 0;
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        invocations.add(method.getName());
                        if ("receive".equals(method.getName())) {
                            if (reads++ <= 1) {
                                return 0L;
                            } else {
                                return 1L;
                            }
                        } else if ("awaitReadable".equals(method.getName())) {
                            return null;
                        }
                        throw new IllegalStateException("Unexpected method invocation: "
                                + method + " with args " + Arrays.toString(args));
                    }
                });
        long result = Channels.receiveBlocking(stubChannel, new ByteBuffer[] {ByteBuffer.allocate(32)}, 0, 10, 1, TimeUnit.SECONDS);
        assertEquals(1L, result);
        // Validate that awaitReadable was called multiple times, and is always followed by a read.
        assertEquals(Arrays.asList("receive", "awaitReadable", "receive", "awaitReadable", "receive"), invocations);
    }

    @Test
    public void testMultiBufferReceiveBlockingSpuriousReturnReachesTimeout() throws IOException {
        List<String> invocations = new ArrayList<String>();
        ReadableSuspendableChannel stubChannel = (ReadableSuspendableChannel) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ReadableSuspendableChannel.class},
                new InvocationHandler() {
                    int reads = 0;
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        invocations.add(method.getName());
                        if ("receive".equals(method.getName())) {
                            if (reads++ <= 1) {
                                return 0L;
                            } else {
                                return 1L;
                            }
                        } else if ("awaitReadable".equals(method.getName())) {
                            // Sleep twenty milliseconds, which exceeds our ten millisecond timeout
                            Thread.sleep(20);
                            return null;
                        }
                        throw new IllegalStateException("Unexpected method invocation: "
                                + method + " with args " + Arrays.toString(args));
                    }
                });
        // Note, use ten milliseconds to avoid issues on platforms with coarse clocks
        long result = Channels.receiveBlocking(stubChannel, new ByteBuffer[] {ByteBuffer.allocate(32)}, 0, 10, 10, TimeUnit.MILLISECONDS);
        assertEquals(0L, result);
        // Validate that awaitReadable was called multiple times, and is always followed by a read.
        assertEquals(Arrays.asList("receive", "awaitReadable", "receive"), invocations);
    }

    interface ReadableSuspendableChannel
            extends ReadableByteChannel, SuspendableReadChannel, ScatteringByteChannel, ReadableMessageChannel {

    }
}
