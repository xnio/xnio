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

/**
 * Common channel options.
 */
public final class ChannelOption {
    private ChannelOption() {}

    /**
     * Type of service for IP sockets.  The value type for this option is {@code int}.  The value given is only
     * a hint to the operating system, and may be ignored.
     */
    public static final String IP_TOS = "IP_TOS";

    /**
     * Enable broadcast support for IP datagram sockets.  The value type for this option is {@code boolean}.  If you
     * intend to send datagrams to a broadcast address, this option must be enabled.
     */
    public static final String BROADCAST = "BROADCAST";

    /**
     * Configure a TCP socket to send an {@code RST} packet on close.  The value type for this option is {@code boolean}.
     */
    public static final String CLOSE_ABORT = "CLOSE_ABORT";

    /**
     * The receive buffer size.  The value type for this option is {@code int}.  This may be used by an XNIO provider
     * directly, or it may be passed to the underlying operating system, depending on the channel type.
     */
    public static final String RECEIVE_BUFFER = "RECEIVE_BUFFER";

    /**
     * Configure an IP socket to reuse addresses.  The value type for this option is {@code boolean}.
     */
    public static final String REUSE_ADDRESSES = "REUSE_ADDRESSES";

    /**
     * The send buffer size.  The value type for this option is {@code int}.  This may be used by an XNIO provider
     * directly, or it may be passed to the underlying operating system, depending on the channel type.
     */
    public static final String SEND_BUFFER = "SEND_BUFFER";

    /**
     * Configure a TCP socket to disable Nagle's algorithm.  The value type for this option is {@code boolean}.
     */
    public static final String TCP_NODELAY = "TCP_NODELAY";

    /**
     * Set the multicast time-to-live field for datagram sockets.  The value type for this option is {@code int}.
     */
    public static final String MULTICAST_TTL = "MULTICAST_TTL";

    /**
     * Set the IP traffic class/type-of-service for the channel.  The value type for this option is {@code int}.
     */
    public static final String IP_TRAFFIC_CLASS = "IP_TRAFFIC_CLASS";

    /**
     * Configure a TCP socket to receive out-of-band data alongside regular data.  The value type for this option is
     * {@code boolean}.
     */
    public static final String TCP_OOB_INLINE = "TCP_OOB_INLINE";

    /**
     * Configure a channel to send a periodic heartbeat of some sort.  The value type for this option is {@code boolean}.
     */
    public static final String KEEP_ALIVE = "KEEP_ALIVE";

    /**
     * Configure a server with the specified backlog.  The value type for this option is {@code int}.
     */
    public static final String BACKLOG = "BACKLOG";
}
