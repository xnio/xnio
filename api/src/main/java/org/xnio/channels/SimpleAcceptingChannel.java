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

package org.xnio.channels;

import java.io.IOException;
import org.xnio.ChannelListener;
import org.xnio.ReadChannelThread;
import org.xnio.WriteChannelThread;

/**
 * A channel which can accept connections.
 *
 * @param <C> the channel type
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface SimpleAcceptingChannel<C extends CloseableChannel> extends SuspendableAcceptChannel {

    /**
     * Attempt to accept a connection.
     *
     * @param readThread the initial read thread to use for the new channel, or {@code null} for none
     * @param writeThread the initial write thread to use for the new channel, or {@code null} for none
     * @return the new connection, or {@code null} if none is available
     * @throws IOException if an I/O error occurs
     */
    C accept(ReadChannelThread readThread, WriteChannelThread writeThread) throws IOException;

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends SimpleAcceptingChannel<C>> getAcceptSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends SimpleAcceptingChannel<C>> getCloseSetter();
}
