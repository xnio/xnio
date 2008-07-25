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
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.net.SocketAddress;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.channels.UdpChannel;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.channels.StreamSourceChannel;
import org.jboss.xnio.channels.StreamSinkChannel;

/**
 * The XNIO entry point class.
 */
public abstract class Xnio implements Closeable {

    private static final String NIO_IMPL_CLASS_NAME = "org.jboss.xnio.nio.XnioNioImpl";
    private static final String PROVIDER_CLASS;

    static {
        String provider = System.getProperty("xnio.provider", NIO_IMPL_CLASS_NAME);
        PROVIDER_CLASS = provider;
    }

    /**
     * Create an instance of the default XNIO provider.  The class name of this provider can be specified through the
     * {@code xnio.provider} system property.  Any failure to create the XNIO provider will cause an {@code java.io.IOException}
     * to be thrown.
     *
     * @return an XNIO instance
     * @throws IOException the the XNIO provider could not be created
     */
    public static Xnio create() throws IOException {
        return createInstance(PROVIDER_CLASS, new Class[0]);
    }

    private static Xnio createInstance(String className, Class[] paramTypes, Object... params) throws IOException {
        try {
            Class<? extends Xnio> xnioClass = Class.forName(className).asSubclass(Xnio.class);
            final Constructor<? extends Xnio> constructor = xnioClass.getConstructor(paramTypes);
            return constructor.newInstance(params);
        } catch (ClassCastException e) {
            final IOException ioe = new IOException("The XNIO provider class \"" + PROVIDER_CLASS + "\" is not really an XNIO provider");
            ioe.initCause(e);
            throw ioe;
        } catch (ClassNotFoundException e) {
            final IOException ioe = new IOException("The XNIO provider class \"" + PROVIDER_CLASS + "\" was not found");
            ioe.initCause(e);
            throw ioe;
        } catch (IllegalAccessException e) {
            final IOException ioe = new IOException("The XNIO provider class \"" + PROVIDER_CLASS + "\" was not instantiatable due to an illegal access exception");
            ioe.initCause(e);
            throw ioe;
        } catch (InstantiationException e) {
            final IOException ioe = new IOException("The XNIO provider class \"" + PROVIDER_CLASS + "\" was not instantiatable due to an instantiation exception");
            ioe.initCause(e);
            throw ioe;
        } catch (InvocationTargetException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else {
                final IOException ioe = new IOException("The XNIO provider class \"" + PROVIDER_CLASS + "\" constructor threw an exception");
                ioe.initCause(cause);
                throw ioe;
            }
        } catch (NoSuchMethodException e) {
            final IOException ioe = new IOException("The XNIO provider class \"" + PROVIDER_CLASS + "\" does not have an accessible no-argument constructor");
            ioe.initCause(e);
            throw ioe;
        } catch (ExceptionInInitializerError e) {
            final IOException ioe = new IOException("The XNIO provider class \"" + PROVIDER_CLASS + "\" was not instantiatable due to an error in initialization");
            ioe.initCause(e);
            throw ioe;
        }
    }

    /**
     * Construct an XNIO provider instance.
     */
    protected Xnio() {
    }

    /**
     * Create an NIO-based XNIO provider.  A direct executor is used for the handlers; the provider will create its own
     * selector threads, of which there will be one reader thread, one writer thread, and one connect/accept thread.
     *
     * @return a new provider
     * @throws IOException if an I/O error occurs while starting the service
     * @deprecated Will be removed in 1.2.  Please use {@link #create()} instead, or use the constructor of your desired implementation.
     */
    @Deprecated
    public static Xnio createNio() throws IOException {
        return createInstance(NIO_IMPL_CLASS_NAME, new Class[0]);
    }

    /**
     * Create an NIO-based XNIO provider.  A direct executor is used for the handlers; the provider will
     * create its own selector threads.
     *
     * @param readSelectorThreads the number of threads to assign for readable events
     * @param writeSelectorThreads the number of threads to assign for writable events
     * @param connectSelectorThreads the number of threads to assign for connect/accept events
     * @return a new provider
     * @throws IOException if an I/O error occurs while starting the service
     * @throws IllegalArgumentException if a given argument is not valid
     * @deprecated Will be removed in 1.2.  Please use {@link #create()} instead, or use the constructor of your desired implementation.
     */
    @Deprecated
    public static Xnio createNio(final int readSelectorThreads, final int writeSelectorThreads, final int connectSelectorThreads) throws IOException, IllegalArgumentException {
        return createInstance(NIO_IMPL_CLASS_NAME, new Class[] { Integer.class, Integer.class, Integer.class }, Integer.valueOf(readSelectorThreads), Integer.valueOf(writeSelectorThreads), Integer.valueOf(connectSelectorThreads));
    }

