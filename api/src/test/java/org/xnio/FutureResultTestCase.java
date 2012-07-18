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
