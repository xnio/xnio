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
import org.jboss.xnio.log.Logger;

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
    public static ChannelSource<AllocatedMessageChannel> convertStreamToAllocatedMessage(final ChannelSource<? extends StreamChannel> streamChannelSource, final int maxInboundMessageSize, final int maxOutboundMessageSize) {
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

    private static final Logger mergedLog = Logger.getLogger("org.jboss.xnio.channels.merged");

    /**
     * Create a handler that is a merged view of two separate handlers, one for read operations and one for write operations.
     * The {@code handleOpened()} and {@code handleClosed()} methods are called on each of the two sub-handlers.
     *
     * @param <T> the resultant channel type
     * @param readSide the handler to handle read operations
     * @param writeSide the handler to handle write operations
     * @return a combined handler
     */
    public static <T extends SuspendableChannel> IoHandler<T> createMergedHandler(final IoHandler<? super T> readSide, final IoHandler<? super T> writeSide) {
        return new IoHandler<T>() {
            public void handleOpened(final T channel) {
                readSide.handleOpened(channel);
                boolean ok = false;
                try {
                    writeSide.handleOpened(channel);
                    ok = true;
                } finally {
                    if (! ok) try {
                        readSide.handleClosed(channel);
                    } catch (Throwable t) {
                        mergedLog.error(t, "Error in close handler");
                    }
                }
            }

            public void handleReadable(final T channel) {
                readSide.handleReadable(channel);
            }

            public void handleWritable(final T channel) {
                writeSide.handleReadable(channel);
            }

            public void handleClosed(final T channel) {
                try {
                    readSide.handleClosed(channel);
                } catch (Throwable t) {
                    mergedLog.error(t, "Error in close handler");
                }
                try {
                    writeSide.handleClosed(channel);
                } catch (Throwable t) {
                    mergedLog.error(t, "Error in close handler");
                }
            }
        };
    }

    /**
     * Create a handler factory that is a merged view of two separate handler factories, one for read operations and one for write operations.
     *
     * @param <T> the resultant channel type
     * @param readFactory the handler factory to create handlers that handle read operations
     * @param writeFactory the handler factory to create handlers that handle write operations
     * @return a combined handler factory
     */
    public static <T extends SuspendableChannel> IoHandlerFactory<T> createMergedHandlerFactory(final IoHandlerFactory<? super T> readFactory, final IoHandlerFactory<? super T> writeFactory) {
        return new IoHandlerFactory<T>() {
            @SuppressWarnings({"unchecked"})
            public IoHandler<T> createHandler() {
                return createMergedHandler((IoHandler)readFactory.createHandler(), (IoHandler)writeFactory.createHandler());
            }
        };
    }
}
