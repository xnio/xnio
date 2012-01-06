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
import org.xnio.ChannelListener.Setter;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.SuspendableChannel;
import org.xnio.channels.TranslatingSuspendableChannel;

/**
 * Change read listener on channel that is handling readable. Check if the channel realizes the listener has
 * changed. 
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
@RunWith(BMUnitRunner.class)
@BMScript(dir="src/test/resources")
public class SetReadListenerOnHandlingReadableChannelTestCase {
    @Test
    public void test() throws Exception {
        // create mockery context
        final Mockery context = new JUnit4Mockery();
        // creating channel and threads
        final MyTranslatingSuspendableChannel channel = new MyTranslatingSuspendableChannel();
        final Thread setReadListenerThread = new Thread(new SetReadListener(channel, context));
        final Thread handleReadableThread = new Thread(new InvokeHandleReadable(channel));
        // set up scenario
        System.out.println("Setting scenario with listener1 and reads suspended");
        @SuppressWarnings("unchecked")
        final ChannelListener<? super SuspendableChannel> listener = context.mock(ChannelListener.class, "listener1");
        context.checking(new Expectations() {{
            oneOf(listener).handleEvent(channel);
        }});
        channel.getReadSetter().set(listener);
        channel.resumeReads();
        // starting threads
        setReadListenerThread.start();
        handleReadableThread.start();
        // joining threads
        setReadListenerThread.join();
        handleReadableThread.join();
        // was "listener that must not be called" never called? And what about listener created by setReadListener thread? Was it called exactly once?
        context.assertIsSatisfied();
    }

    private static class SetReadListener implements Runnable {
        private MyTranslatingSuspendableChannel channel;
        private Mockery context;

        public SetReadListener(MyTranslatingSuspendableChannel channel, Mockery context) {
            this.channel = channel;
            this.context = context;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            // after handleReadable#1
            channel.suspendReads();
            // after handleReadable#2
            final ChannelListener<? super SuspendableChannel> listener = context.mock(ChannelListener.class, "listener2");
            context.checking(new Expectations() {{
                oneOf(listener).handleEvent(channel);
           }});
            // on the third attempt to call handle readable, this is the scenario the channel should see
            System.out.println("Setting listener2 and resuming reads");
            channel.getReadSetter().set(listener);
            // after handleReadable#3
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
            System.out.println("Attempt to handle readable number 1");
            channel.handleReadable();
            System.out.println("Attempt to handle readable number 2");
            channel.handleReadable();
            System.out.println("Attempt to handle readable number 3");
            channel.handleReadable();
            System.out.println("Attempt to handle readable number 4");
            channel.handleReadable();
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
                public Setter<? extends SuspendableChannel> getCloseSetter() {return new ChannelListener.SimpleSetter<SuspendableChannel>();}
                public Setter<? extends SuspendableChannel> getReadSetter() {return new ChannelListener.SimpleSetter<SuspendableChannel>();}
                public Setter<? extends SuspendableChannel> getWriteSetter() {return new ChannelListener.SimpleSetter<SuspendableChannel>();}
            });
        }
        
        public void handleReadable() {
            super.handleReadable();
        }
    }
}
