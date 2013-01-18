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

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.SuspendableChannel;
import org.xnio.channels.TranslatingSuspendableChannel;

/**
 * Change write listener on a channel that is handling writable. Check if the channel realizes the listener has
 * changed. 
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
@RunWith(BMUnitRunner.class)
@BMScript(dir="src/test/resources")
public class SetWriteListenerOnHandlingWritableChannelTestCase {
    @Test
    public void test() throws Exception {
        // create mockery context
        final Mockery context = new JUnit4Mockery();
        // creating channel and threads
        final MyTranslatingSuspendableChannel channel = new MyTranslatingSuspendableChannel();
        final Thread setWriteListenerThread = new Thread(new SetWriteListener(channel, context));
        final Thread handleWritableThread = new Thread(new InvokeHandleWritable(channel));
        // set up scenario
        System.out.println("Setting scenario with listener1 and writes suspended");
        @SuppressWarnings("unchecked")
        final ChannelListener<? super SuspendableChannel> listener = context.mock(ChannelListener.class, "listener1");
        context.checking(new Expectations() {{
            oneOf(listener).handleEvent(channel);
        }});
        channel.getWriteSetter().set(listener);
        channel.resumeWrites();
        // starting threads
        setWriteListenerThread.start();
        handleWritableThread.start();
        // joining threads
        setWriteListenerThread.join();
        handleWritableThread.join();
        // was "listener that must not be called" never called? And what about listener created by setWriteListener thread? Was it called exactly once?
        context.assertIsSatisfied();
    }

    private static class SetWriteListener implements Runnable {
        private MyTranslatingSuspendableChannel channel;
        private Mockery context;

        public SetWriteListener(MyTranslatingSuspendableChannel channel, Mockery context) {
            this.channel = channel;
            this.context = context;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            // after handleWritable#1
            channel.suspendWrites();
            // after handleWritable#2
            final ChannelListener<? super SuspendableChannel> listener = context.mock(ChannelListener.class, "listener2");
            context.checking(new Expectations() {{
                oneOf(listener).handleEvent(channel);
           }});
            // on the third attempt to call handle writable, this is the scenario the channel should see
            System.out.println("Setting listener2 and resuming writes");
            channel.getWriteSetter().set(listener);
            // after handleWritable#3
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
            System.out.println("Attempt to handle writable number 1");
            channel.handleWritable();
            System.out.println("Attempt to handle writable number 2");
            channel.handleWritable();
            System.out.println("Attempt to handle writable number 3");
            channel.handleWritable();
            System.out.println("Attempt to handle writable number 4");
            channel.handleWritable();
        }
        
    }

    private static class MyTranslatingSuspendableChannel extends TranslatingSuspendableChannel<SuspendableChannel, SuspendableChannel> {

        protected MyTranslatingSuspendableChannel() {
            super(new SuspendableChannel() {
                public XnioWorker getWorker() { return null; }
                public void close() throws IOException { }
                public boolean isOpen() { return false; }
                public boolean supportsOption(Option<?> option) { return false; }
                public <T> T getOption(Option<T> option) throws IOException { return null; }
                public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException { return null; }
                public void suspendReads() {}
                public void resumeReads() {}
                public boolean isReadResumed() { return false; }
                public void wakeupReads() {}
                public void shutdownReads() throws IOException {}
                public void awaitReadable() throws IOException {}
                public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {}
                public XnioExecutor getReadThread() { return null; }
                public void suspendWrites() {}
                public void resumeWrites() {}
                public boolean isWriteResumed() {return false;}
                public void wakeupWrites() {}
                public void shutdownWrites() throws IOException {}
                public void awaitWritable() throws IOException {}
                public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {}
                public XnioExecutor getWriteThread() { return null; }
                public boolean flush() throws IOException { return true; }
                public ChannelListener.Setter<? extends SuspendableChannel> getCloseSetter() {return new ChannelListener.SimpleSetter<SuspendableChannel>();}
                public ChannelListener.Setter<? extends SuspendableChannel> getReadSetter() {return new ChannelListener.SimpleSetter<SuspendableChannel>();}
                public ChannelListener.Setter<? extends SuspendableChannel> getWriteSetter() {return new ChannelListener.SimpleSetter<SuspendableChannel>();}
            });
        }
        
        public void handleWritable() {
            super.handleWritable();
        }
    }
}
