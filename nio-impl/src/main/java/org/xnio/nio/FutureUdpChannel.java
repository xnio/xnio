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

import org.xnio.channels.MulticastMessageChannel;
import org.xnio.AbstractIoFuture;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import java.io.IOException;
import java.io.Closeable;

/**
 *
 */
class FutureUdpChannel extends AbstractIoFuture<MulticastMessageChannel> {

    private final MulticastMessageChannel channel;
    private final Closeable underlyingChannel;

    FutureUdpChannel(final MulticastMessageChannel channel, final Closeable underlyingChannel) {
        this.channel = channel;
        this.underlyingChannel = underlyingChannel;
    }

    protected boolean setException(final IOException exception) {
        return super.setException(exception);
    }

    protected boolean done() {
        return setResult(channel);
    }

    public IoFuture<MulticastMessageChannel> cancel() {
        if (setCancelled()) {
            IoUtils.safeClose(underlyingChannel);
        }
        return this;
    }
}
