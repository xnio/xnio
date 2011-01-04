/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

package org.xnio.nio;

import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import org.jboss.logging.Logger;
import org.xnio.AbstractChannelThread;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
abstract class AbstractNioChannelThread extends AbstractChannelThread {

    private static final Logger log = Logger.getLogger("org.xnio.nio.channel-thread");

    private final NioSelectorRunnable runnable;

    protected AbstractNioChannelThread(final NioSelectorRunnable runnable) {
        this.runnable = runnable;
        runnable.setChannelThread(this);
    }

    protected void startShutdown() {
        runnable.shutdown();
    }

    void done() {
        shutdownFinished();
    }

    public int getLoad() {
        return runnable.getKeyLoad();
    }

    <C extends Channel> NioHandle<C> addChannel(final AbstractSelectableChannel channel, final C xnioChannel, final int ops, final NioSetter<C> setter, final boolean oneShot) throws ClosedChannelException {
        log.tracef("Adding channel %s to %s for XNIO channel %s", channel, this, xnioChannel);
        synchronized (getLock()) {
            checkState();
            final SelectionKey key = runnable.register(channel, 0);
            final NioHandle<C> handle = new NioHandle<C>(key, runnable, setter, oneShot, xnioChannel);
            key.attach(handle);
            key.interestOps(ops);
            runnable.wakeup();
            return handle;
        }
    }

    void runTask(final SelectorTask task) {
        runnable.runTask(task);
    }
}
