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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.xnio.IoFuture.Notifier;
import org.xnio.IoFuture.Status;

/**
 * Test for {@link FinishedIoFuture}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class FinishedIoFutureTestCase {
    @Test
    public void test() throws Exception {
        final FinishedIoFuture<String> future = new FinishedIoFuture<String>("future result");
     
        final TestNotifier notifier = new TestNotifier();
        final Object attachment = new Object();
        future.addNotifier(notifier, attachment);
        assertTrue(notifier.isInvoked());
        assertSame(future, notifier.getFuture());
        assertSame(attachment, notifier.getAttachment());

        assertSame(Status.DONE, future.await());
        assertSame(Status.DONE, future.await(10, TimeUnit.SECONDS));
        assertSame(Status.DONE, future.awaitInterruptibly());
        assertSame(Status.DONE, future.awaitInterruptibly(1, TimeUnit.MINUTES));
        assertSame(future, future.cancel());

        assertSame("future result", future.get());
        assertSame("future result", future.getInterruptibly());

        IllegalStateException expected = null;
        try {
            future.getException();
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertSame(Status.DONE, future.getStatus());
    }

    private static class TestNotifier implements Notifier<String, Object> {

        private boolean invoked = false;
        private IoFuture<? extends String> ioFuture;
        private Object attachment;

        @Override
        public void notify(IoFuture<? extends String> f, Object a) {
            invoked = true;
            ioFuture = f;
            attachment = a;
        }

        public boolean isInvoked() {
            return invoked;
        }

        public IoFuture<? extends String> getFuture() {
            return ioFuture;
        }

        public Object getAttachment() {
            return attachment;
        }
    }
}
