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
