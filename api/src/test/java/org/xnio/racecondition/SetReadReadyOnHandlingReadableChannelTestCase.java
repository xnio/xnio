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

import java.lang.reflect.Field;

import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.Bits;
import org.xnio.ChannelListener;
import org.xnio.channels.SuspendableChannel;
import org.xnio.channels.TranslatingSuspendableChannel;
import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * Set read ready on a channel that is handling readable. Check if the effects of setting read ready are
 * not mistakenly covered by handleReadable.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
@RunWith(BMUnitRunner.class)
@BMScript(dir="src/test/resources")
public class SetReadReadyOnHandlingReadableChannelTestCase {
    @Test
    public void test() throws Exception {
        // creating channel and threads
        ConnectedStreamChannelMock connectedChannelMock = new ConnectedStreamChannelMock();
        final MyTranslatingSuspendableChannel channel = new MyTranslatingSuspendableChannel(connectedChannelMock);
        final Thread setReadReadyThread = new Thread(new SetReadReady(channel));
        final Thread handleReadableThread = new Thread(new InvokeHandleReadable(channel));
        // set up scenario
        final ChannelListener<? super SuspendableChannel> listener = new ChannelListener<SuspendableChannel>() {
            @Override
            public void handleEvent(SuspendableChannel c) {
                channel.clearReadReady();
            }
        };
        channel.getReadSetter().set(listener);
        channel.resumeReads();
        channel.setReadRequiresWrite();
        channel.handleReadable();
        assertFalse(channel.isReadReady());
        assertFalse(connectedChannelMock.isReadResumed());
        // first, try to invoke handleReadable and then setReadReady
        System.out.println("Attempt 1: invoke handleReadable before setReadReady");
        channel.handleReadable();
        channel.setReadReady();
        assertTrue(channel.isReadReady());
        assertTrue(connectedChannelMock.isReadAwaken());
        // clear everything
        channel.clearReadReady();
        channel.handleReadable();
        assertFalse(channel.isReadReady());
        assertFalse(connectedChannelMock.isReadResumed());
        // now, try the opposite, invoke handleReadable after setReadReady()
        System.out.println("Attempt 2: invoke handleReadable after setReadReady");
        channel.setReadReady();
        channel.handleReadable();
        assertFalse(channel.isReadReady());
        assertTrue(connectedChannelMock.isReadAwaken());
        channel.clearReadReady();
        channel.handleReadable();
        assertFalse(channel.isReadReady());
        assertFalse(connectedChannelMock.isReadResumed());
        System.out.println("Attempt 3: race condition scenario involving handleReadable and setReadReady");
        // finally, create the race condition scenario... handleReadable and setReadReady occur
        // practically at the same time (see SetReadReadyOnHandlingReadableChannelTestCase.btm)
        setReadReadyThread.start();
        handleReadableThread.start();
        // joining threads
        setReadReadyThread.join();
        handleReadableThread.join();
        assertFalse(channel.isReadReady());
        assertTrue(connectedChannelMock.isReadResumed());
    }

    private static class SetReadReady implements Runnable {
        private MyTranslatingSuspendableChannel channel;

        public SetReadReady(MyTranslatingSuspendableChannel channel) {
            this.channel = channel;
        }

        @Override
        public void run() {
            channel.setReadReady();
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

        private static final int READ_READY;
        private static final Field stateField;
        static {
            try {
                Field READ_READY_field = TranslatingSuspendableChannel.class.getDeclaredField("READ_READY");
                READ_READY_field.setAccessible(true);
                READ_READY = READ_READY_field.getInt(null);
                stateField = TranslatingSuspendableChannel.class.getDeclaredField("state");
                stateField.setAccessible(true);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Unexpected exception " + e);
            }
        }

        protected MyTranslatingSuspendableChannel(SuspendableChannel c) {
            super(c);
        }

        public void handleReadable() {
            super.handleReadable();
        }

        public void setReadReady() {
            super.setReadReady();
        }

        public void clearReadReady() {
            super.clearReadReady();
        }

        public boolean isReadReady() throws Exception {
            return Bits.allAreSet(stateField.getInt(this), READ_READY);
        }

        public void setReadRequiresWrite() {
            super.setReadRequiresWrite();
        }
    }
}
