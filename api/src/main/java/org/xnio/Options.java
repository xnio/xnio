/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xnio;

import org.xnio.channels.ReadTimeoutException;
import org.xnio.channels.SuspendableWriteChannel;
import org.xnio.channels.WriteTimeoutException;
import org.xnio.sasl.SaslQop;
import org.xnio.sasl.SaslStrength;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.security.sasl.Sasl;
import org.xnio.ssl.SslConnection;

/**
 * Common channel options.
 *
 * @apiviz.exclude
 */
public final class Options {

    private Options() {}

    /**
     * Enable or disable blocking I/O for a newly created channel thread.
     */
    public static final Option<Boolean> ALLOW_BLOCKING = Option.simple(Options.class, "ALLOW_BLOCKING", Boolean.class);

    /**
     * Enable multicast support for a socket.  The value type for this option is {@code boolean}.  Note that some
     * implementations may add overhead when multicast sockets are in use.
     */
    public static final Option<Boolean> MULTICAST = Option.simple(Options.class, "MULTICAST", Boolean.class);

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
     * directly, or it may be passed to the underlying operating system, depending on the channel type.  Buffer
     * sizes must always be greater than 0.  Note that this value is just a hint; if the application needs to know
     * what value was actually stored for this option, it must call {@code getOption(Options.RECEIVE_BUFFER)} on the
     * channel to verify.  On most operating systems, the receive buffer size may not be changed on a socket after
     * it is connected; in these cases, calling {@code setOption(Options.RECEIVE_BUFFER, val)} will return {@code null}.
     */
    public static final Option<Integer> RECEIVE_BUFFER = Option.simple(Options.class, "RECEIVE_BUFFER", Integer.class);

    /**
     * Configure an IP socket to reuse addresses.  The value type for this option is {@code boolean}.
     */
    public static final Option<Boolean> REUSE_ADDRESSES = Option.simple(Options.class, "REUSE_ADDRESSES", Boolean.class);

    /**
     * The send buffer size.  The value type for this option is {@code int}.  This may be used by an XNIO provider
     * directly, or it may be passed to the underlying operating system, depending on the channel type.  Buffer
     * sizes must always be greater than 0.  Note that this value is just a hint; if the application needs to know
     * what value was actually stored for this option, it must call {@code getOption(Options.SEND_BUFFER)} on the
     * channel to verify.
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
     * Configure a channel to send TCP keep-alive messages in an implementation-dependent manner. The value type for
     * this option is {@code boolean}.
     */
    public static final Option<Boolean> KEEP_ALIVE = Option.simple(Options.class, "KEEP_ALIVE", Boolean.class);

    /**
     * Configure a server with the specified backlog.  The value type for this option is {@code int}.
     */
    public static final Option<Integer> BACKLOG = Option.simple(Options.class, "BACKLOG", Integer.class);

    /**
     * Configure a read timeout for a socket, in milliseconds.  If the given amount of time elapses without
     * a successful read taking place, the socket's next read will throw a {@link ReadTimeoutException}.
     */
    public static final Option<Integer> READ_TIMEOUT = Option.simple(Options.class, "READ_TIMEOUT", Integer.class);

