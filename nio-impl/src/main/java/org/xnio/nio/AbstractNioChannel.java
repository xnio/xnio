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

package org.xnio.nio;

import java.nio.channels.ClosedChannelException;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.XnioWorker;
import org.xnio.channels.CloseableChannel;

abstract class AbstractNioChannel<C extends AbstractNioChannel<C>> implements CloseableChannel {

    private final ChannelListener.SimpleSetter<C> closeSetter = new ChannelListener.SimpleSetter<C>();

    protected volatile NioXnioWorker worker;

    public AbstractNioChannel(final NioXnioWorker worker) {
        this.worker = worker;
    }

    public final XnioWorker getWorker() {
        return worker;
    }

    public final ChannelListener.Setter<? extends C> getCloseSetter() {
        return closeSetter;
    }

    @SuppressWarnings("unchecked")
    protected final C typed() {
        return (C) this;
    }

    protected final void invokeCloseHandler() {
        ChannelListeners.invokeChannelListener(typed(), closeSetter.get());
    }

    void migrateTo(NioXnioWorker worker) throws ClosedChannelException {
        this.worker = worker;
    }
}
