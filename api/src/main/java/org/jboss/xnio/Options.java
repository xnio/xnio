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

package org.jboss.xnio;

/**
 * Common channel options.
 *
 * @apiviz.exclude
 */
public final class Options {

    private Options() {}

    /**
     * Enable multicast support for a socket.  The value type for this option is {@code boolean}.  Note that some
     * implementations may add overhead when multicast sockets are in use.
     */
    public static final Option<Boolean> MULTICAST = Option.simple(Options.class, "MULTICAST", Boolean.class);

    /**
     * Type of service for IP sockets.  The value type for this option is {@code int}.  The value given is only
     * a hint to the operating system, and may be ignored.
     */
    public static final Option<Integer> IP_TOS = Option.simple(Options.class, "IP_TOS", Integer.class);

    /**
     * Enable broadcast support for IP datagram sockets.  The value type for this option is {@code boolean}.  If you
     * intend to send datagrams to a broadcast address, this option must be enabled.
     */
    public static final Option<Boolean> BROADCAST = Option.simple(Options.class, "BROADCAST", Boolean.class);

    /**
     * Configure a TCP socket to send an {@code RST} packet on close.  The value type for this option is {@code boolean}.
     */
    public static final Option<Boolean> CLOSE_ABORT = Option.simple(Options.class, "CLOSE_ABORT", Boolean.class);

    /**
     * The receive buffer size.  The value type for this option is {@code int}.  This may be used by an XNIO provider
     * directly, or it may be passed to the underlying operating system, depending on the channel type.
     */
    public static final Option<Integer> RECEIVE_BUFFER = Option.simple(Options.class, "RECEIVE_BUFFER", Integer.class);

    /**
     * Configure an IP socket to reuse addresses.  The value type for this option is {@code boolean}.
     */
    public static final Option<Boolean> REUSE_ADDRESSES = Option.simple(Options.class, "REUSE_ADDRESSES", Boolean.class);

    /**
     * The send buffer size.  The value type for this option is {@code int}.  This may be used by an XNIO provider
     * directly, or it may be passed to the underlying operating system, depending on the channel type.
     */
    public static final Option<Integer> SEND_BUFFER = Option.simple(Options.class, "SEND_BUFFER", Integer.class);

    /**
     * Configure a TCP socket to disable Nagle's algorithm.  The value type for this option is {@code boolean}.
     */
    public static final Option<Boolean> TCP_NODELAY = Option.simple(Options.class, "TCP_NODELAY", Boolean.class);

    /**
     * Set the multicast time-to-live field for datagram sockets.  The value type for this option is {@code int}.
     */
    public static final Option<Integer> MULTICAST_TTL = Option.simple(Options.class, "MULTICAST_TTL", Integer.class);

    /**
     * Set the IP traffic class/type-of-service for the channel.  The value type for this option is {@code int}.
     */
    public static final Option<Integer> IP_TRAFFIC_CLASS = Option.simple(Options.class, "IP_TRAFFIC_CLASS", Integer.class);

    /**
     * Configure a TCP socket to receive out-of-band data alongside regular data.  The value type for this option is
     * {@code boolean}.
     */
    public static final Option<Boolean> TCP_OOB_INLINE = Option.simple(Options.class, "TCP_OOB_INLINE", Boolean.class);

    /**
     * Configure a channel to send a periodic heartbeat of some sort.  The value type for this option is {@code boolean}.
     */
    public static final Option<Boolean> KEEP_ALIVE = Option.simple(Options.class, "KEEP_ALIVE", Boolean.class);

    /**
     * Configure a server with the specified backlog.  The value type for this option is {@code int}.
     */
    public static final Option<Integer> BACKLOG = Option.simple(Options.class, "BACKLOG", Integer.class);

    /**
     * Configure an acceptor to manage connections or to leave them unmanaged.  A managed entity will appear in any registered MBean server,
     * but there may be a performance penalty associated with management.  In general it is recommended to always
     * enable management, unless a specific performance problem is identified.
     *
     * @since 1.2
     */
    public static final Option<Boolean> MANAGE_CONNECTIONS = Option.simple(Options.class, "MANAGE_CONNECTIONS", Boolean.class);