    /**
     * Configure a write timeout for a socket, in milliseconds.  If the given amount of time elapses without
     * a successful write taking place, the socket's next write will throw a {@link WriteTimeoutException}.
     */
    public static final Option<Integer> WRITE_TIMEOUT = Option.simple(Options.class, "WRITE_TIMEOUT", Integer.class);

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
     * Specify whether SSL should be enabled.  If specified in conjunction with {@link #SSL_STARTTLS} then SSL will not
     * be negotiated until {@link org.xnio.channels.SslChannel#startHandshake()} or
     * {@link SslConnection#startHandshake()}  is called.
     *
     * @since 3.0
     */
    public static final Option<Boolean> SSL_ENABLED = Option.simple(Options.class, "SSL_ENABLED", Boolean.class);

    /**
     * Specify the SSL client authentication mode.
     *
     * @since 2.0
     */
    public static final Option<SslClientAuthMode> SSL_CLIENT_AUTH_MODE = Option.simple(Options.class, "SSL_CLIENT_AUTH_MODE", SslClientAuthMode.class);

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
     * Specify the protocol name for an SSL context.
     *
     * @since 2.1
     */
    public static final Option<String> SSL_PROTOCOL = Option.simple(Options.class, "SSL_PROTOCOL", String.class);

    /**
     * Enable or disable session creation for an SSL connection.  Defaults to {@code true} to enable session creation.
     *
     * @since 2.0
     */
    public static final Option<Boolean> SSL_ENABLE_SESSION_CREATION = Option.simple(Options.class, "SSL_ENABLE_SESSION_CREATION", Boolean.class);

    /**
     * Specify whether SSL conversations should be in client or server mode.  Defaults to {@code false} (use server mode).  If
     * set to {@code true}, the client and server side swap negotiation roles.
     *
     * @since 2.0
     */
    public static final Option<Boolean> SSL_USE_CLIENT_MODE = Option.simple(Options.class, "SSL_USE_CLIENT_MODE", Boolean.class);

    /**
     * The size of the SSL client session cache.
     *
     * @since 3.0
     */
    public static final Option<Integer> SSL_CLIENT_SESSION_CACHE_SIZE = Option.simple(Options.class, "SSL_CLIENT_SESSION_CACHE_SIZE", Integer.class);

    /**
     * The SSL client session timeout (in seconds).
     *
     * @since 3.0
     */
    public static final Option<Integer> SSL_CLIENT_SESSION_TIMEOUT = Option.simple(Options.class, "SSL_CLIENT_SESSION_TIMEOUT", Integer.class);

    /**
     * The size of the SSL server session cache.
     *
     * @since 3.0
     */
    public static final Option<Integer> SSL_SERVER_SESSION_CACHE_SIZE = Option.simple(Options.class, "SSL_SERVER_SESSION_CACHE_SIZE", Integer.class);

    /**
     * The SSL server session timeout (in seconds).
     *
     * @since 3.0
     */
    public static final Option<Integer> SSL_SERVER_SESSION_TIMEOUT = Option.simple(Options.class, "SSL_SERVER_SESSION_TIMEOUT", Integer.class);

    /**
     * The possible key manager classes to use for a JSSE SSL context.
     *
     * @since 3.0
     */
    public static final Option<Sequence<Class<? extends KeyManager>>> SSL_JSSE_KEY_MANAGER_CLASSES = Option.typeSequence(Options.class, "SSL_JSSE_KEY_MANAGER_CLASSES", KeyManager.class);

    /**
     * The possible trust store classes to use for a JSSE SSL context.
     *
     * @since 3.0
     */
    public static final Option<Sequence<Class<? extends TrustManager>>> SSL_JSSE_TRUST_MANAGER_CLASSES = Option.typeSequence(Options.class, "SSL_JSSE_TRUST_MANAGER_CLASSES", TrustManager.class);

    /**
     * The configuration of a secure RNG for SSL usage.
     *
     * @since 3.0
     */
    public static final Option<OptionMap> SSL_RNG_OPTIONS = Option.simple(Options.class, "SSL_RNG_OPTIONS", OptionMap.class);

    /**
     * The packet buffer size for SSL.
     *
     * @since 3.0
     */
    public static final Option<Integer> SSL_PACKET_BUFFER_SIZE = Option.simple(Options.class, "SSL_PACKET_BUFFER_SIZE", Integer.class);

    /**
     * The application buffer size for SSL.
     *
     * @since 3.0
     */
    public static final Option<Integer> SSL_APPLICATION_BUFFER_SIZE = Option.simple(Options.class, "SSL_APPLICATION_BUFFER_SIZE", Integer.class);

    /**
     * The size of the allocation region to use for SSL packet buffers.
     *
     * @since 3.0
     */
    public static final Option<Integer> SSL_PACKET_BUFFER_REGION_SIZE = Option.simple(Options.class, "SSL_PACKET_BUFFER_REGION_SIZE", Integer.class);

    /**
     * The size of the allocation region to use for SSL application buffers.
     *
     * @since 3.0
     */
    public static final Option<Integer> SSL_APPLICATION_BUFFER_REGION_SIZE = Option.simple(Options.class, "SSL_APPLICATION_BUFFER_REGION_SIZE", Integer.class);

    /**
     * Specify whether to use STARTTLS mode (in which a connection starts clear and switches to TLS on demand).
     *
     * @since 3.0
     */
    public static final Option<Boolean> SSL_STARTTLS = Option.simple(Options.class, "SSL_STARTTLS", Boolean.class);

    /**
     * Specify the (non-authoritative) name of the peer host to use for the purposes of session reuse, as well as
     * for the use of certain cipher suites (such as Kerberos).  If not given, defaults to the host name of the
     * socket address of the peer.
     */
    public static final Option<String> SSL_PEER_HOST_NAME = Option.simple(Options.class, "SSL_PEER_HOST_NAME", String.class);

    /**
     * Specify the (non-authoritative) port number of the peer port number to use for the purposes of session reuse, as well as
     * for the use of certain cipher suites.  If not given, defaults to the port number of the socket address of the peer.
     */
    public static final Option<Integer> SSL_PEER_PORT = Option.simple(Options.class, "SSL_PEER_PORT", Integer.class);

    /**
     * Hint to the SSL engine that the key manager implementation(s) is/are non-blocking, so they can be executed
     * in the I/O thread, possibly improving performance by decreasing latency.
     */
    public static final Option<Boolean> SSL_NON_BLOCKING_KEY_MANAGER = Option.simple(Options.class, "SSL_NON_BLOCKING_KEY_MANAGER", Boolean.class);

    /**
     * Hint to the SSL engine that the trust manager implementation(s) is/are non-blocking, so they can be executed
     * in the I/O thread, possibly improving performance by decreasing latency.
     */
    public static final Option<Boolean> SSL_NON_BLOCKING_TRUST_MANAGER = Option.simple(Options.class, "SSL_NON_BLOCKING_TRUST_MANAGER", Boolean.class);

    /**
     * Specify whether direct buffers should be used for socket communications.
     *
     * @since 3.0
     */
    public static final Option<Boolean> USE_DIRECT_BUFFERS = Option.simple(Options.class, "USE_DIRECT_BUFFERS", Boolean.class);

    /**
     * Determine whether the channel is encrypted, or employs some other level of security.  The interpretation of this flag
     * is specific to the channel in question; however, whatever the channel type, this flag is generally read-only.
     */
    public static final Option<Boolean> SECURE = Option.simple(Options.class, "SECURE", Boolean.class);

    /**
     * Specify whether SASL mechanisms which implement forward secrecy between sessions are required.
     *
     * @see Sasl#POLICY_FORWARD_SECRECY
     */
    public static final Option<Boolean> SASL_POLICY_FORWARD_SECRECY = Option.simple(Options.class, "SASL_POLICY_FORWARD_SECRECY", Boolean.class);

    /**
     * Specify whether SASL mechanisms which are susceptible to active (non-dictionary) attacks are permitted.
     *
     * @see Sasl#POLICY_NOACTIVE
     */
    public static final Option<Boolean> SASL_POLICY_NOACTIVE = Option.simple(Options.class, "SASL_POLICY_NOACTIVE", Boolean.class);

    /**
     * Specify whether SASL mechanisms which accept anonymous logins are permitted.
     *
     * @see Sasl#POLICY_NOANONYMOUS
     */
    public static final Option<Boolean> SASL_POLICY_NOANONYMOUS = Option.simple(Options.class, "SASL_POLICY_NOANONYMOUS", Boolean.class);

    /**
     * Specify whether SASL mechanisms which are susceptible to passive dictionary attacks are permitted.
     *
     * @see Sasl#POLICY_NODICTIONARY
     */
    public static final Option<Boolean> SASL_POLICY_NODICTIONARY = Option.simple(Options.class, "SASL_POLICY_NODICTIONARY", Boolean.class);

    /**
     * Specify whether SASL mechanisms which are susceptible to simple plain passive attacks are permitted.
     *
     * @see Sasl#POLICY_NOPLAINTEXT
     */
    public static final Option<Boolean> SASL_POLICY_NOPLAINTEXT = Option.simple(Options.class, "SASL_POLICY_NOPLAINTEXT", Boolean.class);

    /**
     * Specify whether SASL mechanisms which pass client credentials are required.
     *
     * @see Sasl#POLICY_PASS_CREDENTIALS
     */
    public static final Option<Boolean> SASL_POLICY_PASS_CREDENTIALS = Option.simple(Options.class, "SASL_POLICY_PASS_CREDENTIALS", Boolean.class);

    /**
     * Specify the SASL quality-of-protection to use.
     *
     * @see Sasl#QOP
     */
    public static final Option<Sequence<SaslQop>> SASL_QOP = Option.sequence(Options.class, "SASL_QOP", SaslQop.class);

    /**
     * Specify the SASL cipher strength to use.
     *
     * @see Sasl#STRENGTH
     */
    public static final Option<SaslStrength> SASL_STRENGTH = Option.simple(Options.class, "SASL_STRENGTH", SaslStrength.class);

    /**
     * Specify whether the SASL server must authenticate to the client.
     *
     * @see Sasl#SERVER_AUTH
     */
    public static final Option<Boolean> SASL_SERVER_AUTH = Option.simple(Options.class, "SASL_SERVER_AUTH", Boolean.class);

    /**
     * Specify whether SASL mechanisms should attempt to reuse authenticated session information.
     *
     * @see Sasl#REUSE
     */
    public static final Option<Boolean> SASL_REUSE = Option.simple(Options.class, "SASL_REUSE", Boolean.class);

    /**
     * A list of SASL mechanisms, in decreasing order of preference.
     */
    public static final Option<Sequence<String>> SASL_MECHANISMS = Option.sequence(Options.class, "SASL_MECHANISMS", String.class);

    /**
     * A list of disallowed SASL mechanisms.
     */
    public static final Option<Sequence<String>> SASL_DISALLOWED_MECHANISMS = Option.sequence(Options.class, "SASL_DISALLOWED_MECHANISMS", String.class);

    /**
     * A list of provider specific SASL properties.
     */
    public static final Option<Sequence<Property>> SASL_PROPERTIES = Option.sequence(Options.class, "SASL_PROPERTIES", Property.class);

    /**
     * The file access mode to use when opening a file.
     */
    public static final Option<FileAccess> FILE_ACCESS = Option.simple(Options.class, "FILE_ACCESS", FileAccess.class);

    /**
     * A flag which indicates that opened files should be appended to.  Some platforms do not support both append and
     * {@link FileAccess#READ_WRITE} at the same time.
     */
    public static final Option<Boolean> FILE_APPEND = Option.simple(Options.class, "FILE_APPEND", Boolean.class);

    /**
     * A flag which indicates that a file should be created if it does not exist ({@code true} by default for writing files,
     * {@code false} by default for reading files).
     */
    public static final Option<Boolean> FILE_CREATE = Option.simple(Options.class, "FILE_CREATE", Boolean.class);

    /**
     * The stack size (in bytes) to attempt to use for worker threads.
     */
    public static final Option<Long> STACK_SIZE = Option.simple(Options.class, "STACK_SIZE", Long.class);

    /**
     * The name to use for a newly created worker.  If not specified, the string "XNIO" will be used.  The worker name
     * is used as a part of the thread name for created threads, and for any management constructs.
     */
    public static final Option<String> WORKER_NAME = Option.simple(Options.class, "WORKER_NAME", String.class);

    /**
     * The thread priority for newly created worker threads.  If not specified, the platform default value will be used.
     */
    public static final Option<Integer> THREAD_PRIORITY = Option.simple(Options.class, "THREAD_PRIORITY", Integer.class);

    /**
     * Specify whether worker threads should be daemon threads.  Defaults to {@code false}.
     */
    public static final Option<Boolean> THREAD_DAEMON = Option.simple(Options.class, "THREAD_DAEMON", Boolean.class);

    /**
     * Specify the number of I/O threads to create for the worker.  If not specified, a default will be chosen.
     */
    public static final Option<Integer> WORKER_IO_THREADS = Option.simple(Options.class, "WORKER_IO_THREADS", Integer.class);

    /**
     * Specify the number of I/O threads to devote to reading for split thread channels.  If not specified, a default will be chosen to be
     * roughly half of the worker I/O threads, or the number of threads not specified for writing.
     */
    public static final Option<Integer> WORKER_READ_THREADS = Option.simple(Options.class, "WORKER_READ_THREADS", Integer.class);

    /**
     * Specify the number of I/O threads to devote to writing for split thread channels.  If not specified, a default will be chosen to be
     * roughly half of the worker I/O threads, or the number of threads not specified for reading.
     */
    public static final Option<Integer> WORKER_WRITE_THREADS = Option.simple(Options.class, "WORKER_WRITE_THREADS", Integer.class);

    /**
     * Specify whether read and write operations should be split among separate threads.  If {@code true}, then each
     * directional channel may be accessed concurrently with respect to one another, and will have different worker threads assigned;
     * otherwise, both directions will share a worker thread and must not be accessed independently.
     */
    public static final Option<Boolean> SPLIT_READ_WRITE_THREADS = Option.simple(Options.class, "SPLIT_READ_WRITE_THREADS", Boolean.class);

    /**
     * Specify whether a server, acceptor, or connector should be attached to write threads.  By default, the establishing
     * phase of connections are attached to read threads.  Use this option if the client or server writes a message
     * directly upon connect and a split threads configuration is used.
     */
    public static final Option<Boolean> WORKER_ESTABLISH_WRITING = Option.simple(Options.class, "WORKER_ESTABLISH_WRITING", Boolean.class);

    /**
     * Specify the number of accept threads a single socket server should have.  Specifying more than one can result in spurious wakeups
     * for a socket server under low connection volume, but higher throughput at high connection volume.  The minimum value
     * is 1, and the maximum value is equal to the number of available worker threads.
     *
     * @deprecated This option is now ignored.  All I/O threads are used for accept, except in the case of split read/write
     * threads, in which case only read or write threads will be used as determined by {@link #WORKER_ESTABLISH_WRITING}.
     */
    @Deprecated
    public static final Option<Integer> WORKER_ACCEPT_THREADS = Option.simple(Options.class, "WORKER_ACCEPT_THREADS", Integer.class);

    /**
     * Specify the number of "core" threads for the worker task thread pool.
     */
    public static final Option<Integer> WORKER_TASK_CORE_THREADS = Option.simple(Options.class, "WORKER_TASK_CORE_THREADS", Integer.class);

    /**
     * Specify the maximum number of threads for the worker task thread pool.
     */
    public static final Option<Integer> WORKER_TASK_MAX_THREADS = Option.simple(Options.class, "WORKER_TASK_MAX_THREADS", Integer.class);

    /**
     * Specify the number of milliseconds to keep non-core task threads alive.
     */
    public static final Option<Integer> WORKER_TASK_KEEPALIVE = Option.simple(Options.class, "WORKER_TASK_KEEPALIVE", Integer.class);

    /**
     * Specify the maximum number of worker tasks to allow before rejecting.
     */
    public static final Option<Integer> WORKER_TASK_LIMIT = Option.simple(Options.class, "WORKER_TASK_LIMIT", Integer.class);

    /**
     * Specify that output should be buffered.  The exact behavior of the buffering is not specified; it may flush based
     * on buffered size or time.  An explicit {@link SuspendableWriteChannel#flush()} will still cause
     * the channel to flush its contents immediately.
     */
    public static final Option<Boolean> CORK = Option.simple(Options.class, "CORK", Boolean.class);

    /**
     * The high water mark for a server's connections.  Once this number of connections have been accepted, accepts
     * will be suspended for that server.
     */
    public static final Option<Integer> CONNECTION_HIGH_WATER = Option.simple(Options.class, "CONNECTION_HIGH_WATER", Integer.class);

    /**
     * The low water mark for a server's connections.  Once the number of active connections have dropped below this
     * number, accepts can be resumed for that server.
     */
    public static final Option<Integer> CONNECTION_LOW_WATER = Option.simple(Options.class, "CONNECTION_LOW_WATER", Integer.class);

    /**
     * The compression level to apply for compressing streams and channels.
     */
    public static final Option<Integer> COMPRESSION_LEVEL = Option.simple(Options.class, "COMPRESSION_LEVEL", Integer.class);

    /**
     * The compression type to apply for compressing streams and channels.
     */
    public static final Option<CompressionType> COMPRESSION_TYPE = Option.simple(Options.class, "COMPRESSION_TYPE", CompressionType.class);

    /**
     * The number of balancing tokens, if connection-balancing is enabled.  Must be less than the number of I/O threads,
     * or 0 to disable balancing and just accept opportunistically.
     */
    public static final Option<Integer> BALANCING_TOKENS = Option.simple(Options.class, "BALANCING_TOKENS", Integer.class);

    /**
     * The number of connections to create per connection-balancing token, if connection-balancing is enabled.
     */
    public static final Option<Integer> BALANCING_CONNECTIONS = Option.simple(Options.class, "BALANCING_CONNECTIONS", Integer.class);

    /**
     * The poll interval for poll based file system watchers.  Defaults to 5000ms.  Ignored on Java 7 and later.
     */
    public static final Option<Integer> WATCHER_POLL_INTERVAL = Option.simple(Options.class, "WATCHER_POLL_INTERVAL", Integer.class);
}
