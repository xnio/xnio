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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.Executor;

import org.junit.Test;
import org.xnio.IoFuture.Status;

/**
 * Test for {@link FutureResult}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class FutureResultTestCase {

    @Test
    public void setFutureResult() throws Exception {
        final FutureResult<String> futureResult = new FutureResult<String>(new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        });
        // getIoFuture returns consistently the same value always
        final IoFuture<String> ioFuture = futureResult.getIoFuture();
        assertSame(ioFuture, futureResult.getIoFuture());

        assertSame(Status.WAITING, ioFuture.getStatus());
        assertTrue(futureResult.setResult("result"));
        assertSame(Status.DONE, ioFuture.getStatus());
        assertEquals("result", ioFuture.get());
    }

    @Test
    public void cancelFutureResult() throws Exception {
        final FutureResult<String> futureResult = new FutureResult<String>();
        // getIoFuture returns consistently the same value always
        final IoFuture<String> ioFuture = futureResult.getIoFuture();
        assertSame(ioFuture, futureResult.getIoFuture());

        final TestCancellable cancelHandler = new TestCancellable();
        futureResult.addCancelHandler(cancelHandler);
        assertFalse(cancelHandler.isCancelled());

        ioFuture.cancel();
        assertTrue(cancelHandler.isCancelled());

        assertSame(Status.WAITING, ioFuture.getStatus());
        assertTrue(futureResult.setCancelled());
        assertSame(Status.CANCELLED, ioFuture.getStatus());

        futureResult.setResult("can't set result after cancelled");
    }

    @Test
    public void failFutureResult() throws Exception {
        final IOException exception = new IOException("Test exception");
        final FutureResult<String> futureResult = new FutureResult<String>();
        // getIoFuture returns consistently the same value always
        final IoFuture<String> ioFuture = futureResult.getIoFuture();
        assertSame(ioFuture, futureResult.getIoFuture());

        assertSame(Status.WAITING, ioFuture.getStatus());
        assertTrue(futureResult.setException(exception));
        assertSame(Status.FAILED, ioFuture.getStatus());
        assertSame(exception, ioFuture.getException());

        assertFalse(futureResult.setResult("can't set result after cancelled"));
        assertFalse(futureResult.setException(new IOException()));
        assertFalse(futureResult.setCancelled());
    }

    private static class TestCancellable implements Cancellable {
        private boolean cancelled = false;

        @Override
        public Cancellable cancel() {
            cancelled = true;
            return this;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }
}
