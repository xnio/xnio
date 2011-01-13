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
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;

final class NioHandle<C extends Channel> {
    private final SelectionKey selectionKey;
    private final AbstractNioChannelThread channelThread;
    private final NioSetter<C> handlerSetter;
    private final C channel;

    NioHandle(final SelectionKey selectionKey, final AbstractNioChannelThread channelThread, final NioSetter<C> handlerSetter, final C channel) {
        this.selectionKey = selectionKey;
        this.channelThread = channelThread;
        this.handlerSetter = handlerSetter;
        this.channel = channel;
    }

    SelectionKey getSelectionKey() {
        return selectionKey;
    }

    AbstractNioChannelThread getChannelThread() {
        return channelThread;
    }

    NioSetter<C> getHandlerSetter() {
        return handlerSetter;
    }

    void cancelKey() {
        channelThread.cancelKey(selectionKey);
    }

    void resume(final int op) {
        channelThread.setOps(selectionKey, op);
    }

    void suspend() {
        channelThread.setOps(selectionKey, 0);
    }

    C getChannel() {
        return channel;
    }

    void invoke() {
        final ChannelListener<? super C> listener = handlerSetter.get();
        if (listener == null) {
            // prevent runaway
            suspend();
        } else {
            ChannelListeners.invokeChannelListener(channel, listener);
        }
    }
}
