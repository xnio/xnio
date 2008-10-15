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

package org.jboss.xnio.nio;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.Executor;

/**
 *
 */
public final class NioHandle {
    private final SelectionKey selectionKey;
    private final NioSelectorRunnable selectorRunnable;
    private final Runnable handler;
    private final Executor handlerExecutor;
    private final boolean oneshot;

    NioHandle(final SelectionKey selectionKey, final NioSelectorRunnable selectorRunnable, final Runnable handler, final Executor handlerExecutor, final boolean oneshot) {
        this.selectionKey = selectionKey;
        this.selectorRunnable = selectorRunnable;
        this.handler = handler;
        this.handlerExecutor = handlerExecutor;
        this.oneshot = oneshot;
    }

    SelectionKey getSelectionKey() {
        return selectionKey;
    }

    NioSelectorRunnable getSelectorRunnable() {
        return selectorRunnable;
    }

    Runnable getHandler() {
        return handler;
    }

    Executor getHandlerExecutor() {
        return handlerExecutor;
    }

    void cancelKey() {
        selectorRunnable.runTask(new SelectorTask() {
            public void run(final Selector selector) {
                selectionKey.cancel();
            }
        });
    }

    void resume(final int op) {
        selectionKey.interestOps(op);
        selectorRunnable.wakeup();
    }

    void suspend() {
        selectionKey.interestOps(0);
        selectorRunnable.wakeup();
    }

    boolean isOneshot() {
        return oneshot;
    }
}
