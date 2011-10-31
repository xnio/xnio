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

import java.io.IOException;
import java.net.SocketAddress;
import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.XnioWorker;
import org.xnio.channels.BoundChannel;

final class NioBoundChannel implements BoundChannel {
    private final BoundChannel realBoundChannel;

    NioBoundChannel(final BoundChannel realBoundChannel) {
        this.realBoundChannel = realBoundChannel;
    }

    public SocketAddress getLocalAddress() {
        return realBoundChannel.getLocalAddress();
    }

    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        return realBoundChannel.getLocalAddress(type);
    }

    public ChannelListener.Setter<? extends BoundChannel> getCloseSetter() {
        return realBoundChannel.getCloseSetter();
    }

    public void close() throws IOException {
        realBoundChannel.close();
    }

    public boolean isOpen() {
        return realBoundChannel.isOpen();
    }

    public boolean supportsOption(final Option<?> option) {
        return realBoundChannel.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return realBoundChannel.getOption(option);
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return realBoundChannel.setOption(option, value);
    }

    public XnioWorker getWorker() {
        return realBoundChannel.getWorker();
    }
}
