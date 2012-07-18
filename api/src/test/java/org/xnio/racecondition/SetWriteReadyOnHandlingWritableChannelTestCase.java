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
 * Set write ready on a channel that is handling writable. Check if the effects of setting write ready are
 * not mistakenly covered by handleWritable.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
@RunWith(BMUnitRunner.class)
@BMScript(dir="src/test/resources")
public class SetWriteReadyOnHandlingWritableChannelTestCase {
    @Test
    public void test() throws Exception {
        // creating channel and threads
        ConnectedStreamChannelMock connectedChannelMock = new ConnectedStreamChannelMock();
        final MyTranslatingSuspendableChannel channel = new MyTranslatingSuspendableChannel(connectedChannelMock);
        final Thread setWriteReadyThread = new Thread(new SetWriteReady(channel));
        final Thread handleWritableThread = new Thread(new InvokeHandleWritable(channel));
        // set up scenario
        final ChannelListener<? super SuspendableChannel> listener = new ChannelListener<SuspendableChannel>() {
            @Override
            public void handleEvent(SuspendableChannel c) {
                channel.clearWriteReady();
            }
        };
        channel.getWriteSetter().set(listener);
        channel.resumeWrites();
        channel.setWriteRequiresRead();
        channel.handleWritable();
        assertFalse(channel.isWriteReady());
        assertFalse(connectedChannelMock.isWriteResumed());
        // first, try to invoke handleWritable and then setWriteReady
        System.out.println("Attempt 1: invoke handleWritable before setWriteReady");
        channel.handleWritable();
        channel.setWriteReady();
        assertTrue(channel.isWriteReady());
        assertTrue(connectedChannelMock.isWriteAwaken());
        // clear everything
        channel.clearWriteReady();
        channel.handleWritable();
        assertFalse(channel.isWriteReady());
        assertFalse(connectedChannelMock.isWriteResumed());
        // now, try the opposite, invoke handleWritable after setWriteReady()
        System.out.println("Attempt 2: invoke handleWritable after setWriteReady");
        channel.setWriteReady();
        channel.handleWritable();
        assertFalse(channel.isWriteReady());
        assertTrue(connectedChannelMock.isWriteAwaken());
        channel.clearWriteReady();
        channel.handleWritable();
        assertFalse(channel.isWriteReady());
        assertFalse(connectedChannelMock.isWriteResumed());
        System.out.println("Attempt 3: race condition scenario involving handleWritable and setWriteReady");
        // finally, create the race condition scenario... handleWritable and setWriteReady occur
        // practically at the same time (see SetWriteReadyOnHandlingWritableChannelTestCase.btm)
        setWriteReadyThread.start();
        handleWritableThread.start();
        // joining threads
        setWriteReadyThread.join();
        handleWritableThread.join();
        assertFalse(channel.isWriteReady());
        assertTrue(connectedChannelMock.isWriteResumed());
    }

    private static class SetWriteReady implements Runnable {
        private MyTranslatingSuspendableChannel channel;

        public SetWriteReady(MyTranslatingSuspendableChannel channel) {
            this.channel = channel;
        }

        @Override
        public void run() {
            channel.setWriteReady();
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

        private static final int WRITE_READY;
        private static final Field stateField;

        static {
            try {
                Field WRITE_READY_field = TranslatingSuspendableChannel.class.getDeclaredField("WRITE_READY");
                WRITE_READY_field.setAccessible(true);
                WRITE_READY = WRITE_READY_field.getInt(null);
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

        public void handleWritable() {
            super.handleWritable();
        }

        public void setWriteReady() {
            super.setWriteReady();
        }

        public void clearWriteReady() {
            super.clearWriteReady();
        }

        public boolean isWriteReady() throws Exception {
            return Bits.allAreSet(stateField.getInt(this), WRITE_READY);
        }

        public void setWriteRequiresRead() {
            super.setWriteRequiresRead();
        }
    }
}
