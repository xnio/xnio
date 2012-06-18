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
package org.xnio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.xnio.IoFuture.Status;

/**
 * Test for {@link AbstractConvertingIoFuture}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class AbstractConvertingIoFutureTestCase {

    private StringFuture delegate;
    private StringToCharArrayFuture convertingFuture;

    @Before
    public void init() {
        delegate = new StringFuture("Test");
        convertingFuture = new StringToCharArrayFuture(delegate);
    }

    @Test
    public void delegate() {
        assertSame(delegate, convertingFuture.getDelegate());
    }

    @Test
    public void convertValue() throws IOException {
        char[] result = convertingFuture.get();
        assertNotNull(result);
        assertEquals(4, result.length);
        assertEquals('T', result[0]);
        assertEquals('e', result[1]);
        assertEquals('s', result[2]);
        assertEquals('t', result[3]);
    }

    @Test
    public void convertValueInterruptibly() throws IOException, InterruptedException {
        char[] result = convertingFuture.getInterruptibly();
        assertNotNull(result);
        assertEquals(5, result.length);
        assertEquals('T', result[0]);
        assertEquals('e', result[1]);
        assertEquals('s', result[2]);
        assertEquals('t', result[3]);
        assertEquals('!', result[4]);
    }

    @Test
    public void cancel() {
        assertFalse(delegate.isCanceled());
        assertSame(convertingFuture, convertingFuture.cancel());
        assertTrue(delegate.isCanceled());
    }

    @Test
    public void status() {
        delegate.setStatus(Status.FAILED);
        assertSame(Status.FAILED, convertingFuture.getStatus());
        delegate.setStatus(Status.DONE);
        assertSame(Status.DONE, convertingFuture.getStatus());
        delegate.setStatus(Status.FAILED);
        assertSame(Status.FAILED, convertingFuture.getStatus());
        delegate.setStatus(Status.WAITING);
        assertSame(Status.WAITING, convertingFuture.getStatus());
    }

    @Test
    public void await() {
        delegate.setStatus(Status.WAITING);
        assertFalse(delegate.hasAwaited());
        assertSame(Status.WAITING, convertingFuture.await());
        assertTrue(delegate.hasAwaited());
    }

    @Test
    public void awaitWithTimeout() {
        delegate.setStatus(Status.DONE);
        assertFalse(delegate.hasAwaited());
        assertSame(Status.DONE, convertingFuture.await(10, TimeUnit.SECONDS));
        assertTrue(delegate.hasAwaited());
        assertEquals(10, delegate.getAwaitTime());
        assertEquals(TimeUnit.SECONDS, delegate.getAwaitTimeUnit());

        assertSame(Status.DONE, convertingFuture.await(1, TimeUnit.DAYS));
        assertTrue(delegate.hasAwaited());
        assertEquals(1, delegate.getAwaitTime());
        assertEquals(TimeUnit.DAYS, delegate.getAwaitTimeUnit());
    }

    @Test
    public void awaitInterruptibly() throws InterruptedException {
        delegate.setStatus(Status.CANCELLED);
        assertFalse(delegate.hasAwaitedInterruptibly());
        assertSame(Status.CANCELLED, convertingFuture.awaitInterruptibly());
        assertTrue(delegate.hasAwaitedInterruptibly());
    }

    @Test
    public void awaitInterruptiblyWithTimeout() throws InterruptedException {
        delegate.setStatus(Status.WAITING);
        assertFalse(delegate.hasAwaited());
        assertSame(Status.WAITING, convertingFuture.awaitInterruptibly(10, TimeUnit.SECONDS));
        assertTrue(delegate.hasAwaitedInterruptibly());
        assertEquals(10, delegate.getAwaitTime());
        assertEquals(TimeUnit.SECONDS, delegate.getAwaitTimeUnit());

        assertSame(Status.WAITING, convertingFuture.awaitInterruptibly(1, TimeUnit.DAYS));
        assertTrue(delegate.hasAwaitedInterruptibly());
        assertEquals(1, delegate.getAwaitTime());
        assertEquals(TimeUnit.DAYS, delegate.getAwaitTimeUnit());
    }

    @Test
    public void exception() {
        final IOException exception1 = new IOException("Test exception");
        final IOException exception2 = new IOException("Test exception");
        final IOException exception3 = new IOException("Test exception");
        delegate.setException(exception1);
        assertSame(exception1, convertingFuture.getException());

        delegate.setException(exception2);
        assertSame(exception2, convertingFuture.getException());

        delegate.setException(exception3);
        assertSame(exception3, convertingFuture.getException());
    }

    @Test
    public void futureNotifier() {
        final CharArrayNotifier notifier = new CharArrayNotifier();
        final Object attachment = new Object();

        assertSame(convertingFuture, convertingFuture.addNotifier(notifier, attachment));
        assertFalse(notifier.isInvoked());
        delegate.invokeNotifier();
        assertTrue(notifier.isInvoked());
        assertSame(convertingFuture, notifier.getFuture());
        assertSame(attachment, notifier.getAttachment());
    }

    private static class StringFuture implements IoFuture<String> {

        private String value;
        private boolean canceled;
        private Status status;
        private boolean awaited;
        private boolean awaitedInterruptibly;
        private long awaitTime;
        private TimeUnit awaitTimeUnit;
        private IOException exception;
        private IoFuture.Notifier<? super String, Object> notifier;
        private Object notifierAttachment;

        public StringFuture(String v) {
            value = v;
        }

        @Override
        public IoFuture<String> cancel() {
            canceled = true;
            return this;
        }

        public boolean isCanceled() {
            return canceled;
        }

        public void setStatus(Status s) {
            status = s;
        }

        @Override
        public Status getStatus() {
            return status;
        }

        @Override
        public Status await() {
            awaited = true;
            return status;
        }

        public boolean hasAwaited() {
            return awaited;
        }

        @Override
        public Status await(long time, TimeUnit timeUnit) {
            awaited = true;
            awaitTime = time;
            awaitTimeUnit = timeUnit;
            return status;
        }

        public long getAwaitTime() {
            return awaitTime;
        }

        public TimeUnit getAwaitTimeUnit() {
            return awaitTimeUnit;
        }

        public boolean hasAwaitedInterruptibly() {
            return awaitedInterruptibly;
        }

        @Override
        public Status awaitInterruptibly() throws InterruptedException {
            awaitedInterruptibly = true;
            return status;
        }

        @Override
        public Status awaitInterruptibly(long time, TimeUnit timeUnit) throws InterruptedException {
            awaitedInterruptibly = true;
            awaitTime = time;
            awaitTimeUnit = timeUnit;
            return status;
        }

        @Override
        public String get() throws IOException, CancellationException {
           return value;
        }

        @Override
        public String getInterruptibly() throws IOException, InterruptedException, CancellationException {
            return value + "!";
        }

        @Override
        public IOException getException() throws IllegalStateException {
            return exception;
        }

        public void setException(IOException e) {
            exception = e;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <A> IoFuture<String> addNotifier(IoFuture.Notifier<? super String, A> notifier, A attachment) {
            if (this.notifier != null) {
                throw new IllegalStateException("This test class supports only one notifier at most");
            }
            this.notifier = (IoFuture.Notifier<? super String, Object>) notifier;
            this.notifierAttachment = attachment;
            return this;
        }

        public void invokeNotifier() {
            notifier.notify(this, notifierAttachment);
        }
    }

    private static class StringToCharArrayFuture extends AbstractConvertingIoFuture<char[], String> {

        protected StringToCharArrayFuture(IoFuture<? extends String> delegate) {
            super(delegate);
        }

        @Override
        protected char[] convert(String arg) throws IOException {
            return arg.toCharArray();
        }
    }

    private static class CharArrayNotifier implements IoFuture.Notifier<char[], Object> {

        private boolean invoked;
        private IoFuture<? extends char[]> future;
        private Object attachment;

        @Override
        public void notify(IoFuture<? extends char[]> f, Object a) {
            invoked = true;
            future = f;
            attachment = a;
        }

        public boolean isInvoked() {
            return invoked;
        }

        public IoFuture<? extends char[]> getFuture() {
            return future;
        }

        public Object getAttachment() {
            return attachment;
        }
    }
}