    /**
     * Create an NIO-based XNIO provider.  The given handler executor is used for the handlers; the provider will
     * create its own selector threads.
     *
     * @param handlerExecutor the executor to use to handle events
     * @param readSelectorThreads the number of threads to assign for readable events
     * @param writeSelectorThreads the number of threads to assign for writable events
     * @param connectSelectorThreads the number of threads to assign for connect/accept events
     * @return a new provider
     * @throws IOException if an I/O error occurs while starting the service
     * @throws IllegalArgumentException if a given argument is not valid
     * @deprecated Will be removed in 1.2.  Please use {@link #create()} instead, or use the constructor of your desired implementation.
     */
    @Deprecated
    public static Xnio createNio(Executor handlerExecutor, final int readSelectorThreads, final int writeSelectorThreads, final int connectSelectorThreads) throws IOException, IllegalArgumentException {
        return createInstance(NIO_IMPL_CLASS_NAME, new Class[] { Executor.class, Integer.class, Integer.class, Integer.class }, handlerExecutor, Integer.valueOf(readSelectorThreads), Integer.valueOf(writeSelectorThreads), Integer.valueOf(connectSelectorThreads));
    }

    /**
     * Create an NIO-based XNIO provider.  The given handler executor is used for the handlers; the given thread
     * factory is used to create selector threads.
     *
     * @param handlerExecutor the executor to use to handle events
     * @param selectorThreadFactory the selector thread factory to use
     * @param readSelectorThreads the number of threads to assign for readable events
     * @param writeSelectorThreads the number of threads to assign for writable events
     * @param connectSelectorThreads the number of threads to assign for connect/accept events
     * @return a new provider
     * @throws IOException if an I/O error occurs while starting the service
     * @throws IllegalArgumentException if a given argument is not valid
     * @deprecated Will be removed in 1.2.  Please use {@link #create()} instead, or use the constructor of your desired implementation.
     */
    @Deprecated
    public static Xnio createNio(Executor handlerExecutor, ThreadFactory selectorThreadFactory, final int readSelectorThreads, final int writeSelectorThreads, final int connectSelectorThreads) throws IOException, IllegalArgumentException {
        return createInstance(NIO_IMPL_CLASS_NAME, new Class[] { Executor.class, ThreadFactory.class, Integer.class, Integer.class, Integer.class }, handlerExecutor, selectorThreadFactory, Integer.valueOf(readSelectorThreads), Integer.valueOf(writeSelectorThreads), Integer.valueOf(connectSelectorThreads));
    }

    /**
     * Create a TCP server.  The server will bind to the given addresses.
     *
     * @param executor the executor to use to execute the handlers
     * @param handlerFactory the factory which will produce handlers for inbound connections
     * @param bindAddresses the addresses to bind to
     *
     * @return a factory that can be used to configure the new TCP server
     */
    public ConfigurableFactory<Closeable> createTcpServer(Executor executor, IoHandlerFactory<? super TcpChannel> handlerFactory, SocketAddress... bindAddresses) {
        throw new UnsupportedOperationException("TCP Server");
    }

    /**
     * Create a TCP server.  The server will bind to the given addresses.  The provider's executor will be used to
     * execute handler methods.
     *
     * @param handlerFactory the factory which will produce handlers for inbound connections
     * @param bindAddresses the addresses to bind to
     *
     * @return a factory that can be used to configure the new TCP server
     */
    public ConfigurableFactory<Closeable> createTcpServer(IoHandlerFactory<? super TcpChannel> handlerFactory, SocketAddress... bindAddresses) {
        throw new UnsupportedOperationException("TCP Server");
    }

    /**
     * Create a configurable TCP connector.  The connector can be configured before it is actually created.
     *
     * @param executor the executor to use to execute the handlers
     *
     * @return a factory that can be used to configure the new TCP connector
     */
    public ConfigurableFactory<CloseableTcpConnector> createTcpConnector(Executor executor) {
        throw new UnsupportedOperationException("TCP Connector");
    }

