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

package org.jboss.xnio.channels;

import org.jboss.xnio.ChannelSource;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.AbstractConvertingIoFuture;

/**
 * A utility class containing static methods to convert from one channel type to another.
 */
public final class Channels {
    private Channels() {
    }

    /**
     * Create a channel source for an allocated message channel.  The resulting channel uses a simple protocol to send
     * and receive messages.  First, a four-byte length field is sent in network order; then a message of that length
     * follows.  If an incoming message is too large, it is ignored.  If an outgoing message is too large, an exception
     * is thrown for that send.
     *
     * @param streamChannelSource the stream channel source to encapsulate
     * @param maxInboundMessageSize the maximum incoming message size
     * @param maxOutboundMessageSize the maximum outgoing message size
     * @return an allocated message channel source
     */
    public static ChannelSource<AllocatedMessageChannel> convertStreamToAllocatedMessage(final ChannelSource<StreamChannel> streamChannelSource, final int maxInboundMessageSize, final int maxOutboundMessageSize) {
        return new ChannelSource<AllocatedMessageChannel>() {
            public IoFuture<AllocatedMessageChannel> open(final IoHandler<? super AllocatedMessageChannel> handler) {
                final AllocatedMessageChannelStreamChannelHandler innerHandler = new AllocatedMessageChannelStreamChannelHandler(handler, maxInboundMessageSize, maxOutboundMessageSize);
                return new AbstractConvertingIoFuture<AllocatedMessageChannel, StreamChannel>(streamChannelSource.open(innerHandler)) {
                    protected AllocatedMessageChannel convert(final StreamChannel arg) {
                        return innerHandler.getChannel(arg);
                    }
                };
            }
        };
    }

    /**
     * Create a handler factory for an allocated message channel.  The resulting channel uses a simple protocol to send
     * and receive messages.  First, a four-byte length field is sent in network order; then a message of that length
     * follows.  If an incoming message is too large, it is ignored.  If an outgoing message is too large, an exception
     * is thrown for that send.
     *
     * @param handlerFactory the user allocated message channel handler factory
     * @param maxInboundMessageSize the maximum incoming message size
     * @param maxOutboundMessageSize the maximum outgoing message size
     * @return a stream channel handler factory that implements the protocol
     */
    public static IoHandlerFactory<StreamChannel> convertStreamToAllocatedMessage(final IoHandlerFactory<? super AllocatedMessageChannel> handlerFactory, final int maxInboundMessageSize, final int maxOutboundMessageSize) {
        return new IoHandlerFactory<StreamChannel>() {
            public IoHandler<? super StreamChannel> createHandler() {
                //noinspection unchecked
                return new AllocatedMessageChannelStreamChannelHandler(handlerFactory.createHandler(), maxInboundMessageSize, maxOutboundMessageSize);
            }
        };
    }
}
