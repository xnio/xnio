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
 * Resume writes on a channel that is handling writable. Check if the effects of resuming writes are
 * not mistakenly covered by handleWritable.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
@RunWith(BMUnitRunner.class)
@BMScript(dir="src/test/resources")
public class ResumeWritesOnHandlingWritableChannelTestCase {
    @Test
    public void test() throws Exception {
        // create mockery context
        final Mockery context = new JUnit4Mockery();
        // creating channel and threads
        ConnectedStreamChannelMock connectedChannelMock = new ConnectedStreamChannelMock();
        final MyTranslatingSuspendableChannel channel = new MyTranslatingSuspendableChannel(connectedChannelMock);
        final Thread resumeWritesThread = new Thread(new ResumeWrites(channel));
        final Thread handleWritableThread = new Thread(new InvokeHandleWritable(channel));
        // set up scenario
        @SuppressWarnings("unchecked")
        final ChannelListener<? super SuspendableChannel> listener = context.mock(ChannelListener.class, "listener1");
        context.checking(new Expectations() {{
            allowing(listener).handleEvent(channel);
        }});
        channel.getWriteSetter().set(listener);
        channel.suspendWrites();
        channel.handleWritable();
        assertFalse(connectedChannelMock.isWriteResumed());
        // first, try to invoke handleWritable and then resumeWrites
        System.out.println("Attempt 1: invoke handleWritable before resumeWrites");
        channel.handleWritable();
        channel.resumeWrites();
        assertTrue(channel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        // clear everything
        channel.suspendWrites();
        channel.handleWritable();
        assertFalse(channel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        // now, try the opposite, invoke handleWritable after resumeWrites()
        System.out.println("Attempt 2: invoke handleWritable after resumeWrites");
        channel.resumeWrites();
        channel.handleWritable();
        assertTrue(channel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
        // clear everything once more
        channel.suspendWrites();
        channel.handleWritable();
        assertFalse(channel.isWriteResumed());
        assertFalse(connectedChannelMock.isWriteResumed());
        System.out.println("Attempt 3: race condition scenario involving handleWritable and resumeWrites");
        // finally, create the race condition scenario... handleWritable and resumeWrites occur
        // practically at the same time (see ReusmeWritesOnHandlingWritableChannelTestCase.btm)
        resumeWritesThread.start();
        handleWritableThread.start();
        // joining threads
        resumeWritesThread.join();
        handleWritableThread.join();
        assertTrue(channel.isWriteResumed());
        assertTrue(connectedChannelMock.isWriteResumed());
    }

    private static class ResumeWrites implements Runnable {
        private MyTranslatingSuspendableChannel channel;

        public ResumeWrites(MyTranslatingSuspendableChannel channel) {
            this.channel = channel;
        }

        @Override
        public void run() {
            channel.resumeWrites();
        }
    }

    private static class InvokeHandleWritable implements Runnable {
        private MyTranslatingSuspendableChannel channel;

        public InvokeHandleWritable(MyTranslatingSuspendableChannel channel) {
            this.channel = channel;
        }

        @Override
        public void run() {
            channel.handleWritable();
        }
    }

    private static class MyTranslatingSuspendableChannel extends TranslatingSuspendableChannel<SuspendableChannel, SuspendableChannel> {

        protected MyTranslatingSuspendableChannel(SuspendableChannel c) {
            super(c);
        }

        public void handleWritable() {
            super.handleWritable();
        }
    }
}
