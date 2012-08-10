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
package org.xnio.nio.test;

import java.nio.channels.Channel;
import java.util.concurrent.CountDownLatch;

import org.xnio.ChannelListener;

/**
 * Utility channel listener.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
class TestChannelListener<C extends Channel> implements ChannelListener<C> {

    private boolean invoked = false;
    private C channel = null;
    private CountDownLatch latch = new CountDownLatch(1);

    @Override
    public synchronized void handleEvent(C c) {
        invoked = true;
        channel = c;
        latch.countDown();
    }

    public boolean isInvoked() {
        final CountDownLatch currentLatch;
        synchronized (this) {
            currentLatch = latch;
        }
        try {
            currentLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        synchronized (this) {
            return invoked;
        }
    }

    public synchronized boolean isInvokedYet() {
        return invoked;
    }

    public synchronized C getChannel() {
        return channel;
    }

    public synchronized void clear() {
        invoked = false;
        channel = null;
        latch = new CountDownLatch(1);
    }
}