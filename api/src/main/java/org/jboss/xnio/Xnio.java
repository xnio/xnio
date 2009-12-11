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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Properties;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.net.InetSocketAddress;
import org.jboss.xnio.channels.ConnectedStreamChannel;
import org.jboss.xnio.channels.DatagramChannel;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.channels.StreamSinkChannel;
import org.jboss.xnio.channels.StreamSourceChannel;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.channels.UdpChannel;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.management.OneWayPipeConnectionMBean;
import org.jboss.xnio.management.PipeConnectionMBean;
import org.jboss.xnio.management.PipeServerMBean;
import org.jboss.xnio.management.PipeSinkServerMBean;
import org.jboss.xnio.management.PipeSourceServerMBean;
import org.jboss.xnio.management.TcpConnectionMBean;
import org.jboss.xnio.management.TcpServerMBean;
import org.jboss.xnio.management.UdpServerMBean;

import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.RuntimeOperationsException;
import javax.net.SocketFactory;
import javax.net.ServerSocketFactory;

/**
 * The XNIO entry point class.
 *
 * @apiviz.landmark
 */
public abstract class Xnio implements Closeable {

    private static final Logger mlog = Logger.getLogger("org.jboss.xnio.management");

    static {
        Logger.getLogger("org.jboss.xnio").info("XNIO Version " + Version.VERSION);
    }

    private static final String NIO_IMPL_PROVIDER = "nio";
    private static final String PROVIDER_NAME;
    private static final String MANAGEMENT_DOMAIN = "jboss.xnio";
    private static final String PROPERTIES = "xnio.properties";

    private static final AtomicLong mbeanSequence = new AtomicLong();

    private static String AGENTID_PROPNAME = "xnio.agentid";
    private static String PROVIDER_PROPNAME = "xnio.provider.name";
    private static String PROPERTY_FILE_PROPNAME = "xnio.property.file";

    private static final PrivilegedAction<String> GET_PROVIDER_ACTION = new GetPropertyAction(PROVIDER_PROPNAME, NIO_IMPL_PROVIDER);
    private static final PrivilegedAction<String> GET_AGENTID_ACTION = new GetPropertyAction(AGENTID_PROPNAME, null);

    private static final Permission SUBCLASS_PERMISSION = new RuntimePermission("xnioProvider");

    static {
        String providerClassName = NIO_IMPL_PROVIDER;
        try {
            providerClassName = AccessController.doPrivileged(GET_PROVIDER_ACTION);
        } catch (Throwable t) {
            // ignored
        }
        PROVIDER_NAME = providerClassName;
    }

    private final List<MBeanServer> mBeanServers = new ArrayList<MBeanServer>();

    private final String name;
    private final Executor executor;

    /**
     * Get the default handler executor.
     *
     * @return the executor
     */
    protected Executor getExecutor() {
        return executor;
    }

    private static final Map<String, Xnio> instanceMap = new HashMap<String, Xnio>();

    /**
     * Get or create a named XNIO provider instance, which is automatically configured from a properties file.
     * <p>
     * The boot classpath is searched for a file named {@code "xnio.properties"} (the name can be overridden by way of
     * the {@code "xnio.property.file"} system property).  This file contains properties which are used to configure
     * all created providers.
     * <p>
     * The following properties are recognized:
     * <ul>
     * <li><code><i>&lt;name&gt;</i>.handler.threadpool</code> - a boolean value which specifies whether channel listeners should be invoked via
     *      a thread pool executor.  A value of {@code true} indicates that a thread pool should be created; a value of
     *      {@code false} (the default) indicates that the listeners should be invoked from the current thread.</li>
     * <li><code><i>&lt;name&gt;</i>.handler.threadpool.coresize</code> - an integer value which specifies the core size of the thread pool.
     *      The default value is 8 threads.</li>
     * <li><code><i>&lt;name&gt;</i>.handler.threadpool.maxsize</code> - an integer value which specifies the maximum size of the thread pool.
     *      The default value is 64 threads.</li>
     * <li><code><i>&lt;name&gt;</i>.handler.threadpool.keepaliveseconds</code> - an integer value which specifies the number of seconds an idle
     *      thread should be kept alive before exiting.  The default value is 30 seconds.</li>
     * <li><code><i>&lt;name&gt;</i>.handler.threadpool.queuelength</code> - an integer value which specifies the length of the task queue for the
     *      listener thread pool.  The default value is 64.</li>
     * <li><code><i>&lt;name&gt;</i>.provider.option.<i>&lt;option-name&gt;</i></code> - An option to add to the XNIO provider's option map.  The
     *      value is the value for the option.</li>
     * <li><code><i>&lt;name&gt;</i>.provider</code> - the provider implementation to use.  If not specified, a default provider will be located and
     *      used.</li>
     * </ul>
     *
     * @param name the provider name to get
     * @return the configured global XNIO instance
     * @throws IOException if the XNIO provider could not be created
     */
    public static Xnio getInstance(final String name) throws IOException {
        synchronized (instanceMap) {
            final Xnio instance = instanceMap.get(name);
            if (instance != null) {
                return instance;
            }
            final Xnio newInstance = createConfigured(name);
            instanceMap.put(name, newInstance);
            return newInstance;
        }
    }

