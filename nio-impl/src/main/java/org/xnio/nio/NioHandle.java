/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.atomic.AtomicBoolean;
import org.xnio.ChannelListeners;

final class NioHandle<C extends Channel> {
    private final SelectionKey selectionKey;
    private final NioSelectorRunnable selectorRunnable;
    private final NioSetter<C> handlerSetter;
    private final boolean oneShot;
    private final C channel;

    NioHandle(final SelectionKey selectionKey, final NioSelectorRunnable selectorRunnable, final NioSetter<C> handlerSetter, final boolean oneShot, final C channel) {
        this.selectionKey = selectionKey;
        this.selectorRunnable = selectorRunnable;
        this.handlerSetter = handlerSetter;
        this.oneShot = oneShot;
        this.channel = channel;
    }

    SelectionKey getSelectionKey() {
        return selectionKey;
    }

    NioSelectorRunnable getSelectorRunnable() {
        return selectorRunnable;
    }

    NioSetter<C> getHandlerSetter() {
        return handlerSetter;
    }

    void cancelKey() {
        final AtomicBoolean lock = new AtomicBoolean();
        selectorRunnable.runTask(new SelectorTask() {
            public void run(final Selector selector) {
                selectionKey.cancel();
                synchronized (lock) {
                    lock.set(true);
                    lock.notifyAll();
                }
            }
        });
        boolean intr = false;
        try {
            synchronized (lock) {
                while (! lock.get()) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        intr = true;
                    }
                }
            }
        } finally {
            if (intr) {
                Thread.currentThread().interrupt();
            }
        }
    }

    void resume(final int op) {
        selectionKey.interestOps(op);
        selectorRunnable.wakeup();
    }

    void suspend() {
        selectionKey.interestOps(0);
        selectorRunnable.wakeup();
    }

    boolean isOneShot() {
        return oneShot;
    }

    C getChannel() {
        return channel;
    }

    void invoke() {
        ChannelListeners.invokeChannelListener(channel, handlerSetter.get());
    }
}
