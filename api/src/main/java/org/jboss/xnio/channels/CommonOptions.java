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

import static org.jboss.xnio.channels.PlainChannelOption.createOption;

/**
 * Common channel options.
 */
public final class CommonOptions {

    private CommonOptions() {}

    /**
     * Type of service for IP sockets.  The value type for this option is {@code int}.  The value given is only
     * a hint to the operating system, and may be ignored.
     */
    public static final ChannelOption<Integer> IP_TOS = createOption("IP_TOS", Integer.class);

    /**
     * Enable broadcast support for IP datagram sockets.  The value type for this option is {@code boolean}.  If you
     * intend to send datagrams to a broadcast address, this option must be enabled.
     */
    public static final ChannelOption<Boolean> BROADCAST = createOption("BROADCAST", Boolean.class);

    /**
     * Configure a TCP socket to send an {@code RST} packet on close.  The value type for this option is {@code boolean}.
     */
    public static final ChannelOption<Boolean> CLOSE_ABORT = createOption("CLOSE_ABORT", Boolean.class);

    /**
     * The receive buffer size.  The value type for this option is {@code int}.  This may be used by an XNIO provider
     * directly, or it may be passed to the underlying operating system, depending on the channel type.
     */
    public static final ChannelOption<Integer> RECEIVE_BUFFER = createOption("RECEIVE_BUFFER", Integer.class);

    /**
     * Configure an IP socket to reuse addresses.  The value type for this option is {@code boolean}.
     */
    public static final ChannelOption<Boolean> REUSE_ADDRESSES = createOption("REUSE_ADDRESSES", Boolean.class);

    /**
     * The send buffer size.  The value type for this option is {@code int}.  This may be used by an XNIO provider
     * directly, or it may be passed to the underlying operating system, depending on the channel type.
     */
    public static final ChannelOption<Integer> SEND_BUFFER = createOption("SEND_BUFFER", Integer.class);

    /**
     * Configure a TCP socket to disable Nagle's algorithm.  The value type for this option is {@code boolean}.
     */
    public static final ChannelOption<Boolean> TCP_NODELAY = createOption("TCP_NODELAY", Boolean.class);

    /**
     * Set the multicast time-to-live field for datagram sockets.  The value type for this option is {@code int}.
     */
    public static final ChannelOption<Integer> MULTICAST_TTL = createOption("MULTICAST_TTL", Integer.class);

    /**
     * Set the IP traffic class/type-of-service for the channel.  The value type for this option is {@code int}.
     */
    public static final ChannelOption<Integer> IP_TRAFFIC_CLASS = createOption("IP_TRAFFIC_CLASS", Integer.class);

    /**
     * Configure a TCP socket to receive out-of-band data alongside regular data.  The value type for this option is
     * {@code boolean}.
     */
    public static final ChannelOption<Boolean> TCP_OOB_INLINE = createOption("TCP_OOB_INLINE", Boolean.class);

    /**
     * Configure a channel to send a periodic heartbeat of some sort.  The value type for this option is {@code boolean}.
     */
    public static final ChannelOption<Boolean> KEEP_ALIVE = createOption("KEEP_ALIVE", Boolean.class);

    /**
     * Configure a server with the specified backlog.  The value type for this option is {@code int}.
     */
    public static final ChannelOption<Integer> BACKLOG = createOption("BACKLOG", Integer.class);

    /**
     * Configure an acceptor to manage connections or to leave them unmanaged.  A managed entity will appear in any registered MBean server,
     * but there may be a performance penalty associated with management.  In general it is recommended to always
     * enable management, unless a specific performance problem is identified.
     */
    public static final ChannelOption<Boolean> MANAGE_CONNECTIONS = createOption("MANAGE_CONNECTIONS", Boolean.class);
}
