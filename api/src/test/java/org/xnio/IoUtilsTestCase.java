/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates, and individual
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

package org.xnio;

import static org.xnio.AssertReadWrite.assertWrittenMessage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import junit.framework.TestCase;

import org.xnio.IoFuture.Status;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * Test for {@link IoUtils}.
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public final class IoUtilsTestCase extends TestCase {

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
        assertNotNull(IoUtils.directExecutor().toString());
    }

    public void testNullExecutor() {
        IoUtils.nullExecutor().execute(new Runnable() {
            public void run() {
                fail("null executor ran task");
            }
        });
        assertNotNull(IoUtils.nullExecutor().toString());
    }

    public void testNullCloseable() throws IOException {
        //nothing should happen
        IoUtils.nullCloseable().close();
        IoUtils.nullCloseable().close();
        IoUtils.nullCloseable().close();
        IoUtils.nullCloseable().close();
        IoUtils.nullCloseable().close();
        assertNotNull(IoUtils.nullCloseable().toString());
    }

    public void testNullCancellable() throws IOException {
        //nothing should happen
        assertSame(IoUtils.nullCancellable(), IoUtils.nullCancellable().cancel());
        assertSame(IoUtils.nullCancellable(), IoUtils.nullCancellable().cancel());
        assertSame(IoUtils.nullCancellable(), IoUtils.nullCancellable().cancel());
        assertSame(IoUtils.nullCancellable(), IoUtils.nullCancellable().cancel());
        assertNotNull(IoUtils.nullCancellable().toString());
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
        IoUtils.safeClose(new Closeable() {
            public void close() throws IOException {
                throw new ClosedChannelException(); // this error should be ignored
            }
        });
        IoUtils.safeClose((Closeable) null);        // should do nothing if target is null
    }

    public void testSafeCloseSocket() {
        IoUtils.safeClose(new Socket() {
            public void close() throws IOException {
                throw new RuntimeException("This error should be consumed but logged");
            }
        });
        IoUtils.safeClose(new Socket() {
            public void close() throws IOException {
                throw new Error("This error should be consumed but logged");
            }
        });
        IoUtils.safeClose(new Socket() {
            public void close() throws IOException {
                throw new IOException("This error should be consumed but logged");
            }
        });
        IoUtils.safeClose(new Socket() {
            public void close() throws IOException {
                throw new ClosedChannelException(); // this error should be ignored
            }
        });
        IoUtils.safeClose((Socket) null);           // should do nothing if target is null
    }

    public void testSafeCloseDatagramSocket() throws SocketException {
        IoUtils.safeClose(new DatagramSocket() {
            public void close() {
                throw new RuntimeException("This error should be consumed but logged");
            }
        });
        IoUtils.safeClose(new DatagramSocket() {
            public void close() {
                throw new Error("This error should be consumed but logged");
            }
        });
        IoUtils.safeClose((DatagramSocket) null);   // should do nothing if target is null
    }

    public void testSafeCloseSelector() {
        IoUtils.safeClose(new TestSelector() {
            public void close() throws IOException {
                throw new RuntimeException("This error should be consumed but logged");
            }
        });
        IoUtils.safeClose(new TestSelector() {
            public void close() throws IOException {
                throw new Error("This error should be consumed but logged");
            }
        });
        IoUtils.safeClose(new TestSelector() {
            public void close() throws IOException {
                throw new IOException("This error should be consumed but logged");
            }
        });
        IoUtils.safeClose(new TestSelector() {
            public void close() throws IOException {
                throw new ClosedChannelException(); // this error should be ignored
            }
        });
        IoUtils.safeClose((Selector) null);        // should do nothing if target is null
    }

    public void testSafeCloseServerSocket() throws IOException {
        IoUtils.safeClose(new ServerSocket() {
            public void close() throws IOException {
                throw new RuntimeException("This error should be consumed but logged");
            }
        });
        IoUtils.safeClose(new ServerSocket() {
            public void close() throws IOException {
                throw new Error("This error should be consumed but logged");
            }
        });
        IoUtils.safeClose(new ServerSocket() {
            public void close() throws IOException {
                throw new IOException("This error should be consumed but logged");
            }
        });
        IoUtils.safeClose(new ServerSocket() {
            public void close() throws IOException {
                throw new ClosedChannelException(); // this error should be ignored
            }
        });
        IoUtils.safeClose((ServerSocket) null);     // should do nothing if target is null
    }

    public void testSafeCloseZipFile() throws IOException {
        final File file = File.createTempFile("foo", ".zip");
        file.deleteOnExit();
        final ZipOutputStream zipOutput = new ZipOutputStream(new FileOutputStream(file));
        zipOutput.close();

        IoUtils.safeClose(new ZipFile(file) {
            public void close() throws IOException {
                throw new RuntimeException("This error should be consumed but logged");
            }
        });
        IoUtils.safeClose(new ZipFile(file) {
            public void close() throws IOException {
                throw new Error("This error should be consumed but logged");
            }
        });
        IoUtils.safeClose(new ZipFile(file) {
            public void close() throws IOException {
                throw new IOException("This error should be consumed but logged");
            }
        });
        IoUtils.safeClose((ZipFile) null);         // should do nothing if target is null
    }

    public void testSafeCloseHandler() throws IOException {
        IoUtils.safeClose(new Handler() {
            public void close() {
                throw new RuntimeException("This error should be consumed but logged");
            }
            @Override public void publish(LogRecord record) {}
            @Override public void flush() {}
        });
        IoUtils.safeClose(new Handler() {
            public void close() {
                throw new Error("This error should be consumed but logged");
            }
            @Override public void publish(LogRecord record) {}
            @Override public void flush() {}
        });
        IoUtils.safeClose(new Handler() {
            public void close() throws SecurityException {
                throw new SecurityException("This error should be consumed but logged");
            }
            @Override public void publish(LogRecord record) {}
            @Override public void flush() {}
        });
        IoUtils.safeClose((Handler) null);         // should do nothing if target is null
    }

    public void testSafeCloseIoFuture() {
        final TestIoFuture<Closeable> future1 = new TestIoFuture<Closeable>();
        IoUtils.safeClose(future1);
        assertEquals(IoFuture.Status.CANCELLED, future1.getStatus());

        final TestIoFuture<Closeable> future2 = new TestIoFuture<Closeable>();
        final TestCloseable closeable = new TestCloseable();
        assertTrue(future2.setResult(closeable));
        assertTrue(closeable.isOpen());
        IoUtils.safeClose(future2);
        assertEquals(IoFuture.Status.DONE, future2.getStatus());
        assertFalse(closeable.isOpen());

        // should do nothing if target is null
        IoUtils.safeClose((IoFuture<? extends Closeable>) null);
    }

    public void testAttachmentClosingNotifier() {
        final TestCloseable closeable = new TestCloseable();
        assertTrue(closeable.isOpen());
        IoUtils.attachmentClosingNotifier().notify(null, closeable);
        assertFalse(closeable.isOpen());
    }

    public void testClosingNotifier() {
        final TestCloseable closeable = new TestCloseable();
        final TestIoFuture<Closeable> future = new TestIoFuture<Closeable>();
        future.addNotifier(IoUtils.closingNotifier(), null);
        assertTrue(closeable.isOpen());
        future.setResult(closeable);
        assertFalse(closeable.isOpen());
    }

    public void testRunnableNotifier() {
        final TestRunnable testRunnable = new TestRunnable();
        final IoFuture.Notifier<Void, Void> notifier = IoUtils.runnableNotifier(testRunnable);
        assertFalse(testRunnable.isInvoked());

        notifier.notify(null, null);
        assertTrue(testRunnable.isInvoked());
    }

    public void testResultNotifier() throws Exception {
        final FutureResult<Closeable> futureResult1 = new FutureResult<Closeable>();
        final FutureResult<Closeable> futureResult2 = new FutureResult<Closeable>();
        final FutureResult<Closeable> futureResult3 = new FutureResult<Closeable>();

        final TestIoFuture<Closeable> future1 = new TestIoFuture<Closeable>();
        final TestIoFuture<Closeable> future2 = new TestIoFuture<Closeable>();
        final TestIoFuture<Closeable> future3 = new TestIoFuture<Closeable>();

        future1.addNotifier(IoUtils.<Closeable>resultNotifier(), futureResult1);
        future2.addNotifier(IoUtils.<Closeable>resultNotifier(), futureResult2);
        future3.addNotifier(IoUtils.<Closeable>resultNotifier(), futureResult3);

        future1.cancel();
        assertSame(Status.CANCELLED, futureResult1.getIoFuture().getStatus());

        final IOException exception = new IOException("Test exception");
        future2.setException(exception);
        assertSame(Status.FAILED, futureResult2.getIoFuture().getStatus());
        assertSame(exception, futureResult2.getIoFuture().getException());

        final Closeable result = new TestCloseable();
        future3.setResult(result);
        assertSame(Status.DONE, futureResult3.getIoFuture().getStatus());
        assertSame(result, futureResult3.getIoFuture().get());
    }

    public void testChannelListenerNotifier() {
        final ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
        final TestChannelListener channelListener = new TestChannelListener();

        final TestIoFuture<ConnectedStreamChannel> future1 = new TestIoFuture<ConnectedStreamChannel>();
        future1.<ChannelListener<? super ConnectedStreamChannel>>addNotifier(
                IoUtils.<ConnectedStreamChannel>channelListenerNotifier(), channelListener);
        final TestIoFuture<ConnectedStreamChannel> future2 = new TestIoFuture<ConnectedStreamChannel>();
        future2.<ChannelListener<? super ConnectedStreamChannel>>addNotifier(
                IoUtils.<ConnectedStreamChannel>channelListenerNotifier(), channelListener);
        final TestIoFuture<ConnectedStreamChannel> future3 = new TestIoFuture<ConnectedStreamChannel>();
        future3.<ChannelListener<? super ConnectedStreamChannel>>addNotifier(
                IoUtils.<ConnectedStreamChannel>channelListenerNotifier(), channelListener);

        future1.cancel();
        future2.setException(new IOException());
        assertFalse(channelListener.isInvoked());
        future3.setResult(channel);
        assertTrue(channelListener.isInvoked());
        assertSame(channel, channelListener.getChannel());
    }

    public void testIoFutureWrapper() throws Exception {
        // test future wrapper with ioFuture1, which will have a value successfully set
        final TestIoFuture<String> ioFuture1 = new TestIoFuture<String>();
        final Future<String> future1 = IoUtils.getFuture(ioFuture1);

        assertFalse(future1.isCancelled());
        assertFalse(future1.isDone());
        assertNotNull(future1.toString());

        final FutureValueRetriever<String> futureValueRetriever1 = new FutureValueRetriever<String>(future1);
        final FutureValueRetriever<String> futureValueRetriever2 = new FutureValueRetriever<String>(future1, 50, TimeUnit.MILLISECONDS);
        final Thread retrieverThread1 = new Thread(futureValueRetriever1);
        final Thread retrieverThread2 = new Thread(futureValueRetriever2);
        retrieverThread1.start();
        retrieverThread2.start();
        retrieverThread1.join(80);
        retrieverThread2.join(150);
        assertTrue(retrieverThread1.isAlive());
        assertFalse(retrieverThread2.isAlive());
        assertNotNull(futureValueRetriever2.getTimeoutException());
        ioFuture1.setResult("future1");
        retrieverThread1.join();
        assertSame("future1", futureValueRetriever1.getFutureValue());
        assertFalse(future1.isCancelled());
        assertTrue(future1.isDone());
        assertFalse(future1.cancel(true));

        // test future wrapper with ioFuture2, which will be canceled, by a call to ioFuture2.cancel()
        final TestIoFuture<Integer> ioFuture2 = new TestIoFuture<Integer>();
        final Future<Integer> future2 = IoUtils.getFuture(ioFuture2);

        assertFalse(future2.isCancelled());
        assertFalse(future2.isDone());
        assertNotNull(future2.toString());

        final FutureValueRetriever<Integer> futureValueRetriever3 = new FutureValueRetriever<Integer>(future2);
        final FutureValueRetriever<Integer> futureValueRetriever4 = new FutureValueRetriever<Integer>(future2, 1, TimeUnit.DAYS);
        final Thread retrieverThread3 = new Thread(futureValueRetriever3);
        final Thread retrieverThread4 = new Thread(futureValueRetriever4);
        retrieverThread3.start();
        retrieverThread4.start();
        retrieverThread3.join(60);
        retrieverThread4.join(60);
        assertTrue(retrieverThread3.isAlive());
        assertTrue(retrieverThread4.isAlive());

        ioFuture2.cancel();
        retrieverThread3.join();
        retrieverThread4.join();
        assertNotNull(futureValueRetriever3.getCancellationException());
        assertNotNull(futureValueRetriever4.getCancellationException());
        assertTrue(future2.isCancelled());
        assertFalse(future2.isDone());
        assertTrue(future2.cancel(false));

        // test future wrapper with ioFuture3, which will be canceled, by a call to the future wrapper
        final TestIoFuture<Integer> ioFuture3 = new TestIoFuture<Integer>();
        final Future<Integer> future3 = IoUtils.getFuture(ioFuture3);

        assertFalse(future3.isCancelled());
        assertFalse(future3.isDone());
        assertNotNull(future3.toString());

        final FutureValueRetriever<Integer> futureValueRetriever5 = new FutureValueRetriever<Integer>(future3);
        final FutureValueRetriever<Integer> futureValueRetriever6 = new FutureValueRetriever<Integer>(future3, 10, TimeUnit.NANOSECONDS);
        final Thread retrieverThread5 = new Thread(futureValueRetriever5);
        final Thread retrieverThread6 = new Thread(futureValueRetriever6);
        retrieverThread5.start();
        retrieverThread6.start();
        retrieverThread5.join(60);
        retrieverThread6.join(60);
        assertTrue(retrieverThread5.isAlive());
        assertFalse(retrieverThread6.isAlive());
        assertNotNull(futureValueRetriever6.getTimeoutException());

        assertTrue(future3.cancel(true));
        retrieverThread5.join();
        assertNotNull(futureValueRetriever5.getCancellationException());
        assertTrue(future3.isCancelled());
        assertFalse(future3.isDone());
        assertTrue(future3.cancel(true));

        // test future wrapper with ioFuture3, which will be canceled, by a call to the future wrapper
        final TestIoFuture<Void> ioFuture4 = new TestIoFuture<Void>();
        final Future<Void> future4 = IoUtils.getFuture(ioFuture4);
        assertFalse(future4.isCancelled());
        assertFalse(future4.isDone());
        assertNotNull(future4.toString());

        final FutureValueRetriever<Void> futureValueRetriever7 = new FutureValueRetriever<Void>(future4);
        final FutureValueRetriever<Void> futureValueRetriever8 = new FutureValueRetriever<Void>(future4, 1, TimeUnit.MINUTES);
        final Thread retrieverThread7 = new Thread(futureValueRetriever7);
        final Thread retrieverThread8 = new Thread(futureValueRetriever8);
        retrieverThread7.start();
        retrieverThread8.start();
        retrieverThread7.join(60);
        retrieverThread8.join(60);
        assertTrue(retrieverThread7.isAlive());
        assertTrue(retrieverThread8.isAlive());

        final IOException failure = new IOException("Test exception");
        ioFuture4.setException(failure);
        retrieverThread7.join();
        retrieverThread8.join();
        assertSame(failure, futureValueRetriever7.getFailure());
        assertSame(failure, futureValueRetriever8.getFailure());

        assertFalse(future4.isCancelled());
        assertFalse(future4.isDone());
        assertFalse(future4.cancel(false));
    }

    public void testAwaitAll() throws InterruptedException {
        final TestIoFuture<Void> future1 = new TestIoFuture<Void>();
        final TestIoFuture<Void> future2 = new TestIoFuture<Void>();
        final TestIoFuture<Void> future3 = new TestIoFuture<Void>();
        final TestIoFuture<Void> future4 = new TestIoFuture<Void>();
        final TestIoFuture<Void> future5 = new TestIoFuture<Void>();

        final Awaiter awaiterTask1 = new Awaiter(future1, future2, future3, future4, future5);
        final Awaiter awaiterTask2 = new Awaiter(future1, future3, future4);
        final Awaiter awaiterTask3 = new Awaiter(future1, future2, future4, future5);
        final Awaiter awaiterTask4 = new Awaiter(true, future1, future3, future4);
        final Awaiter awaiterTask5 = new Awaiter();
        final Thread awaiterThread1 = new Thread(awaiterTask1);
        final Thread awaiterThread2 = new Thread(awaiterTask2);
        final Thread awaiterThread3 = new Thread(awaiterTask3);
        final Thread awaiterThread4 = new Thread(awaiterTask4);
        final Thread awaiterThread5 = new Thread(awaiterTask5);
        awaiterThread1.start();
        awaiterThread2.start();
        awaiterThread3.start();
        awaiterThread4.start();
        awaiterThread5.start();
        awaiterThread1.join(100);
        awaiterThread2.join(100);
        awaiterThread3.join(100);
        awaiterThread4.join(100);
        awaiterThread5.join();
        assertTrue(awaiterThread1.isAlive());
        assertTrue(awaiterThread2.isAlive());
        assertTrue(awaiterThread3.isAlive());
        assertTrue(awaiterThread4.isAlive());

        future2.setResult(null);
        future3.cancel();
        awaiterThread1.join(100);
        awaiterThread2.join(100);
        awaiterThread3.join(100);
        awaiterThread4.join(100);
        assertTrue(awaiterThread1.isAlive());
        assertTrue(awaiterThread2.isAlive());
        assertTrue(awaiterThread3.isAlive());
        assertTrue(awaiterThread4.isAlive());

        awaiterThread3.interrupt();
        awaiterThread4.interrupt();
        awaiterThread4.join();
        assertNotNull(awaiterTask4.getInterruptedException());

        future1.setResult(null);
        future4.setException(new IOException());
        awaiterThread1.join(100);
        awaiterThread3.join(100);
        awaiterThread2.join();
        assertTrue(awaiterThread1.isAlive());
        assertTrue(awaiterThread3.isAlive());

        future5.setResult(null);
        awaiterThread1.join();
        awaiterThread3.join();
        assertNull(awaiterTask3.getInterruptedException());
    }

    public void testCast() throws Exception {
        // future with result set
        final TestIoFuture<Object> future1 = new TestIoFuture<Object>();
        final IoFuture<? extends String> castFuture1 = IoUtils.<Object, String>cast(future1, String.class);

        assertSame(Status.WAITING, castFuture1.getStatus());
        assertSame(Status.WAITING, castFuture1.await(10, TimeUnit.MICROSECONDS));
        assertSame(Status.WAITING, castFuture1.awaitInterruptibly(1, TimeUnit.MICROSECONDS));

        future1.setResult("test");
        assertSame(Status.DONE, castFuture1.getStatus());
        assertSame(Status.DONE, castFuture1.await(10, TimeUnit.MICROSECONDS));
        assertSame(Status.DONE, castFuture1.awaitInterruptibly(1, TimeUnit.MICROSECONDS));
        assertSame(Status.DONE, castFuture1.await());
        assertSame(Status.DONE, castFuture1.awaitInterruptibly());
        assertSame("test", castFuture1.get());
        assertSame("test", castFuture1.getInterruptibly());

        final TestCloseable closeable1 = new TestCloseable();
        castFuture1.addNotifier(IoUtils.attachmentClosingNotifier(), closeable1);
        assertFalse(closeable1.isOpen());

        // cancelled future
        final TestIoFuture<Object> future2 = new TestIoFuture<Object>();
        final IoFuture<? extends String> castFuture2 = IoUtils.<Object, String>cast(future2, String.class);
        final TestCloseable closeable2 = new TestCloseable();
        castFuture2.addNotifier(IoUtils.attachmentClosingNotifier(), closeable2);
        assertTrue(closeable2.isOpen());

        assertSame(Status.WAITING, castFuture2.getStatus());
        assertSame(Status.WAITING, castFuture2.await(10, TimeUnit.MICROSECONDS));
        assertSame(Status.WAITING, castFuture2.awaitInterruptibly(1, TimeUnit.MICROSECONDS));

        castFuture2.cancel();
        assertSame(Status.CANCELLED, castFuture2.getStatus());
        assertSame(Status.CANCELLED, castFuture2.await(10, TimeUnit.MICROSECONDS));
        assertSame(Status.CANCELLED, castFuture2.awaitInterruptibly(1, TimeUnit.MICROSECONDS));
        assertSame(Status.CANCELLED, castFuture2.await());
        assertSame(Status.CANCELLED, castFuture2.awaitInterruptibly());
        assertFalse(closeable2.isOpen());
        CancellationException expected = null;
        try {
            castFuture2.get();
        } catch (CancellationException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            castFuture2.getInterruptibly();
        } catch (CancellationException e) {
            expected = e;
        }
        assertNotNull(expected);

        // failed future
        final TestIoFuture<Object> future3 = new TestIoFuture<Object>();
        final IoFuture<? extends String> castFuture3 = IoUtils.<Object, String>cast(future3, String.class);


        assertSame(Status.WAITING, castFuture3.getStatus());
        assertSame(Status.WAITING, castFuture3.await(10, TimeUnit.MICROSECONDS));
        assertSame(Status.WAITING, castFuture3.awaitInterruptibly(1, TimeUnit.MICROSECONDS));

        final IOException failure = new IOException("Test exception");
        future3.setException(failure);
        assertSame(Status.FAILED, castFuture3.getStatus());
        assertSame(Status.FAILED, castFuture3.await(10, TimeUnit.MICROSECONDS));
        assertSame(Status.FAILED, castFuture3.awaitInterruptibly(1, TimeUnit.MICROSECONDS));
        assertSame(Status.FAILED, castFuture3.await());
        assertSame(Status.FAILED, castFuture3.awaitInterruptibly());
        assertSame(failure, castFuture3.getException());
    }

    public void testSafeShutdownReads() {
        final ChannelMock channel1 = new ChannelMock();
        final ChannelMock channel2 = new ChannelMock();

        IoUtils.safeShutdownReads(channel1);
        assertTrue(channel1.isShutdownReads());

        channel2.throwExceptionOnShutdownReads();
        IoUtils.safeShutdownReads(channel2);
        assertFalse(channel2.isShutdownReads());

        IoUtils.safeShutdownReads(null); // should just ignore
    }

    public void testTransfer() throws IOException {
        final ConnectedStreamChannelMock sourceChannel = new ConnectedStreamChannelMock();
        final ConnectedStreamChannelMock sinkChannel = new ConnectedStreamChannelMock();
        sinkChannel.enableWrite(false);
        sourceChannel.setReadData("a kinda big text to transfer from source to sink");
        sourceChannel.enableRead(true);

        final ByteBuffer throughBuffer = ByteBuffer.allocate(10);
        assertEquals(0, IoUtils.transfer(sourceChannel, 50, throughBuffer, sinkChannel));

        assertWrittenMessage(sinkChannel);

        sinkChannel.enableWrite(true);
        sinkChannel.write(throughBuffer);

        assertEquals(38, IoUtils.transfer(sourceChannel, 60, throughBuffer, sinkChannel));
        assertWrittenMessage(sinkChannel, "a kinda big text to transfer from source to sink");
    }

    public void testManagerNotifier() throws Exception {
        // add manager notifier to a future that will be cancelled
        final TestIoFuture<Channel> future1 = new TestIoFuture<Channel>();
        final FutureResult<Channel> manager1 = new FutureResult<Channel>();
        final FutureResult<Channel> manager2 = new FutureResult<Channel>();
        final IoFuture.Notifier<Channel, FutureResult<Channel>> notifier1 = IoUtils.<Channel>getManagerNotifier();
        final IoFuture.Notifier<Channel, FutureResult<Channel>> notifier2 = IoUtils.<Channel>getManagerNotifier();
        future1.addNotifier(notifier1, manager1);
        future1.cancel();
        future1.addNotifier(notifier2, manager2);
        assertSame(Status.CANCELLED, manager1.getIoFuture().getStatus());
        assertSame(Status.CANCELLED, manager2.getIoFuture().getStatus());

        // add manager notifier to a future that will fail
        final IOException exception = new IOException("Test exception");
        final TestIoFuture<Channel> future2 = new TestIoFuture<Channel>();
        final FutureResult<Channel> manager3 = new FutureResult<Channel>();
        final FutureResult<Channel> manager4 = new FutureResult<Channel>();
        final IoFuture.Notifier<Channel, FutureResult<Channel>> notifier3 = IoUtils.<Channel>getManagerNotifier();
        final IoFuture.Notifier<Channel, FutureResult<Channel>> notifier4 = IoUtils.<Channel>getManagerNotifier();
        future2.addNotifier(notifier3, manager3);
        future2.setException(exception);
        future2.addNotifier(notifier4, manager4);
        assertSame(Status.FAILED, manager3.getIoFuture().getStatus());
        assertSame(exception, manager3.getIoFuture().getException());
        assertSame(Status.FAILED, manager4.getIoFuture().getStatus());
        assertSame(exception, manager4.getIoFuture().getException());

        // add manager notifier to a future that will have its value set
        final Channel result = new ConnectedStreamChannelMock();
        final TestIoFuture<Channel> future3 = new TestIoFuture<Channel>();
        final FutureResult<Channel> manager5 = new FutureResult<Channel>();
        final FutureResult<Channel> manager6 = new FutureResult<Channel>();
        final IoFuture.Notifier<Channel, FutureResult<Channel>> notifier5 = IoUtils.<Channel>getManagerNotifier();
        final IoFuture.Notifier<Channel, FutureResult<Channel>> notifier6 = IoUtils.<Channel>getManagerNotifier();
        future3.addNotifier(notifier5, manager5);
        future3.setResult(result);
        future3.addNotifier(notifier6, manager6);
        assertSame(Status.DONE, manager5.getIoFuture().getStatus());
        assertSame(result, manager5.getIoFuture().get());
        assertSame(Status.DONE, manager6.getIoFuture().getStatus());
        assertSame(result, manager6.getIoFuture().get());
    }

    public void testRetryingChannelSource() throws Exception {
        final TestChannelSource testChannelSource1 = new TestChannelSource(0);
        final TestChannelSource testChannelSource2 = new TestChannelSource(3);
        final TestChannelSource testChannelSource3 = new TestChannelSource(8);
        final ChannelSource<ConnectedStreamChannelMock> testChannelSource4 = new ChannelSource<ConnectedStreamChannelMock>() {
            @Override
            public IoFuture<ConnectedStreamChannelMock> open(ChannelListener<? super ConnectedStreamChannelMock> openListener) {
                return  new TestIoFuture<ConnectedStreamChannelMock>().cancel();
            }
        };

        final ChannelSource<ConnectedStreamChannelMock> retryingChannelSource1 = IoUtils.getRetryingChannelSource(testChannelSource1, 5);
        final ChannelSource<ConnectedStreamChannelMock> retryingChannelSource2 = IoUtils.getRetryingChannelSource(testChannelSource2, 5);
        final ChannelSource<ConnectedStreamChannelMock> retryingChannelSource3 = IoUtils.getRetryingChannelSource(testChannelSource3, 5);
        final ChannelSource<ConnectedStreamChannelMock> retryingChannelSource4 = IoUtils.getRetryingChannelSource(testChannelSource4, 5);
        final TestOpenListener openListener1 = new TestOpenListener();
        final TestOpenListener openListener2 = new TestOpenListener();
        final TestOpenListener openListener3 = new TestOpenListener();
        final TestOpenListener openListener4 = new TestOpenListener();

        final IoFuture<ConnectedStreamChannelMock> future1 = retryingChannelSource1.open(openListener1);
        final IoFuture<ConnectedStreamChannelMock> future2 = retryingChannelSource2.open(openListener2);
        final IoFuture<ConnectedStreamChannelMock> future3 = retryingChannelSource3.open(openListener3);
        final IoFuture<ConnectedStreamChannelMock> future4 = retryingChannelSource4.open(openListener4);

        assertSame(Status.DONE, future1.getStatus());
        assertNotNull(future1.get());
        assertTrue(openListener1.isInvoked());
        assertSame(future1.get(), openListener1.getChannel());

        assertSame(Status.DONE, future2.getStatus());
        assertNotNull(future2.get());
        assertTrue(openListener2.isInvoked());
        assertSame(future2.get(), openListener2.getChannel());

        assertSame(Status.FAILED, future3.getStatus());
        assertNotNull(future3.getException());
        assertFalse(openListener3.isInvoked());

        assertSame(Status.CANCELLED, future4.getStatus());
        assertFalse(openListener3.isInvoked());

        IllegalArgumentException expected = null;
        try {
            IoUtils.getRetryingChannelSource(testChannelSource1, 0);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            IoUtils.getRetryingChannelSource(testChannelSource1, -1);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            IoUtils.getRetryingChannelSource(testChannelSource1, -9);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    public void testClosingCancellable() {
        final TestCloseable closeable = new TestCloseable();
        final Cancellable closingCancellable = IoUtils.closingCancellable(closeable);
        assertNotNull(closingCancellable);
        assertTrue(closeable.isOpen());
        assertSame(closingCancellable, closingCancellable.cancel());
        assertFalse(closeable.isOpen());
    }

    public void testThreadLocalRandom() throws Exception {
        final Random random = IoUtils.getThreadLocalRandom();
        random.nextFloat();
        random.nextInt();

        final ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
        final ObjectOutput objectOutput = new ObjectOutputStream(new BufferedOutputStream(byteOutput));
        objectOutput.writeObject(random);
        objectOutput.close();
        final ObjectInput objectInput = new ObjectInputStream(new BufferedInputStream(new ByteArrayInputStream(byteOutput.toByteArray())));
        final Random deserializedRandom = (Random) objectInput.readObject();
        assertNotNull(deserializedRandom);
    }

    public void testTransferThroughBuffer() throws IOException{
        byte[] bytes = "This bytes".getBytes();
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer buffer = ByteBuffer.allocate(16);
        assertEquals(bytes.length, IoUtils.transfer(Channels.newChannel(in), bytes.length, buffer, Channels.newChannel(out)));
        assertFalse(buffer.hasRemaining());
    }
    private static abstract class TestSelector extends Selector {
        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public SelectorProvider provider() {
            return null;
        }

        @Override
        public Set<SelectionKey> keys() {
            return null;
        }

        @Override
        public Set<SelectionKey> selectedKeys() {
            return null;
        }

        @Override
        public int selectNow() throws IOException {
            return 0;
        }

        @Override
        public int select(long timeout) throws IOException {
            return 0;
        }

        @Override
        public int select() throws IOException {
            return 0;
        }

        @Override
        public Selector wakeup() {
            return null;
        }
    }

    private static class TestIoFuture<T> extends AbstractIoFuture<T> {

        @Override
        public boolean setResult(T result) {
            return super.setResult(result);
        }

        @Override
        public boolean setException(IOException exception) {
            return super.setException(exception);
        }

        @Override
        public TestIoFuture<T> cancel() {
            super.cancel();
            setCancelled();
            return this;
        }
    }

    private static class TestCloseable implements Closeable {
        private boolean open = true;

        public void close() {
            open = false;
        }

        public boolean isOpen() {
            return open;
        }
    }

    private static class TestRunnable implements Runnable {
        private boolean invoked = false;

        public void run() {
            invoked = true;
        }

        public boolean isInvoked() {
            return invoked;
        }
    }

    private static class TestChannelListener implements ChannelListener<ConnectedStreamChannel> {

        private boolean invoked = false;
        private ConnectedStreamChannel channel;

        @Override
        public void handleEvent(ConnectedStreamChannel c) {
            invoked = true;
            channel = c;
        }

        public boolean isInvoked() {
            return invoked;
        }

        public ConnectedStreamChannel getChannel() {
            return channel;
        }
    }

    private static class FutureValueRetriever<T> implements Runnable {
        private final Future<T> future;
        private final long timeout;
        private final TimeUnit timeoutUnit;
        private volatile T result;
        private TimeoutException timeoutException = null;
        private CancellationException cancellationException = null;
        private IOException failure;

        public FutureValueRetriever(Future<T> f) {
            this(f, -1, null);
        }

        public FutureValueRetriever(Future<T> f, long t, TimeUnit tu) {
            future = f;
            timeout = t;
            timeoutUnit = tu;
        }

        public void run() {
            try {
            if (timeoutUnit == null) {
                result = future.get();
            } else {
                result = future.get(timeout, timeoutUnit);
            }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof IOException) {
                    failure = (IOException) e.getCause();
                } else {
                    throw new RuntimeException(e);
                }
            } catch (TimeoutException e) {
                timeoutException = e;
            } catch (CancellationException e) {
                cancellationException = e;
            }
        }

        public T getFutureValue() {
            return result;
        }

        public TimeoutException getTimeoutException() {
            return timeoutException;
        }

        public CancellationException getCancellationException() {
            return cancellationException;
        }

        public IOException getFailure() {
            return failure;
        }
    }

    private class Awaiter implements Runnable {
        private boolean interruptibly;
        private IoFuture<?>[] futures;
        private InterruptedException exception;

        public Awaiter(IoFuture<?>... f) {
            this(false, f);
        }

        public Awaiter(boolean i, IoFuture<?>... f) {
            futures = f;
            interruptibly = i;
        }

        public void run() {
            try {
                if (interruptibly) {
                    IoUtils.awaitAllInterruptibly(futures);
                } else {
                    IoUtils.awaitAll(futures);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                exception = e;
            } finally {}
        }

        public InterruptedException getInterruptedException() {
            return exception;
        }
    }

    private static class ChannelMock extends ConnectedStreamChannelMock {
        private boolean throwExceptionOnShutdownReads = false;
        
        public void throwExceptionOnShutdownReads() {
            throwExceptionOnShutdownReads = true;
        }

        @Override
        public void shutdownReads() throws IOException {
            if (throwExceptionOnShutdownReads) {
                throw new IOException("Test exception");
            }
            super.shutdownReads();
        }
    }

    private static class TestChannelSource implements ChannelSource<ConnectedStreamChannelMock> {

        public int count;

        public TestChannelSource(int numberOfFailures) {
            count = numberOfFailures;
        }

        @Override
        public IoFuture<ConnectedStreamChannelMock> open(ChannelListener<? super ConnectedStreamChannelMock> openListener) {
            if (count == 0) {
                IoFuture<ConnectedStreamChannelMock> future = new FinishedIoFuture<ConnectedStreamChannelMock>(new ConnectedStreamChannelMock());
                try {
                    openListener.handleEvent(future.get());
                } catch (CancellationException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return future;
            }
            count --;
            return new FailedIoFuture<ConnectedStreamChannelMock>(new IOException("Test exception"));
        }
    }

    private static class TestOpenListener implements ChannelListener<ConnectedStreamChannelMock> {

        private boolean invoked;
        private ConnectedStreamChannelMock channel;

        @Override
        public void handleEvent(ConnectedStreamChannelMock c) {
            invoked = true;
            channel = c;
        }

        public boolean isInvoked() {
            return invoked;
        }

        public ConnectedStreamChannelMock getChannel() {
            return channel;
        }
    }
}
