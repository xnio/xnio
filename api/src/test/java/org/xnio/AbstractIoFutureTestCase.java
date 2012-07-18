/*
 * JBoss, Home of Professional Open Source.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.xnio.IoFuture.Status;

/**
 * Test for {@link AbstractIoFuture}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class AbstractIoFutureTestCase {
    
    private TestIoFuture future;

    @Before
    public void createFuture() {
        future = new TestIoFuture();
        assertSame(Status.WAITING, future.getStatus());
    }

    @Test
    public void waitForResult() throws InterruptedException {
        final Awaiter awaiter = new Awaiter(future);
        final Thread awaiterThread = new Thread(awaiter);
        awaiterThread.start();
        awaiterThread.join(100);
        assertTrue(awaiterThread.isAlive());

        assertTrue(future.setResult("test"));
        awaiterThread.join();
        assertSame(Status.DONE, future.getStatus());
        assertSame(Status.DONE, awaiter.getAwaitStatus());

        assertSame(Status.DONE, future.await()); // shouldn't block

        assertFalse(future.setResult("second reult"));
    }

    @Test
    public void interruptWaiter() throws InterruptedException {
        final Awaiter awaiter = new Awaiter(future);
        final Thread awaiterThread = new Thread(awaiter);
        awaiterThread.start();
        // interrupt a couple of times
        for (int i = 0; i < 3; i++) {
            awaiterThread.interrupt();
            Thread.sleep(100);
        }
        assertSame(Status.WAITING, future.getStatus());
        assertSame(future, future.cancel());
        awaiterThread.join();
        assertSame(Status.CANCELLED, awaiter.getAwaitStatus());
    }

    @Test
    public void waitForResultWithTimeout() throws InterruptedException {
        final Awaiter awaiter = new Awaiter(future, 1, TimeUnit.DAYS);
        final Thread awaiterThread = new Thread(awaiter);
        awaiterThread.start();
        awaiterThread.join(100);
        assertTrue(awaiterThread.isAlive());

        assertTrue(future.setResult("test"));
        awaiterThread.join();
        assertSame(Status.DONE, future.getStatus());
        assertSame(Status.DONE, awaiter.getAwaitStatus());

        assertSame(Status.DONE, future.await(100, TimeUnit.MILLISECONDS)); // shouldn't block

        assertFalse(future.setResult("second result"));
    }

    @Test
    public void interruptWaiterWithTimeout() throws InterruptedException {
        assertSame(Status.WAITING, future.await(0, TimeUnit.MICROSECONDS));
        assertSame(Status.WAITING, future.await(-5, TimeUnit.MICROSECONDS));

        final Awaiter awaiter = new Awaiter(future, 1000, TimeUnit.SECONDS);
        final Thread awaiterThread = new Thread(awaiter);
        awaiterThread.start();
        // interrupt a couple of times
        for (int i = 0; i < 3; i++) {
            awaiterThread.interrupt();
            Thread.sleep(100);
        }
        assertSame(Status.WAITING, future.getStatus());
        assertTrue(awaiterThread.isAlive());
        assertSame(future, future.cancel());
        awaiterThread.join();
        assertSame(Status.CANCELLED, awaiter.getAwaitStatus());
        assertSame(Status.CANCELLED, future.getStatus());
    }

    @Test
    public void waitInterruptiblyForResult() throws InterruptedException {
        final InterruptiblyAwaiter awaiter = new InterruptiblyAwaiter(future);
        final Thread awaiterThread = new Thread(awaiter);
        awaiterThread.start();
        awaiterThread.join(100);
        assertTrue(awaiterThread.isAlive());

        assertTrue(future.setResult("test"));
        awaiterThread.join();
        assertSame(Status.DONE, future.getStatus());
        assertSame(Status.DONE, awaiter.getAwaitStatus());

        assertSame(Status.DONE, future.awaitInterruptibly()); // shouldn't block

        assertFalse(future.setResult("second reult"));
    }

    @Test
    public void interruptInterruptiblyWaiter() throws InterruptedException {
        final InterruptiblyAwaiter awaiter = new InterruptiblyAwaiter(future);
        final Thread awaiterThread = new Thread(awaiter);
        awaiterThread.start();
        // interrupt a couple of times
        for (int i = 0; i < 3; i++) {
            awaiterThread.interrupt();
            Thread.sleep(100);
        }
        assertSame(Status.WAITING, future.getStatus());
        assertSame(future, future.cancel());
        awaiterThread.join();
        assertSame(null, awaiter.getAwaitStatus());
        assertNotNull(awaiter.getException());
    }

    @Test
    public void waitInterruptiblyForResultWithTimeout() throws InterruptedException {
        final InterruptiblyAwaiter awaiter = new InterruptiblyAwaiter(future, 1, TimeUnit.DAYS);
        final Thread awaiterThread = new Thread(awaiter);
        awaiterThread.start();
        awaiterThread.join(100);
        assertTrue(awaiterThread.isAlive());

        assertTrue(future.setResult("test"));
        awaiterThread.join();
        assertSame(Status.DONE, future.getStatus());
        assertSame(Status.DONE, awaiter.getAwaitStatus());

        assertSame(Status.DONE, future.awaitInterruptibly(100, TimeUnit.MILLISECONDS)); // shouldn't block

        assertFalse(future.setResult("second result"));
    }

    @Test
    public void interruptInterruptiblyWaiterWithTimeout() throws InterruptedException {
        assertSame(Status.WAITING, future.awaitInterruptibly(0, TimeUnit.MICROSECONDS));
        assertSame(Status.WAITING, future.awaitInterruptibly(-5, TimeUnit.MICROSECONDS));

        final InterruptiblyAwaiter awaiter = new InterruptiblyAwaiter(future, 1000, TimeUnit.SECONDS);
        final Thread awaiterThread = new Thread(awaiter);
        awaiterThread.start();
        // interrupt a couple of times
        for (int i = 0; i < 3; i++) {
            awaiterThread.interrupt();
            Thread.sleep(100);
        }
        assertSame(Status.WAITING, future.getStatus());
        assertFalse(awaiterThread.isAlive());
        assertSame(future, future.cancel());
        awaiterThread.join();
        assertSame(null, awaiter.getAwaitStatus());
        assertNotNull(awaiter.getException());
        assertSame(Status.CANCELLED, future.getStatus());
    }

    @Test
    public void retrieveFutureResult() throws Exception {
        final ResultRetriever resultRetriever = new ResultRetriever(future);
        final Thread retrieverThread = new Thread(resultRetriever);
        retrieverThread.start();
        retrieverThread.join(100);
        assertTrue(retrieverThread.isAlive());

        assertTrue(future.setResult("get this"));
        retrieverThread.join();
        assertSame(Status.DONE, future.getStatus());
        assertSame("get this", resultRetriever.getResult());

        assertSame("get this", future.get()); // shouldn't block

        // cannot change the result nor set a new result
        assertFalse(future.setResult("second result"));
        assertFalse(future.setException(new IOException()));
        assertFalse(future.setCancelled());
    }

    @Test
    public void retrieveFutureFailure() throws InterruptedException {
        final ResultRetriever resultRetriever = new ResultRetriever(future);
        final Thread retrieverThread = new Thread(resultRetriever);
        retrieverThread.start();
        retrieverThread.join(100);
        assertTrue(retrieverThread.isAlive());

        final IOException exception = new IOException("Test exception");
        assertTrue(future.setException(exception));

        // can't set any result after setting exception
        assertFalse(future.setException(exception));
        assertFalse(future.setCancelled());
        assertFalse(future.setResult("anything"));

        retrieverThread.join();
        assertSame(Status.FAILED, future.getStatus());
        assertNull(resultRetriever.getResult());
        assertSame(exception, resultRetriever.getIOException());

        IOException expected = null;
        try {
            future.get(); // shouldn't block
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void retrieveFutureCancellationResult() throws Exception {
        final ResultRetriever resultRetriever = new ResultRetriever(future);
        final Thread retrieverThread = new Thread(resultRetriever);
        retrieverThread.start();
        retrieverThread.join(100);
        assertTrue(retrieverThread.isAlive());

        future.cancel();

        // can't set any result after canceling
        assertFalse(future.setException(new IOException()));
        assertFalse(future.setCancelled());
        future.cancel(); // useless call, future is already canceled
        assertFalse(future.setResult("anything"));

        retrieverThread.join();
        assertSame(Status.CANCELLED, future.getStatus());
        assertNull(resultRetriever.getResult());
        assertNotNull(resultRetriever.getCancellationException());

        CancellationException expected = null;
        try {
            future.get(); // shouldn't block
        } catch (CancellationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void interruptResultRetrieval() throws InterruptedException {
        final ResultRetriever resultRetriever= new ResultRetriever(future);
        final Thread retrieverThread = new Thread(resultRetriever);
        retrieverThread.start();
        // interrupt a couple of times
        for (int i = 0; i < 3; i++) {
            retrieverThread.interrupt();
            Thread.sleep(100);
        }
        assertSame(Status.WAITING, future.getStatus());
        assertSame(future, future.cancel());
        retrieverThread.join();
        assertSame(null, resultRetriever.getResult());
        assertNotNull(resultRetriever.getCancellationException());
    }

    @Test
    public void retrieveFutureResultInterruptibly() throws Exception {
        final ResultRetriever resultRetriever = new ResultRetriever(future, true);
        final Thread retrieverThread = new Thread(resultRetriever);
        retrieverThread.start();
        retrieverThread.join(100);
        assertTrue(retrieverThread.isAlive());

        assertTrue(future.setResult("get this"));
        retrieverThread.join();
        assertSame(Status.DONE, future.getStatus());
        assertSame("get this", resultRetriever.getResult());

        assertSame("get this", future.get()); // shouldn't block

        // cannot change the result nor set a new result
        assertFalse(future.setResult("second result"));
        assertFalse(future.setException(new IOException()));
        assertFalse(future.setCancelled());
    }

    @Test
    public void retrieveFutureFailureInterruptibly() throws InterruptedException {
        final ResultRetriever resultRetriever = new ResultRetriever(future, true);
        final Thread retrieverThread = new Thread(resultRetriever);
        retrieverThread.start();
        retrieverThread.join(100);
        assertTrue(retrieverThread.isAlive());

        final IOException exception = new IOException("Test exception");
        assertTrue(future.setException(exception));

        // can't set any result after setting exception
        assertFalse(future.setException(exception));
        assertFalse(future.setCancelled());
        assertFalse(future.setResult("anything"));

        retrieverThread.join();
        assertSame(Status.FAILED, future.getStatus());
        assertNull(resultRetriever.getResult());
        assertSame(exception, resultRetriever.getIOException());

        IOException expected = null;
        try {
            future.get(); // shouldn't block
        } catch (IOException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void retrieveFutureCancellationResultInterruptibly() throws Exception {
        final ResultRetriever resultRetriever = new ResultRetriever(future, true);
        final Thread retrieverThread = new Thread(resultRetriever);
        retrieverThread.start();
        retrieverThread.join(100);
        assertTrue(retrieverThread.isAlive());

        future.cancel();

        // can't set any result after canceling
        assertFalse(future.setException(new IOException()));
        assertFalse(future.setCancelled());
        future.cancel(); // useless call, future is already canceled
        assertFalse(future.setResult("anything"));

        retrieverThread.join();
        assertSame(Status.CANCELLED, future.getStatus());
        assertNull(resultRetriever.getResult());
        assertNotNull(resultRetriever.getCancellationException());

        CancellationException expected = null;
        try {
            future.get(); // shouldn't block
        } catch (CancellationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void interruptInterruptiblyResultRetrieval() throws InterruptedException {
        final ResultRetriever resultRetriever= new ResultRetriever(future, true);
        final Thread retrieverThread = new Thread(resultRetriever);
        retrieverThread.start();
        // interrupt a couple of times
        for (int i = 0; i < 3; i++) {
            retrieverThread.interrupt();
            Thread.sleep(100);
        }
        assertSame(Status.WAITING, future.getStatus());
        retrieverThread.join();
        assertSame(null, resultRetriever.getResult());
        assertNotNull(resultRetriever.getInterruptedException());
    }

    @Test
    public void setAndRetrieveException () {
        final IOException exception = new IOException();
        future.setException(exception);

        assertSame(exception, future.getException());

        assertFalse(future.setException(new IOException()));
        assertFalse(future.setException(new IOException()));
        assertFalse(future.setException(new IOException()));
        assertFalse(future.setException(new IOException()));

        assertSame(exception, future.getException());

        // wrongfully try to retrieve non-existent exception

        IllegalStateException expected = null;
        final TestIoFuture future2 = new TestIoFuture();
        future2.setResult("future2");
        try {
            future2.getException();
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        final TestIoFuture future3 = new TestIoFuture();
        future3.cancel();
        try {
            future3.getException();
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        final TestIoFuture future4 = new TestIoFuture();
        assertSame(Status.WAITING, future4.getStatus());
        try {
            future4.getException();
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
    }


    private void checkNotifiers(Runnable stopWaitingTask) {
        final TestNotifier notifier1 = new TestNotifier();
        final TestNotifier notifier2 = new TestNotifier();
        final TestNotifier notifier3 = new TestNotifier();
        final TestNotifier notifier4 = new TestNotifier();
        final TestNotifier notifier5 = new TestNotifier();
        notifier2.throwExceptionOnNotify();

        final Object attachment1 = new Object();
        final Object attachment2 = new Object();
        final Object attachment3 = new Object();
        final Object attachment4 = new Object();
        final Object attachment5 = new Object();

        future.addNotifier(notifier1, attachment1);
        future.addNotifier(notifier2, attachment2);
        future.addNotifier(notifier3, attachment3);
        future.addNotifier(notifier4, attachment4);

        assertFalse(notifier1.isInvoked());
        assertFalse(notifier2.isInvoked());
        assertFalse(notifier3.isInvoked());
        assertFalse(notifier4.isInvoked());

        stopWaitingTask.run();

        assertTrue(notifier1.isInvoked());
        assertSame(future, notifier1.getFuture());
        assertSame(attachment1, notifier1.getAttachment());

        assertTrue(notifier2.isInvoked());
        assertSame(future, notifier2.getFuture());
        assertSame(attachment2, notifier2.getAttachment());

        assertTrue(notifier3.isInvoked());
        assertSame(future, notifier3.getFuture());
        assertSame(attachment3, notifier3.getAttachment());

        assertTrue(notifier4.isInvoked());
        assertSame(future, notifier4.getFuture());
        assertSame(attachment4, notifier4.getAttachment());

        assertFalse(notifier5.isInvoked());

        future.addNotifier(notifier5, attachment5);

        assertTrue(notifier5.isInvoked());
        assertSame(future, notifier5.getFuture());
        assertSame(attachment5, notifier5.getAttachment());
    }

    @Test
    public void checkNotifiersWhenDone() {
        checkNotifiers(new Runnable() {
            public void run() {
                future.setResult("run notifiers");
            }
        });
    }

    @Test
    public void checkNotifiersWhenCancelled() {
        checkNotifiers(new Runnable() {
            public void run() {
                future.cancel();
            }
        });
    }

    @Test
    public void checkNotifiersWhenFailed() {
        checkNotifiers(new Runnable() {
            public void run() {
                future.setException(new IOException("Test exceptionś"));
            }
        });
    }

    @Test
    public void invokeCancelHandlers() {
        final TestCancelHandler cancelHandler1 = new TestCancelHandler();
        final TestCancelHandler cancelHandler2 = new TestCancelHandler();
        final TestCancelHandler cancelHandler3 = new TestCancelHandler();
        final TestCancelHandler cancelHandler4 = new TestCancelHandler();

        future.addCancelHandler(cancelHandler1);
        future.addCancelHandler(cancelHandler2);

        assertFalse(cancelHandler1.isInvoked());
        assertFalse(cancelHandler2.isInvoked());

        future.startCancellation();

        assertTrue(cancelHandler1.isInvoked());
        assertTrue(cancelHandler2.isInvoked());

        cancelHandler1.clear();
        cancelHandler2.clear();
        // should be idempotent
        future.startCancellation();
        assertFalse(cancelHandler1.isInvoked());
        assertFalse(cancelHandler2.isInvoked());

        future.addCancelHandler(cancelHandler3);
        assertTrue(cancelHandler3.isInvoked());

        cancelHandler3.clear();
        assertSame(Status.WAITING, future.getStatus());
        future.concludeCancellation();

        future.addCancelHandler(cancelHandler4);
        assertTrue(cancelHandler4.isInvoked());

        // no cancel handler should have been invoked again, they must be called only once
        assertFalse(cancelHandler1.isInvoked());
        assertFalse(cancelHandler2.isInvoked());
        assertFalse(cancelHandler3.isInvoked());
    }

    private static class TestIoFuture extends AbstractIoFuture<String> {

        @Override
        public TestIoFuture cancel() {
            super.cancel();
            setCancelled();
            return this;
        }

        public void startCancellation() {
            super.cancel();
        }

        public void concludeCancellation() {
            setCancelled();
        }
    }

    private static class Awaiter implements Runnable {

        private final IoFuture<?> ioFuture;
        private final long timeout;
        private final TimeUnit timeoutUnit;
        private Status status;

        public Awaiter(IoFuture<?> f) {
            this(f, -1, null);
        }
        
        public Awaiter(IoFuture<?> f, long t, TimeUnit tu) {
            ioFuture = f;
            timeout = t;
            timeoutUnit = tu;
        }

        public void run() {
            if (timeoutUnit == null) {
                status = ioFuture.await();
            } else {
                status = ioFuture.await(timeout, timeoutUnit);
            }
        }

        public Status getAwaitStatus() {
            return status;
        }
    }

    private static class InterruptiblyAwaiter implements Runnable {

        private final IoFuture<?> ioFuture;
        private final long timeout;
        private final TimeUnit timeoutUnit;
        private InterruptedException exception;
        private Status status;

        public InterruptiblyAwaiter(IoFuture<?> f) {
            this(f, -1, null);
        }

        public InterruptiblyAwaiter(IoFuture<?> f, long t, TimeUnit tu) {
            ioFuture = f;
            timeout = t;
            timeoutUnit = tu;
        }

        public void run() {
            try {
                if (timeoutUnit == null) {
                    status = ioFuture.awaitInterruptibly();
                } else {
                    status = ioFuture.awaitInterruptibly(timeout, timeoutUnit);
                }
            } catch (InterruptedException e) {
                exception = e;
            }
        }

        public InterruptedException getException() {
            return exception;
        }

        public Status getAwaitStatus() {
            return status;
        }
    }

    private static class ResultRetriever implements Runnable {
        private final IoFuture<String> ioFuture;
        private final boolean interruptibly;
        private String result;
        private CancellationException cancellationException;
        private IOException exception;
        private InterruptedException interruptedException;

        public ResultRetriever(IoFuture<String> f) {
            this(f, false);
        }

        public ResultRetriever(IoFuture<String> f, boolean i) {
            ioFuture = f;
            interruptibly = i;
        }

        public void run() {
            try {
                if (interruptibly) {
                    result = ioFuture.getInterruptibly();
                } else {
                    result = ioFuture.get();
                }
            } catch (CancellationException e) {
                cancellationException = e;
            } catch (IOException e) {
                exception = e;
            } catch (InterruptedException e) {
                interruptedException = e;
            }
        }

        public String getResult() {
            return result;
        }

        public CancellationException getCancellationException() {
            return cancellationException;
        }

        public InterruptedException getInterruptedException() {
            return interruptedException;
        }

        public IOException getIOException() {
            return exception;
        }
    }

    private static class TestNotifier implements IoFuture.Notifier<String, Object> {

        private boolean throwException;
        private boolean invoked;
        private IoFuture<? extends String> future;
        private Object attachment;

        public void throwExceptionOnNotify() {
            throwException = true;
        }

        @Override
        public void notify(IoFuture<? extends String> f, Object a) {
            invoked = true;
            future = f;
            attachment = a;
            if (throwException) {
                throw new RuntimeException("Test exception");
            }
        }

        public boolean isInvoked() {
            return invoked;
        }

        public IoFuture<? extends String> getFuture() {
            return future;
        }

        public Object getAttachment() {
            return attachment;
        }
    }

    private static class TestCancelHandler implements Cancellable {

        private boolean invoked;

        @Override
        public Cancellable cancel() {
            invoked = true;
            return this;
        }

        public boolean isInvoked() {
            return invoked;
        }

        public void clear() {
            invoked = false;
        }
    }
}
