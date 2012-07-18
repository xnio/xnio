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

package org.xnio.racecondition;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.ChannelListener;
import org.xnio.channels.SuspendableChannel;
import org.xnio.channels.TranslatingSuspendableChannel;
import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * Resume reads on a channel that is handling readable. Check if the effects of resuming reads are
 * not mistakenly covered by handleReadable.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
@RunWith(BMUnitRunner.class)
@BMScript(dir="src/test/resources")
public class ResumeReadsOnHandlingReadableChannelTestCase {
    @Test
    public void test() throws Exception {
        // create mockery context
        final Mockery context = new JUnit4Mockery();
        // creating channel and threads
        ConnectedStreamChannelMock connectedChannelMock = new ConnectedStreamChannelMock();
        final MyTranslatingSuspendableChannel channel = new MyTranslatingSuspendableChannel(connectedChannelMock);
        final Thread resumeReadsThread = new Thread(new ResumeReads(channel));
        final Thread handleReadableThread = new Thread(new InvokeHandleReadable(channel));
        // set up scenario
        @SuppressWarnings("unchecked")
        final ChannelListener<? super SuspendableChannel> listener = context.mock(ChannelListener.class, "listener1");
        context.checking(new Expectations() {{
            allowing(listener).handleEvent(channel);
        }});
        channel.getReadSetter().set(listener);
        channel.suspendReads();
        channel.handleReadable();
        assertFalse(connectedChannelMock.isReadResumed());
        // first, try to invoke handleReadable and then resumeReads
        System.out.println("Attempt 1: invoke handleReadable before resumeReads");
        channel.handleReadable();
        channel.resumeReads();
        assertTrue(channel.isReadResumed());
        assertTrue(connectedChannelMock.isReadResumed());
        // clear everything
        channel.suspendReads();
        channel.handleReadable();
        assertFalse(channel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        // now, try the opposite, invoke handleReadable after resumeReads()
        System.out.println("Attempt 2: invoke handleReadable after resumeReads");
        channel.resumeReads();
        channel.handleReadable();
        assertTrue(channel.isReadResumed());
        assertTrue(connectedChannelMock.isReadResumed());
        // clear everything once more
        channel.suspendReads();
        channel.handleReadable();
        assertFalse(channel.isReadResumed());
        assertFalse(connectedChannelMock.isReadResumed());
        System.out.println("Attempt 3: race condition scenario involving handleReadable and resumeReads");
        // finally, create the race condition scenario... handleReadable and resumeReads occur
        // practically at the same time (see ResumeReadsOnHandlingReadableChannelTestCase.btm)
        resumeReadsThread.start();
        handleReadableThread.start();
        // joining threads
        resumeReadsThread.join();
        handleReadableThread.join();
        assertTrue(channel.isReadResumed());
        assertTrue(connectedChannelMock.isReadResumed());
    }

    private static class ResumeReads implements Runnable {
        private MyTranslatingSuspendableChannel channel;

        public ResumeReads(MyTranslatingSuspendableChannel channel) {
            this.channel = channel;
        }

        @Override
        public void run() {
            channel.resumeReads();
        }
    }

    private static class InvokeHandleReadable implements Runnable {
        private MyTranslatingSuspendableChannel channel;

        public InvokeHandleReadable(MyTranslatingSuspendableChannel channel) {
            this.channel = channel;
        }

        @Override
        public void run() {
            channel.handleReadable();
        }
    }

    private static class MyTranslatingSuspendableChannel extends TranslatingSuspendableChannel<SuspendableChannel, SuspendableChannel> {

        protected MyTranslatingSuspendableChannel(SuspendableChannel c) {
            super(c);
        }

        public void handleReadable() {
            super.handleReadable();
        }
    }
}
