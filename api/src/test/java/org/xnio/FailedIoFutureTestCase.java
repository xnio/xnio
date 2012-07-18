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

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.xnio.IoFuture.Notifier;
import org.xnio.IoFuture.Status;

/**
 * Test for {@link FailedIoFuture}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class FailedIoFutureTestCase {

    @Test
    public void test() throws Exception {
        final IOException exception = new IOException("Test exception");
        final FailedIoFuture<Void> future = new FailedIoFuture<Void>(exception);
        future.addCancelHandler(new Cancellable() {
            @Override
            public Cancellable cancel() {
                throw new RuntimeException("This Cancellable should never be called!");
            }
        });

        final TestNotifier notifier = new TestNotifier();
        final Object attachment = new Object();
        future.addNotifier(notifier, attachment);
        assertTrue(notifier.isInvoked());
        assertSame(future, notifier.getFuture());
        assertSame(attachment, notifier.getAttachment());

        assertSame(Status.FAILED, future.await());
        assertSame(Status.FAILED, future.await(10, TimeUnit.SECONDS));
        assertSame(Status.FAILED, future.awaitInterruptibly());
        assertSame(Status.FAILED, future.awaitInterruptibly(1, TimeUnit.MINUTES));
        assertSame(future, future.cancel());

        IOException expected = null;
        try {
            future.get();
        } catch (IOException e) {
            expected = e;
        }
        assertSame(exception, expected);
        expected = null;
        try {
            future.getInterruptibly();
        } catch (IOException e) {
            expected = e;
        }
        assertSame(exception, expected);

        assertSame(exception, future.getException());
        assertSame(Status.FAILED, future.getStatus());
    }

    private static class TestNotifier implements Notifier<Void, Object> {

        private boolean invoked = false;
        private IoFuture<? extends Void> ioFuture;
        private Object attachment;

        @Override
        public void notify(IoFuture<? extends Void> f, Object a) {
            invoked = true;
            ioFuture = f;
            attachment = a;
        }

        public boolean isInvoked() {
            return invoked;
        }

        public IoFuture<? extends Void> getFuture() {
            return ioFuture;
        }

        public Object getAttachment() {
            return attachment;
        }
    }
}