    /**
     * The maximum inbound message size.
     *
     * @since 2.0
     */
    public static final Option<Integer> MAX_INBOUND_MESSAGE_SIZE = Option.simple(Options.class, "MAX_INBOUND_MESSAGE_SIZE", Integer.class);

    /**
     * The maximum outbound message size.
     *
     * @since 2.0
     */
    public static final Option<Integer> MAX_OUTBOUND_MESSAGE_SIZE = Option.simple(Options.class, "MAX_OUTBOUND_MESSAGE_SIZE", Integer.class);

    /**
     * Signify that client authentication is requested on an SSL channel.
     *
     * @since 2.0
     */
    public static final Option<Boolean> SSL_WANT_CLIENT_AUTH = Option.simple(Options.class, "SSL_WANT_CLIENT_AUTH", Boolean.class);

    /**
     * Signify that client authentication is required on an SSL channel.
     *
     * @since 2.0
     */
    public static final Option<Boolean> SSL_NEED_CLIENT_AUTH = Option.simple(Options.class, "SSL_NEED_CLIENT_AUTH", Boolean.class);

    /**
     * Specify the cipher suites for an SSL/TLS session.  If a listed cipher suites is not supported, it is ignored; however, if you
     * specify a list of cipher suites, none of which are supported, an exception will be thrown.
     *
     * @since 2.0
     */
    public static final Option<Sequence<String>> SSL_ENABLED_CIPHER_SUITES = Option.sequence(Options.class, "SSL_ENABLED_CIPHER_SUITES", String.class);

    /**
     * Get the supported cipher suites for an SSL/TLS session.  This option is generally read-only.
     *
     * @since 2.0
     */
    public static final Option<Sequence<String>> SSL_SUPPORTED_CIPHER_SUITES = Option.sequence(Options.class, "SSL_SUPPORTED_CIPHER_SUITES", String.class);

    /**
     * Specify the enabled protocols for an SSL/TLS session.  If a listed protocol is not supported, it is ignored; however, if you
     * specify a list of protocols, none of which are supported, an exception will be thrown.
     *
     * @since 2.0
     */
    public static final Option<Sequence<String>> SSL_ENABLED_PROTOCOLS = Option.sequence(Options.class, "SSL_ENABLED_PROTOCOLS", String.class);

    /**
     * Get the supported protocols for an SSL/TLS session.  This option is generally read-only.
     *
     * @since 2.0
     */
    public static final Option<Sequence<String>> SSL_SUPPORTED_PROTOCOLS = Option.sequence(Options.class, "SSL_SUPPORTED_PROTOCOLS", String.class);

    /**
     * Specify the requested provider for an SSL/TLS session.
     *
     * @since 2.0
     */
    public static final Option<String> SSL_PROVIDER = Option.simple(Options.class, "SSL_PROVIDER", String.class);

    /**
     * Specify the SSL send buffer size.
     *
     * @since 2.0
     */
    public static final Option<Integer> SSL_SEND_BUFFER = Option.simple(Options.class, "SSL_SEND_BUFFER", Integer.class);

    /**
     * Specify the SSL receive buffer size.
     *
     * @since 2.0
     */
    public static final Option<Integer> SSL_RECEIVE_BUFFER = Option.simple(Options.class, "SSL_RECEIVE_BUFFER", Integer.class);

    /**
     * Enable or disable session creation for an SSL connection.  Defaults to {@code true} to enable session creation.
     *
     * @since 2.0
     */
    public static final Option<Boolean> SSL_ENABLE_SESSION_CREATION = Option.simple(Options.class, "SSL_ENABLE_SESSION_CREATION", Boolean.class);

    /**
     * Specify whether SSL conversations should be in client or server mode.  Defaults to {@code false} (use server mode).
     *
     * @since 2.0
     */
    public static final Option<Boolean> SSL_USE_CLIENT_MODE = Option.simple(Options.class, "SSL_USE_CLIENT_MODE", Boolean.class);

    /**
     * Determine whether the channel is encrypted, or employs some other level of security.  The interpretation of this flag
     * is specific to the channel in question; however, whatever the channel type, this flag is generally read-only.
     */
    public static final Option<Boolean> SECURE = Option.simple(Options.class, "SECURE", Boolean.class);
}
