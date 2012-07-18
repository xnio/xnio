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
