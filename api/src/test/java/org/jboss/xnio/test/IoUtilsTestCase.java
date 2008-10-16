/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.jboss.xnio.test;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import junit.framework.TestCase;
import org.jboss.xnio.ChannelSource;
import org.jboss.xnio.FinishedIoFuture;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.test.support.LoggingHelper;

/**
 *
 */
@SuppressWarnings({"unchecked"})
public final class IoUtilsTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

    public void testConnection() throws IOException {
        final boolean statuses[] = new boolean[2];
        final ChannelSource<StreamChannel> testChannelSource = new ChannelSource<StreamChannel>() {
            public IoFuture<StreamChannel> open(final IoHandler<? super StreamChannel> ioHandler) {
                final StreamChannel channel = new StreamChannel() {
                    public void suspendReads() {
                    }

                    public void resumeReads() {
                    }

                    public boolean isOpen() {
                        return false;
                    }

                    public void close() throws IOException {
                        ioHandler.handleClosed(this);
                    }

                    public void suspendWrites() {
                    }

                    public void resumeWrites() {
                    }

                    public void shutdownWrites() throws IOException {
                    }

                    public void awaitWritable() throws IOException {
                    }

                    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
                    }

                    public int write(final ByteBuffer src) throws IOException {
                        return 0;
                    }

                    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
                        return 0;
                    }

                    public long write(final ByteBuffer[] srcs) throws IOException {
                        return 0;
                    }

                    public void shutdownReads() throws IOException {
                    }

                    public void awaitReadable() throws IOException {
                    }

                    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
                    }

                    public int read(final ByteBuffer dst) throws IOException {
                        return 0;
                    }

                    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
                        return 0;
                    }

                    public long read(final ByteBuffer[] dsts) throws IOException {
                        return 0;
                    }

                    public <T> T getOption(final ChannelOption<T> option) throws UnsupportedOptionException, IOException {
                        return null;
                    }

                    public Set<ChannelOption<?>> getOptions() {
                        return null;
                    }

                    public <T> Configurable setOption(final ChannelOption<T> option, final T value) throws IllegalArgumentException, IOException {
                        return null;
                    }
                };
                ioHandler.handleOpened(channel);
                return new FinishedIoFuture<StreamChannel>(channel);
            }
        };
        final IoHandler<StreamChannel> testHandler = new IoHandler<StreamChannel>() {
            public void handleOpened(final StreamChannel channel) {
                try {
                    channel.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            public void handleReadable(final StreamChannel channel) {
                fail("wrong method");
            }

            public void handleWritable(final StreamChannel channel) {
                fail("wrong method");
            }

            public void handleClosed(final StreamChannel channel) {
                statuses[0] = true;
            }
        };
        final Executor testExecutor = new Executor() {
            public void execute(final Runnable command) {
                statuses[1] = true;
            }
        };
        final Closeable connection = IoUtils.createConnection(testChannelSource, testHandler, testExecutor);
        connection.close();
        for (int i = 0; i < statuses.length; i++) {
            boolean t = statuses[i];
            assertTrue("Flag " + i, t);
        }
    }

    public void testDelayedExecutor() {
        final boolean[] ok = new boolean[1];
        final Runnable task = new Runnable() {
            public void run() {
            }
        };
        IoUtils.delayedExecutor(new ScheduledExecutorService() {
            public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
                assertSame(task, command);
                ok[0] = true;
                return new ScheduledFuture<Object>() {
                    public long getDelay(final TimeUnit unit) {
                        return 0;
                    }

                    public int compareTo(final Delayed o) {
                        return 0;
                    }

                    public boolean cancel(final boolean mayInterruptIfRunning) {
                        return false;
                    }

                    public boolean isCancelled() {
                        return false;
                    }

                    public boolean isDone() {
                        return false;
                    }

                    public Object get() throws InterruptedException, ExecutionException {
                        return null;
                    }

                    public Object get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                        return null;
                    }
                };
            }

            public <V> ScheduledFuture<V> schedule(final Callable<V> callable, final long delay, final TimeUnit unit) {
                return null;
            }

            public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command, final long initialDelay, final long period, final TimeUnit unit) {
                return null;
            }

            public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command, final long initialDelay, final long delay, final TimeUnit unit) {
                return null;
            }

            public void shutdown() {
            }

            public List<Runnable> shutdownNow() {
                return null;
            }

            public boolean isShutdown() {
                return false;
            }

            public boolean isTerminated() {
                return false;
            }

            public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
                return false;
            }

            public <T> Future<T> submit(final Callable<T> task) {
                return null;
            }

            public <T> Future<T> submit(final Runnable task, final T result) {
                return null;
            }

            public Future<?> submit(final Runnable task) {
                return null;
            }

            public List invokeAll(final Collection tasks) throws InterruptedException {
                return null;
            }

            public List invokeAll(final Collection tasks, final long timeout, final TimeUnit unit) throws InterruptedException {
                return null;
            }

            public Object invokeAny(final Collection tasks) throws InterruptedException, ExecutionException {
                return null;
            }

            public Object invokeAny(final Collection tasks, final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return null;
            }

            public void execute(final Runnable command) {
            }
        }, 1234L, TimeUnit.MICROSECONDS).execute(task);
        assertTrue(ok[0]);
    }

    public void testDirectExecutor() {
        final Thread t = Thread.currentThread();
        final boolean ok[] = new boolean[1];
        IoUtils.directExecutor().execute(new Runnable() {
            public void run() {
                assertSame(t, Thread.currentThread());
                ok[0] = true;
            }
        });
        assertTrue(ok[0]);
    }

    public void testNullExecutor() {
        IoUtils.nullExecutor().execute(new Runnable() {
            public void run() {
                fail("null executor ran task");
            }
        });
    }

    public void testSafeClose() {
        IoUtils.safeClose(new Closeable() {
            public void close() throws IOException {
                throw new RuntimeException("This error should be consumed but logged");
            }
        });
        IoUtils.safeClose(new Closeable() {
            public void close() throws IOException {
                throw new Error("This error should be consumed but logged");
            }
        });
        IoUtils.safeClose(new Closeable() {
            public void close() throws IOException {
                throw new IOException("This error should be consumed but logged");
            }
        });
    }
}