    /**
     * Get the {@code "default"} instance.  Equivalent to calling {@link #getInstance(String) getInstance("default")}.
     *
     * @return the default instance
     * @throws IOException if the XNIO provider could not be created
     */
    public static Xnio getInstance() throws IOException {
        return getInstance("default");
    }

    private static Xnio createConfigured(final String name) throws IOException {
        try {
            return AccessController.doPrivileged(new PrivilegedAction<Xnio>() {
                public Xnio run() {
                    final String fileName = System.getProperty(PROPERTY_FILE_PROPNAME, PROPERTIES);
                    final Properties props = new Properties();
                    try {
                        final InputStream stream = getClass().getResourceAsStream(fileName);
                        if (stream != null) try {
                            final InputStreamReader reader = new InputStreamReader(stream, "utf-8");
                            try {
                                props.load(reader);
                                reader.close();
                            } finally {
                                IoUtils.safeClose(reader);
                            }
                        } finally {
                            IoUtils.safeClose(stream);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    final XnioConfiguration conf = new XnioConfiguration();
                    if (Boolean.parseBoolean(props.getProperty(name + ".handler.threadpool", "false"))) {
                        conf.setExecutor(
                                new ThreadPoolExecutor(
                                        Integer.parseInt(props.getProperty(name + ".handler.threadpool.coresize", "8")),
                                        Integer.parseInt(props.getProperty(name + ".handler.threadpool.maxsize", "64")),
                                        Long.parseLong(props.getProperty(name + ".handler.threadpool.keepaliveseconds", "30")),
                                        TimeUnit.SECONDS,
                                        new ArrayBlockingQueue<Runnable>(Integer.parseInt(props.getProperty(name + ".handler.threadpool.queuelength", "64"))),
                                        new ThreadPoolExecutor.CallerRunsPolicy()
                                )
                        );
                    }
                    conf.setName(name);
                    conf.setOptionMap(OptionMap.builder().parseAll(props, name + ".provider.option.").getMap());
                    try {
                        return Xnio.create(props.getProperty(name + ".provider", PROVIDER_NAME), conf);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (RuntimeException e) {
            final Throwable c = e.getCause();
            if (c instanceof IOException) {
                throw (IOException)c;
            }
            throw e;
        }
    }

    /**
     * Create an instance of the default XNIO provider.  The provider name can be specified through the
     * {@code xnio.provider.name} system property.  Any failure to create the XNIO provider will cause an {@code java.io.IOException}
     * to be thrown.
     *
     * @return an XNIO instance
     * @throws IOException if the XNIO provider could not be created
     */
    public static Xnio create() throws IOException {
        return create(PROVIDER_NAME, new XnioConfiguration());
    }

    /**
     * Create an instance of the default XNIO provider.  The provider name can be specified through the
     * {@code xnio.provider.name} system property.  Any failure to create the XNIO provider will cause an {@code java.io.IOException}
     * to be thrown.
     *
     * @param configuration the configuration parameters for the implementation
     * @return an XNIO instance
     * @throws IOException if the XNIO provider could not be created
     */
    public static Xnio create(XnioConfiguration configuration) throws IOException {
        return create(PROVIDER_NAME, configuration);
    }

    /**
     * Create an instance of the named XNIO provider.  Any failure to create the XNIO provider will cause an {@code java.io.IOException}
     * to be thrown.
     *
     * @param implName the name of the implementation
     * @param configuration the configuration parameters for the implementation
     * @return an XNIO instance
     * @throws IOException if the XNIO provider could not be created
     */
    public static Xnio create(String implName, XnioConfiguration configuration) throws IOException {
        for (XnioProvider xnioProvider : ServiceLoader.load(XnioProvider.class)) {
            if (implName.equals(xnioProvider.getName())) {
                return xnioProvider.getNewInstance(configuration);
            }
        }
        throw new IOException("No XNIO provider named \"" + implName + "\" could be found");
    }

    private static final AtomicInteger xnioSequence = new AtomicInteger(1);

    /**
     * Construct an XNIO provider instance.
     */
    protected Xnio(XnioConfiguration configuration) {
        if (configuration == null) {
            throw new NullPointerException("configuration is null");
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SUBCLASS_PERMISSION);
        }
        final String name = configuration.getName();
        final int seq = xnioSequence.getAndIncrement();
        this.name = name != null ? name : String.format("%s-%d", getClass().getName(), Integer.valueOf(seq));
        final Executor executor = configuration.getExecutor();
        this.executor = executor != null ? executor : IoUtils.directExecutor();
        // Perform MBeanServer autodetection...
        final List<MBeanServer> servers = mBeanServers;
        synchronized (servers) {
            final List<MBeanServer> confServers = configuration.getMBeanServers();
            if (confServers != null) {
                for (MBeanServer server : confServers) {
                    if (server == null) {
                        throw new NullPointerException("server in MBeanServer configuration list is null");
                    }
                    mlog.debug("Registered configured MBeanServer %s", server);
                    servers.add(server);
                }
            } else {
                final String agentidpropval;
                try {
                    agentidpropval = sm != null ? AccessController.doPrivileged(GET_AGENTID_ACTION) : System.getProperty(AGENTID_PROPNAME);
                } catch (SecurityException e) {
                    // not allowed; leave mbean servers empty
                    mlog.debug("Unable to read agentid property (%s); JMX features disabled", e);
                    return;
                }
                if (agentidpropval == null || agentidpropval.length() == 0) {
                    final Collection<? extends MBeanServer> fullList;
                    try {
                        fullList = sm != null ? AccessController.doPrivileged(new GetMBeanServersAction(null)) : MBeanServerFactory.findMBeanServer(null);
                    } catch (SecurityException e) {
                        mlog.debug("Unable to detect installed mbean servers (%s); JMX features disabled", e);
                        return;
                    }
                    for (MBeanServer match : fullList) {
                        mlog.debug("Registered MBeanServer %s", match);
                        servers.add(match);
                    }
                } else {
                    String[] agentids = agentidpropval.split(",");
                    for (String agentid : agentids) {
                        String properName = agentid.trim();
                        if (properName.length() == 0) {
                            continue;
                        }
                        Collection<? extends MBeanServer> matches;
                        try {
                            matches = sm != null ? AccessController.doPrivileged(new GetMBeanServersAction(properName)) : MBeanServerFactory.findMBeanServer(null);
                        } catch (SecurityException e) {
                            mlog.debug("Unable to locate any MBeanServer for ID \"%s\" (%s); skipping", properName, e);
                            continue;
                        }
                        if (matches == null) {
                            mlog.debug("Unable to locate any MBeanServer for ID \"%s\" (no matches); skipping", properName);
                        } else {
                            for (MBeanServer match : matches) {
                                mlog.debug("Registered MBeanServer %s for ID \"%s\"", match, properName);
                                servers.add(match);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Create a managed socket factory which uses this provider's MBean configuration to track management information.
     *
     * @param optionMap the option map
     * @return the managed socket factory
     *
     * @since 2.0
     */
    public SocketFactory createManagedSocketFactory(OptionMap optionMap) {
        return new ManagedSocketFactory(this, optionMap);
    }

    /**
     * Create a managed server socket factory which uses this provider's MBean configuration to track management information.
     *
     * @param optionMap the option map
     * @return the managed server socket factory
     *
     * @since 2.0
     */
    public ServerSocketFactory createServerSocketFactory(OptionMap optionMap) {
        return new ManagedServerSocketFactory(this, optionMap);
    }

    /**
     * Create an unbound TCP server.  The given executor will be used to execute handler methods.
     *
     * @param executor the executor to use to execute the handlers
     * @param openHandler the initial open-connection handler
     * @param optionMap the initial configuration for the server
     * @return the unbound TCP server
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public TcpServer createTcpServer(Executor executor, ChannelListener<? super TcpChannel> openHandler, OptionMap optionMap) {
        throw new UnsupportedOperationException("TCP Server");
    }

    /**
     * Create an unbound TCP server.  The provider's default executor will be used to execute handler methods.
     *
     * @param openHandler the initial open-connection handler
     * @param optionMap the initial configuration for the server
     * @return the unbound TCP server
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public TcpServer createTcpServer(ChannelListener<? super TcpChannel> openHandler, OptionMap optionMap) {
        return createTcpServer(executor, openHandler, optionMap);
    }

    /**
     * Create a TCP connector.  The given executor will be used to execute handler methods.
     *
     * @param executor the executor to use to execute the handlers
     * @param optionMap the initial configuration for the connector
     * @return the TCP connector
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public TcpConnector createTcpConnector(Executor executor, OptionMap optionMap) {
        return createTcpConnector(executor, null, optionMap);
    }

    /**
     * Create a TCP connector.  The provider's default executor will be used to execute handler methods.
     *
     * @param optionMap the initial configuration for the connector
     * @return the TCP connector
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public TcpConnector createTcpConnector(OptionMap optionMap) {
        return createTcpConnector(executor, optionMap);
    }

    /**
     * Create a TCP connector.  The given executor will be used to execute handler methods.
     *
     * @param executor the executor to use to execute the handlers
     * @param src the source address for connections
     * @param optionMap the initial configuration for the connector
     * @return the TCP connector
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public TcpConnector createTcpConnector(Executor executor, InetSocketAddress src, OptionMap optionMap) {
        throw new UnsupportedOperationException("TCP Connector");
    }

    /**
     * Create a TCP connector.  The provider's default executor will be used to execute handler methods.
     *
     * @param src the source address for connections
     * @param optionMap the initial configuration for the connector
     * @return the TCP connector
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public TcpConnector createTcpConnector(InetSocketAddress src, OptionMap optionMap) {
        return createTcpConnector(executor, src, optionMap);
    }

    /**
     * Create an unbound UDP server.  The UDP server can be configured to be multicast-capable; this should only be
     * done if multicast is needed, since some providers have a performance penalty associated with multicast.
     * The given executor will be used to execute handler methods.
     *
     * @param executor the executor to use to execute the handlers
     * @param openHandler the initial open-connection handler
     * @param optionMap the initial configuration for the server
     *
     * @return a factory that can be used to configure the new UDP server
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public UdpServer createUdpServer(Executor executor, ChannelListener<? super UdpChannel> openHandler, OptionMap optionMap) {
        throw new UnsupportedOperationException("UDP Server");
    }

    /**
     * Create an unbound UDP server.  The UDP server can be configured to be multicast-capable; this should only be
     * done if multicast is needed, since some providers have a performance penalty associated with multicast.
     * The provider's default executor will be used to execute handler methods.
     *
     * @param openHandler the initial open-connection handler
     * @param optionMap the initial configuration for the server
     *
     * @return a factory that can be used to configure the new UDP server
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public UdpServer createUdpServer(ChannelListener<? super UdpChannel> openHandler, OptionMap optionMap) {
        return createUdpServer(executor, openHandler, optionMap);
    }

    /**
     * Create an unbound UDP server.  The UDP server can be configured to be multicast-capable; this should only be
     * done if multicast is needed, since some providers have a performance penalty associated with multicast.
     * The provider's default executor will be used to execute handler methods.
     *
     * @param optionMap the initial configuration for the server
     *
     * @return a factory that can be used to configure the new UDP server
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public UdpServer createUdpServer(OptionMap optionMap) {
        return createUdpServer(executor, IoUtils.nullChannelListener(), optionMap);
    }

    /**
     * Create a pipe "server".  The provided handler factory is used to supply handlers for the server "end" of the
     * pipe. The returned channel source is used to establish connections to the server.
     *
     * @param executor the executor to use to execute the handlers
     * @param openHandler the initial open-connection handler
     *
     * @return the client channel source
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public ChannelSource<? extends StreamChannel> createPipeServer(Executor executor, ChannelListener<? super StreamChannel> openHandler) {
        throw new UnsupportedOperationException("Pipe Server");
    }

    /**
     * Create a pipe "server".  The provided handler factory is used to supply handlers for the server "end" of the
     * pipe. The returned channel source is used to establish connections to the server.  The provider's default executor will be used to
     * execute handler methods.
     *
     * @param openHandler the initial open-connection handler
     *
     * @return the client channel source
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public ChannelSource<? extends StreamChannel> createPipeServer(ChannelListener<? super StreamChannel> openHandler) {
        return createPipeServer(executor, openHandler);
    }

    /**
     * Create a one-way pipe "server".  The provided handler factory is used to supply handlers for the server "end" of
     * the pipe. The returned channel source is used to establish connections to the server.  The data flows from the
     * server to the client.
     *
     * @param executor the executor to use to execute the handlers
     * @param openHandler the initial open-connection handler
     *
     * @return the client channel source
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public ChannelSource<? extends StreamSourceChannel> createPipeSourceServer(Executor executor, ChannelListener<? super StreamSinkChannel> openHandler) {
        throw new UnsupportedOperationException("One-way Pipe Server");
    }

    /**
     * Create a one-way pipe "server".  The provided handler factory is used to supply handlers for the server "end" of
     * the pipe. The returned channel source is used to establish connections to the server.  The data flows from the
     * server to the client.  The provider's default executor will be used to
     * execute handler methods.
     *
     * @param openHandler the initial open-connection handler
     *
     * @return the client channel source
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public ChannelSource<? extends StreamSourceChannel> createPipeSourceServer(ChannelListener<? super StreamSinkChannel> openHandler) {
        return createPipeSourceServer(executor, openHandler);
    }

    /**
     * Create a one-way pipe "server".  The provided handler factory is used to supply handlers for the server "end" of
     * the pipe. The returned channel source is used to establish connections to the server.  The data flows from the
     * client to the server.
     *
     * @param executor the executor to use to execute the handlers
     * @param openHandler the initial open-connection handler
     *
     * @return the client channel source
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public ChannelSource<? extends StreamSinkChannel> createPipeSinkServer(Executor executor, ChannelListener<? super StreamSourceChannel> openHandler) {
        throw new UnsupportedOperationException("One-way Pipe Server");
    }

    /**
     * Create a one-way pipe "server".  The provided handler factory is used to supply handlers for the server "end" of
     * the pipe. The returned channel source is used to establish connections to the server.  The data flows from the
     * client to the server.  The provider's default executor will be used to
     * execute handler methods.
     *
     * @param openHandler the initial open-connection handler
     *
     * @return the client channel source
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public ChannelSource<? extends StreamSinkChannel> createPipeSinkServer(ChannelListener<? super StreamSourceChannel> openHandler) {
        return createPipeSinkServer(executor, openHandler);
    }

    /**
     * Create a single pipe connection.
     *
     * @param executor the executor to use to execute the handlers
     * @param leftHandler the open handler for the "left" side of the pipe
     * @param rightHandler the open handler for the "right" side of the pipe
     *
     * @return the future connection
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public IoFuture<? extends Closeable> createPipeConnection(Executor executor, ChannelListener<? super StreamChannel> leftHandler, ChannelListener<? super StreamChannel> rightHandler) {
        throw new UnsupportedOperationException("Pipe Connection");
    }

    /**
     * Create a single pipe connection.  The provider's default executor will be used to
     * execute handler methods.
     *
     * @param leftHandler the handler for the "left" side of the pipe
     * @param rightHandler the handler for the "right" side of the pipe
     *
     * @return the future connection
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public IoFuture<? extends Closeable> createPipeConnection(ChannelListener<? super StreamChannel> leftHandler, ChannelListener<? super StreamChannel> rightHandler) {
        return createPipeConnection(executor, leftHandler, rightHandler);
    }

    /**
     * Create a single one-way pipe connection.
     *
     * @param executor the executor to use to execute the handlers
     * @param sourceHandler the handler for the "source" side of the pipe
     * @param sinkHandler the handler for the "sink" side of the pipe
     *
     * @return the future connection
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public IoFuture<? extends Closeable> createOneWayPipeConnection(Executor executor, ChannelListener<? super StreamSourceChannel> sourceHandler, ChannelListener<? super StreamSinkChannel> sinkHandler) {
        throw new UnsupportedOperationException("One-way Pipe Connection");
    }

    /**
     * Create a single one-way pipe connection.  The provider's default executor will be used to
     * execute handler methods.
     *
     * @param sourceHandler the handler for the "source" side of the pipe
     * @param sinkHandler the handler for the "sink" side of the pipe
     *
     * @return the future connection
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public IoFuture<? extends Closeable> createOneWayPipeConnection(ChannelListener<? super StreamSourceChannel> sourceHandler, ChannelListener<? super StreamSinkChannel> sinkHandler) {
        return createOneWayPipeConnection(executor, sourceHandler, sinkHandler);
    }

    /**
     * Create a TCP acceptor.
     *
     * @param executor the executor to use to execute the handlers
     * @param optionMap the initial configuration for the acceptor
     * @return the TCP acceptor
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public TcpAcceptor createTcpAcceptor(Executor executor, OptionMap optionMap) {
        throw new UnsupportedOperationException("TCP Acceptor");
    }

    /**
     * Create a TCP acceptor.
     *
     * @param optionMap the initial configuration for the acceptor
     * @return the TCP acceptor
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public TcpAcceptor createTcpAcceptor(OptionMap optionMap) {
        return createTcpAcceptor(executor, optionMap);
    }

    /**
     * Create a local stream server.  The stream server can be bound to one or more files in the filesystem.
     *
     * @param executor the executor to use to execute the handlers
     * @param openListener a listener which is notified on channel open
     * @param optionMap the initial configuration for the server
     *
     * @return a factory that can be used to configure the new stream server
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public LocalServer createLocalStreamServer(Executor executor, ChannelListener<? super ConnectedStreamChannel<String>> openListener, OptionMap optionMap) {
        throw new UnsupportedOperationException("Local IPC Stream Server");
    }

    /**
     * Create a local stream server.  The stream server can be bound to one or more files in the filesystem.
     *
     * @param openListener a listener which is notified on channel open
     * @param optionMap the initial configuration for the server
     *
     * @return a factory that can be used to configure the new stream server
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public LocalServer createLocalStreamServer(ChannelListener<? super ConnectedStreamChannel<String>> openListener, OptionMap optionMap) {
        return createLocalStreamServer(executor, openListener, optionMap);
    }

    /**
     * Create a local stream connector.
     *
     * @param executor the executor to use to execute the handlers
     * @param optionMap the initial configuration for the connector
     *
     * @return the stream connector
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public LocalStreamConnector createLocalStreamConnector(Executor executor, OptionMap optionMap) {
        throw new UnsupportedOperationException("Local IPC Stream Connector");
    }

    /**
     * Create a local stream connector.
     *
     * @param optionMap the initial configuration for the connector
     *
     * @return the stream connector
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public LocalStreamConnector createLocalStreamConnector(OptionMap optionMap) {
        return createLocalStreamConnector(executor, optionMap);
    }

    /**
     * Create a local datagram server.  The datagram server is bound to one or more files in the filesystem.
     *
     * @param executor the executor to use to execute the handlers
     * @param openHandler the initial open-connection handler
     * @param optionMap the initial configuration for the server
     *
     * @return the new datagram server
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public LocalServer createLocalDatagramServer(Executor executor, ChannelListener<? super DatagramChannel<String>> openHandler, OptionMap optionMap) {
        throw new UnsupportedOperationException("Local IPC Datagram Server");
    }

    /**
     * Create a local datagram server.  The datagram server is bound to one or more files in the filesystem.
     *
     * @param openHandler the initial open-connection handler
     * @param optionMap the initial configuration for the server
     *
     * @return the new datagram server
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public LocalServer createLocalDatagramServer(ChannelListener<? super DatagramChannel<String>> openHandler, OptionMap optionMap) {
        return createLocalDatagramServer(executor, openHandler, optionMap);
    }

    /**
     * Create a local datagram connector.
     *
     * @param executor the executor to use to execute the handlers
     * @param optionMap the initial configuration for the connector
     * @return the new datagram connector
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public LocalDatagramConnector createLocalDatagramConnector(Executor executor, OptionMap optionMap) {
        throw new UnsupportedOperationException("Local IPC Datagram Connector");
    }

    /**
     * Create a local datagram connector.
     *
     * @param optionMap the initial configuration for the connector
     * @return the new datagram connector
     *
     * @since 2.0
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public LocalDatagramConnector createLocalDatagramConnector(OptionMap optionMap) {
        return createLocalDatagramConnector(executor, optionMap);
    }

    /**
     * Wake up any blocking I/O operation being carried out on a given thread.  Custom implementors of {@link Thread}
     * may call this method from their implementation of {@link Thread#interrupt()} after the default implementation
     * to ensure that any thread waiting in a blocking operation is woken up in a timely manner.  Some implementations
     * may not implement this method, relying instead on the interruption mechanism built in to the JVM; as such this
     * method should not be relied upon as a guaranteed way to awaken a blocking thread independently of thread
     * interruption.
     *
     * @param targetThread the thread to awaken
     *
     * @since 1.2
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    public void awaken(Thread targetThread) {
        // nothing by default
    }

    /**
     * Get the name of this XNIO instance.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Get a string representation of this XNIO instance.
     *
     * @return the string representation
     */
    public String toString() {
        return String.format("XNIO provider \"%s\" <%s@%s>", getName(), getClass().getName(), Integer.toHexString(hashCode()));
    }

    /**
     * Close this XNIO provider.  Calling this method more than one time has no additional effect.
     */
    public abstract void close() throws IOException;

    private Closeable registerMBean(final Object mBean, final ObjectName mBeanName) {
        final SecurityManager sm = System.getSecurityManager();
        final List<MBeanServer> servers = mBeanServers;
        synchronized (servers) {
            final Iterator<MBeanServer> it = servers.iterator();
            if (!it.hasNext()) {
                return IoUtils.nullCloseable();
            } else {
                final List<Registration> registrations = new ArrayList<Registration>(servers.size());
                do {
                    final MBeanServer server = it.next();
                    if (sm != null) {
                        AccessController.doPrivileged(new PrivilegedAction<Void>() {
                            public Void run() {
                                doRegister(mBean, mBeanName, registrations, server);
                                return null;
                            }
                        });
                    } else {
                        doRegister(mBean, mBeanName, registrations, server);
                    }
                } while (it.hasNext());
                return new RegHandle(registrations);
            }
        }
    }

    private static void doRegister(final Object mBean, final ObjectName mBeanName, final List<Registration> registrations, final MBeanServer server) {
        try {
            final ObjectInstance instance = server.registerMBean(mBean, mBeanName);
            registrations.add(new Registration(server, instance.getObjectName()));
        } catch (JMException e) {
            mlog.debug(e, "Failed to register mBean named \"%s\" on server %s", mBeanName, server);
        } catch (RuntimeOperationsException e) {
            mlog.debug(e, "Failed to register mBean named \"%s\" on server %s", mBeanName, server);
        }
    }

    private interface Entry extends Map.Entry<String, String> {}

    private static Entry entry(final String k, final String v) {
        return new Entry() {
            public String getKey() {
                return k;
            }

            public String getValue() {
                return v;
            }

            public String setValue(final String value) {
                throw new UnsupportedOperationException("setValue");
            }
        };
    }

    private static Hashtable<String, String> hashtable(Entry... entries) {
        final Hashtable<String, String> table = new Hashtable<String, String>(entries.length);
        for (Entry entry : entries) {
            table.put(entry.getKey(), entry.getValue());
        }
        return table;
    }

    /**
     * Get an XNIO property.  The property name must start with {@code "xnio."}.
     *
     * @param name the property name
     * @return the property value, or {@code null} if it wasn't found
     * @since 1.2
     */
    protected String getProperty(final String name) {
        if (! name.startsWith("xnio.")) {
            throw new SecurityException("Not allowed to read non-XNIO properties");
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            return AccessController.doPrivileged(new GetPropertyAction(name, null));
        } else {
            return System.getProperty(name);
        }
    }

    /**
     * Get an XNIO property.  The property name must start with {@code "xnio."}.
     *
     * @param name the property name
     * @param defaultValue the default value
     * @return the property value, or {@code defaultValue} if it wasn't found
     * @since 1.2
     */
    protected String getProperty(final String name, final String defaultValue) {
        if (! name.startsWith("xnio.")) {
            throw new SecurityException("Not allowed to read non-XNIO properties");
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            return AccessController.doPrivileged(new GetPropertyAction(name, defaultValue));
        } else {
            return System.getProperty(name, defaultValue);
        }
    }

    /**
     * Register a TCP server MBean.
     *
     * @param mBean the MBean
     * @return a handle which may be used to unregister the MBean
     * @since 1.2
     */
    protected Closeable registerMBean(final TcpServerMBean mBean) {
        try {
            final ObjectName mbeanName = new ObjectName(MANAGEMENT_DOMAIN, hashtable(
                    entry("provider", ObjectName.quote(getName())),
                    entry("type", "server"),
                    entry("protocol", "tcp"),
                    entry("id", Long.toString(mbeanSequence.getAndIncrement()))
                    /* TODO: name? */
            ));
            return registerMBean(mBean, mbeanName);
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    /**
     * Register a TCP connection MBean.
     *
     * @param mBean the MBean
     * @return a handle which may be used to unregister the MBean
     * @since 1.2
     */
    protected Closeable registerMBean(final TcpConnectionMBean mBean) {
        try {
            final ObjectName mbeanName = new ObjectName(MANAGEMENT_DOMAIN, hashtable(
                    entry("provider", ObjectName.quote(getName())),
                    entry("type", "connection"),
                    entry("protocol", "tcp"),
                    entry("bindAddress", ObjectName.quote(mBean.getBindAddress().toString())),
                    entry("peerAddress", ObjectName.quote(mBean.getPeerAddress().toString())),
                    entry("id", Long.toString(mbeanSequence.getAndIncrement()))
            ));
            return registerMBean(mBean, mbeanName);
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    /**
     * Register a UDP server MBean.
     *
     * @param mBean the MBean
     * @return a handle which may be used to unregister the MBean
     * @since 1.2
     */
    protected Closeable registerMBean(final UdpServerMBean mBean) {
        try {
            final ObjectName mbeanName = new ObjectName(MANAGEMENT_DOMAIN, hashtable(
                    entry("provider", ObjectName.quote(getName())),
                    entry("type", "server"),
                    entry("protocol", "udp"),
                    entry("id", Long.toString(mbeanSequence.getAndIncrement()))
            ));
            return registerMBean(mBean, mbeanName);
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    /**
     * Register a one-way pipe connection MBean.
     *
     * @param mBean the MBean
     * @return a handle which may be used to unregister the MBean
     * @since 1.2
     */
    protected Closeable registerMBean(final OneWayPipeConnectionMBean mBean) {
        try {
            final ObjectName mbeanName = new ObjectName(MANAGEMENT_DOMAIN, hashtable(
                    entry("provider", ObjectName.quote(getName())),
                    entry("type", "connection"),
                    entry("protocol", "local"),
                    entry("id", Long.toString(mbeanSequence.getAndIncrement()))
            ));
            return registerMBean(mBean, mbeanName);
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    /**
     * Register a pipe connection MBean.
     *
     * @param mBean the MBean
     * @return a handle which may be used to unregister the MBean
     * @since 1.2
     */
    protected Closeable registerMBean(final PipeConnectionMBean mBean) {
        try {
            final ObjectName mbeanName = new ObjectName(MANAGEMENT_DOMAIN, hashtable(
                    entry("provider", ObjectName.quote(getName())),
                    entry("type", "connection"),
                    entry("protocol", "local"),
                    entry("id", Long.toString(mbeanSequence.getAndIncrement()))
            ));
            return registerMBean(mBean, mbeanName);
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    /**
     * Register a pipe server MBean.
     *
     * @param mBean the MBean
     * @return a handle which may be used to unregister the MBean
     * @since 1.2
     */
    protected Closeable registerMBean(final PipeServerMBean mBean) {
        try {
            final ObjectName mbeanName = new ObjectName(MANAGEMENT_DOMAIN, hashtable(
                    entry("provider", ObjectName.quote(getName())),
                    entry("type", "server"),
                    entry("protocol", "local"),
                    entry("id", Long.toString(mbeanSequence.getAndIncrement()))
            ));
            return registerMBean(mBean, mbeanName);
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    /**
     * Register a pipe source server MBean.
     *
     * @param mBean the MBean
     * @return a handle which may be used to unregister the MBean
     * @since 1.2
     */
    protected Closeable registerMBean(final PipeSourceServerMBean mBean) {
        try {
            final ObjectName mbeanName = new ObjectName(MANAGEMENT_DOMAIN, hashtable(
                    entry("provider", ObjectName.quote(getName())),
                    entry("type", "server"),
                    entry("protocol", "local-source"),
                    entry("id", Long.toString(mbeanSequence.getAndIncrement()))
            ));
            return registerMBean(mBean, mbeanName);
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    /**
     * Register a pipe sink server MBean.
     *
     * @param mBean the MBean
     * @return a handle which may be used to unregister the MBean
     * @since 1.2
     */
    protected Closeable registerMBean(final PipeSinkServerMBean mBean) {
        try {
            final ObjectName mbeanName = new ObjectName(MANAGEMENT_DOMAIN, hashtable(
                    entry("provider", ObjectName.quote(getName())),
                    entry("type", "server"),
                    entry("protocol", "local-sink"),
                    entry("id", Long.toString(mbeanSequence.getAndIncrement()))
            ));
            return registerMBean(mBean, mbeanName);
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    private static final class Registration {
        private final MBeanServer server;
        private final ObjectName objectName;

        private Registration(final MBeanServer server, final ObjectName objectName) {
            this.server = server;
            this.objectName = objectName;
        }
    }

    private static final class RegHandle implements Closeable {
        private final List<Registration> registrations;
        private final AtomicBoolean open = new AtomicBoolean(true);

        private RegHandle(final List<Registration> registrations) {
            this.registrations = registrations;
        }

        public void close() throws IOException {
            if (open.getAndSet(false)) {
                final SecurityManager sm = System.getSecurityManager();
                for (final Registration registration : registrations) {
                    if (sm != null) {
                        AccessController.doPrivileged(new PrivilegedAction<Void>() {
                            public Void run() {
                                doUnregister(registration);
                                return null;
                            }
                        });
                    } else {
                        doUnregister(registration);
                    }
                }
            }
        }

        private static void doUnregister(final Registration registration) {
            final MBeanServer server = registration.server;
            final ObjectName mBeanName = registration.objectName;
            try {
                server.unregisterMBean(mBeanName);
            } catch (InstanceNotFoundException e) {
                mlog.debug(e, "Failed to unregister mBean named \"%s\" on server %s", mBeanName, server);
            } catch (MBeanRegistrationException e) {
                mlog.debug(e, "Failed to unregister mBean named \"%s\" on server %s", mBeanName, server);
            }
        }
    }

    private static final class GetMBeanServersAction implements PrivilegedAction<Collection<? extends MBeanServer>> {

        private final String agentId;

        public GetMBeanServersAction(final String agentId) {
            this.agentId = agentId;
        }

        public Collection<? extends MBeanServer> run() {
            return MBeanServerFactory.findMBeanServer(agentId);
        }
    }

    private static final class GetPropertyAction implements PrivilegedAction<String> {
        private final String propertyName;
        private final String defaultValue;

        private GetPropertyAction(final String propertyName, final String defaultValue) {
            this.propertyName = propertyName;
            this.defaultValue = defaultValue;
        }

        public String run() {
            return System.getProperty(propertyName, defaultValue);
        }
    }
}
