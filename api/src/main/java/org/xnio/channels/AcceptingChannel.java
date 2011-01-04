/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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
 * A channel which can accept inbound connections from remote endpoints.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @param <C> the channel type
 */
public interface AcceptingChannel<C extends ConnectedChannel> extends BoundChannel, SimpleAcceptingChannel<C> {

    /** {@inheritDoc} */
    C accept(ReadChannelThread readThread, WriteChannelThread writeThread) throws IOException;

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends AcceptingChannel<C>> getAcceptSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends AcceptingChannel<C>> getCloseSetter();
}