    /**
     * Create a configurable TCP connector.  The connector can be configured before it is actually created.  The
     * provider's executor will be used to execute handler methods.
     *
     * @return a factory that can be used to configure the new TCP connector
     */
    public ConfigurableFactory<CloseableTcpConnector> createTcpConnector() {
        throw new UnsupportedOperationException("TCP Connector");
    }

    /**
     * Create a UDP server.  The server will bind to the given addresses.  The UDP server can be configured to be
     * multicast-capable; this should only be done if multicast is needed, since some providers have a performance
     * penalty associated with multicast.
     *
     * @param multicast {@code true} if the UDP server should be multicast-capable
     * @param executor the executor to use to execute the handlers
     * @param handlerFactory the factory which will produce handlers for each channel
     * @param bindAddresses the addresses to bind
     *
     * @return a factory that can be used to configure the new UDP server
     */
    public ConfigurableFactory<Closeable> createUdpServer(Executor executor, boolean multicast, IoHandlerFactory<? super UdpChannel> handlerFactory, SocketAddress... bindAddresses) {
        throw new UnsupportedOperationException("UDP Server");
    }

    /**
     * Create a UDP server.  The server will bind to the given addresses.  The provider's executor will be used to
     * execute handler methods.
     *
     * @param multicast {@code true} if the UDP server should be multicast-capable
     * @param handlerFactory the factory which will produce handlers for each channel
     * @param bindAddresses the addresses to bind
     *
     * @return a factory that can be used to configure the new UDP server
     */
    public ConfigurableFactory<Closeable> createUdpServer(boolean multicast, IoHandlerFactory<? super UdpChannel> handlerFactory, SocketAddress... bindAddresses) {
        throw new UnsupportedOperationException("UDP Server");
    }

    /**
     * Create a pipe "server".  The provided handler factory is used to supply handlers for the server "end" of the
     * pipe. The returned channel source is used to establish connections to the server.
     *
     * @param handlerFactory the server handler factory
     *
     * @return the client channel source
     */
    public ChannelSource<StreamChannel> createPipeServer(IoHandlerFactory<? super StreamChannel> handlerFactory) {
        throw new UnsupportedOperationException("Pipe Server");
    }

    /**
     * Create a one-way pipe "server".  The provided handler factory is used to supply handlers for the server "end" of
     * the pipe. The returned channel source is used to establish connections to the server.  The data flows from the
     * server to the client.
     *
     * @param handlerFactory the server handler factory
     *
     * @return the client channel source
     */
    public ChannelSource<StreamSourceChannel> createPipeSourceServer(IoHandlerFactory<? super StreamSinkChannel> handlerFactory) {
        throw new UnsupportedOperationException("One-way Pipe Server");
    }

    /**
     * Create a one-way pipe "server".  The provided handler factory is used to supply handlers for the server "end" of
     * the pipe. The returned channel source is used to establish connections to the server.  The data flows from the
     * server to the client.
     *
     * @param handlerFactory the server handler factory
     *
     * @return the client channel source
     */
    public ChannelSource<StreamSinkChannel> createPipeSinkServer(IoHandlerFactory<? super StreamSourceChannel> handlerFactory) {
        throw new UnsupportedOperationException("One-way Pipe Server");
    }

    /**
     * Create a single pipe connection.
     *
     * @param leftHandler the handler for the "left" side of the pipe
     * @param rightHandler the handler for the "right" side of the pipe
     *
     * @return the future connection
     */
    public IoFuture<Closeable> createPipeConnection(IoHandler<? super StreamChannel> leftHandler, IoHandler<? super StreamChannel> rightHandler) {
        throw new UnsupportedOperationException("Pipe Connection");
    }

    /**
     * Create a single one-way pipe connection.
     *
     * @param sourceHandler the handler for the "source" side of the pipe
     * @param sinkHandler the handler for the "sink" side of the pipe
     *
     * @return the future connection
     */
    public IoFuture<Closeable> createOneWayPipeConnection(IoHandler<? super StreamSourceChannel> sourceHandler, IoHandler<? super StreamSinkChannel> sinkHandler) {
        throw new UnsupportedOperationException("One-way Pipe Connection");
    }

    /**
     * Close this XNIO provider.  Calling this method more than one time has no additional effect.
     */
    public abstract void close() throws IOException;
}
