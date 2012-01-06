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
