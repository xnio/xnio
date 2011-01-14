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

package org.xnio.nio;

import java.io.IOException;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ThreadFactory;
import java.net.InetSocketAddress;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.jboss.logging.Logger;
import org.xnio.Cancellable;
import org.xnio.ChannelListeners;
import org.xnio.ConnectionChannelThread;
import org.xnio.FailedIoFuture;
import org.xnio.FinishedIoFuture;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.ReadChannelThread;
import org.xnio.Version;
import org.xnio.WriteChannelThread;
import org.xnio.Xnio;
import org.xnio.OptionMap;
import org.xnio.ChannelListener;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.MulticastMessageChannel;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * An NIO-based XNIO provider for a standalone application.
 */
final class NioXnio extends Xnio {

    private static final Logger log = Logger.getLogger("org.xnio.nio");

    private interface SelectorCreator {
        Selector open() throws IOException;
    }

    private final SelectorCreator selectorCreator;

    static {
        log.info("XNIO NIO Implementation Version " + Version.VERSION);
    }

    /**
     * Construct a new NIO-based XNIO provider instance.  Should only be invoked by the service loader.
     */
    public NioXnio() {
        super("nio");
        final String providerClassName = SelectorProvider.provider().getClass().getCanonicalName();
        if ("sun.nio.ch.PollSelectorProvider".equals(providerClassName)) {
            log.warnf("The currently defined selector provider class (%s) is not supported for use with XNIO", providerClassName);
        }
        log.tracef("Starting up with selector provider %s", providerClassName);
        selectorCreator = AccessController.doPrivileged(
            new PrivilegedAction<SelectorCreator>() {
                public SelectorCreator run() {
                    try {
                        // A Polling selector is most efficient on most platforms for one-off selectors.  Try to hack a way to get them on demand.
                        final Class<? extends Selector> selectorImplClass = Class.forName("sun.nio.ch.PollSelectorImpl").asSubclass(Selector.class);
                        final Constructor<? extends Selector> constructor = selectorImplClass.getDeclaredConstructor(SelectorProvider.class);
                        // Usually package private.  So untrusting.
                        constructor.setAccessible(true);
                        log.trace("Using polling selector type for temporary selectors.");
                        return new SelectorCreator() {
                            public Selector open() throws IOException {
                                try {
                                    return constructor.newInstance(SelectorProvider.provider());
                                } catch (InstantiationException e) {
                                    return Selector.open();
                                } catch (IllegalAccessException e) {
                                    return Selector.open();
                                } catch (InvocationTargetException e) {
                                    try {
                                        throw e.getTargetException();
                                    } catch (IOException e2) {
                                        throw e2;
                                    } catch (RuntimeException e2) {
                                        throw e2;
                                    } catch (Error e2) {
                                        throw e2;
                                    } catch (Throwable t) {
                                        throw new IllegalStateException("Unexpected invocation exception", t);
                                    }
                                }
                            }
                        };
                    } catch (Exception e) {
                        // ignore.
                    }
                    // Can't get our selector type?  That's OK, just use the default.
                    log.trace("Using default selector type for temporary selectors.");
                    return new SelectorCreator() {
                        public Selector open() throws IOException {
                            return Selector.open();
                        }
                    };
                }
            }
        );
    }

    /** {@inheritDoc} */
    public ReadChannelThread createReadChannelThread(final ThreadFactory threadFactory) throws IOException {
        final NioReadChannelThread thread = new NioReadChannelThread(threadFactory);
        thread.start();
        return thread;
    }

    /** {@inheritDoc} */
    public WriteChannelThread createWriteChannelThread(final ThreadFactory threadFactory) throws IOException {
        final NioWriteChannelThread thread = new NioWriteChannelThread(threadFactory);
        thread.start();
        return thread;
    }

    /** {@inheritDoc} */
    public ConnectionChannelThread createConnectionChannelThread(final ThreadFactory threadFactory) throws IOException {
        final NioConnectionChannelThread thread = new NioConnectionChannelThread(threadFactory);
        thread.start();
        return thread;
    }

    protected AcceptingChannel<? extends ConnectedStreamChannel> createTcpServer(final InetSocketAddress bindAddress, final ConnectionChannelThread thread, final ChannelListener<? super AcceptingChannel<ConnectedStreamChannel>> acceptListener, final OptionMap optionMap) throws IOException {
        final ServerSocketChannel channel = ServerSocketChannel.open();
        channel.configureBlocking(false);
        channel.socket().bind(bindAddress);
        final NioTcpServer server = new NioTcpServer(this, channel);
        server.setAcceptThread(thread);
        //noinspection unchecked
        server.getAcceptSetter().set((ChannelListener<? super NioTcpServer>) acceptListener);
        return server;
    }

    /** {@inheritDoc} */
    protected IoFuture<? extends ConnectedStreamChannel> connectTcp(final InetSocketAddress bindAddress, final InetSocketAddress destinationAddress, final ConnectionChannelThread thread, final ReadChannelThread readThread, final WriteChannelThread writeThread, final ChannelListener<? super ConnectedStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        try {
            final SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.socket().bind(bindAddress);
            final NioTcpChannel tcpChannel = new NioTcpChannel(this, channel);
            ChannelListeners.invokeChannelListener(tcpChannel.getBoundChannel(), bindListener);
            if (channel.connect(destinationAddress)) {
                tcpChannel.setReadThread(readThread);
                tcpChannel.setWriteThread(writeThread);
                //noinspection unchecked
                ChannelListeners.invokeChannelListener(tcpChannel, openListener);
                return new FinishedIoFuture<ConnectedStreamChannel>(tcpChannel);
            }
            final NioSetter<SocketChannel> setter = new NioSetter<SocketChannel>();
            final FutureResult<NioTcpChannel> futureResult = new FutureResult<NioTcpChannel>();
            final NioHandle<SocketChannel> handle = ((NioConnectionChannelThread) thread).addChannel(channel, channel, 0, setter);
            setter.set(new ChannelListener<SocketChannel>() {
                public void handleEvent(final SocketChannel channel) {
                    try {
                        if (channel.finishConnect()) {
                            handle.cancelKey();
                            tcpChannel.setReadThread(readThread);
                            tcpChannel.setWriteThread(writeThread);
                            futureResult.setResult(tcpChannel);
                            //noinspection unchecked
                            ChannelListeners.invokeChannelListener(tcpChannel, openListener);
                        }
                    } catch (IOException e) {
                        IoUtils.safeClose(channel);
                        handle.cancelKey();
                        futureResult.setException(e);
                    }
                }

                public String toString() {
                    return "Connection finisher for " + channel;
                }
            });
            futureResult.addCancelHandler(new Cancellable() {
                public Cancellable cancel() {
                    if (futureResult.setCancelled()) {
                        handle.cancelKey();
                        IoUtils.safeClose(channel);
                    }
                    return this;
                }

                public String toString() {
                    return "Cancel handler for " + channel;
                }
            });
            handle.resume(SelectionKey.OP_CONNECT);
            return futureResult.getIoFuture();
        } catch (IOException e) {
            return new FailedIoFuture<ConnectedStreamChannel>(e);
        }
    }

    protected IoFuture<? extends ConnectedStreamChannel> acceptTcp(final InetSocketAddress destination, final ConnectionChannelThread thread, final ReadChannelThread readThread, final WriteChannelThread writeThread, final ChannelListener<? super ConnectedStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        try {
            final ServerSocketChannel channel = ServerSocketChannel.open();
            channel.configureBlocking(false);
            channel.socket().bind(destination);
            final NioSetter<NioTcpChannel> closeSetter = new NioSetter<NioTcpChannel>();
            //noinspection unchecked
            ChannelListeners.invokeChannelListener(new BoundChannel() {
                public SocketAddress getLocalAddress() {
                    return channel.socket().getLocalSocketAddress();
                }

                public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
                    final SocketAddress address = getLocalAddress();
                    return type.isInstance(address) ? type.cast(address) : null;
                }

                public ChannelListener.Setter<? extends BoundChannel> getCloseSetter() {
                    return closeSetter;
                }

                public boolean isOpen() {
                    return channel.isOpen();
                }

                public boolean supportsOption(final Option<?> option) {
                    return false;
                }

                public <T> T getOption(final Option<T> option) throws IOException {
                    return null;
                }

                public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
                    return null;
                }

                public void close() throws IOException {
                    channel.close();
                }

                public String toString() {
                    return String.format("TCP acceptor bound channel (NIO) <%h>", this);
                }
            }, bindListener);
            final SocketChannel accepted = channel.accept();
            if (accepted != null) {
                IoUtils.safeClose(channel);
                final NioTcpChannel tcpChannel = new NioTcpChannel(this, accepted);
                tcpChannel.setReadThread(readThread);
                tcpChannel.setWriteThread(writeThread);
                //noinspection unchecked
                ChannelListeners.invokeChannelListener(tcpChannel, openListener);
                return new FinishedIoFuture<ConnectedStreamChannel>(tcpChannel);
            }
            final NioSetter<ServerSocketChannel> setter = new NioSetter<ServerSocketChannel>();
            final FutureResult<NioTcpChannel> futureResult = new FutureResult<NioTcpChannel>();
            final NioHandle<ServerSocketChannel> handle = ((NioConnectionChannelThread) thread).addChannel(channel, channel, 0, setter);
            setter.set(new ChannelListener<ServerSocketChannel>() {
                public void handleEvent(final ServerSocketChannel channel) {
                    final SocketChannel accepted;
                    try {
                        accepted = channel.accept();
                        if (accepted == null) {
                            return;
                        }
                    } catch (IOException e) {
                        IoUtils.safeClose(channel);
                        handle.cancelKey();
                        futureResult.setException(e);
                        return;
                    }
                    handle.cancelKey();
                    IoUtils.safeClose(channel);
                    try {
                        accepted.configureBlocking(false);
                    } catch (IOException e) {
                        IoUtils.safeClose(accepted);
                        futureResult.setException(e);
                        return;
                    }
                    final NioTcpChannel tcpChannel = new NioTcpChannel(NioXnio.this, accepted);
                    tcpChannel.setReadThread(readThread);
                    tcpChannel.setWriteThread(writeThread);
                    futureResult.setResult(tcpChannel);
                    //noinspection unchecked
                    ChannelListeners.invokeChannelListener(tcpChannel, openListener);
                }

                public String toString() {
                    return "Accepting finisher for " + channel;
                }
            });
            handle.resume(SelectionKey.OP_ACCEPT);
            return futureResult.getIoFuture();
        } catch (IOException e) {
            return new FailedIoFuture<ConnectedStreamChannel>(e);
        }
    }

    /** {@inheritDoc} */
    public MulticastMessageChannel createUdpServer(final InetSocketAddress bindAddress, final ReadChannelThread readThread, final WriteChannelThread writeThread, final ChannelListener<? super MulticastMessageChannel> bindListener, final OptionMap optionMap) throws IOException {
        if (optionMap.get(Options.MULTICAST, false)) {
            return new BioMulticastUdpChannel(optionMap.get(Options.SEND_BUFFER, 8192), optionMap.get(Options.RECEIVE_BUFFER, 8192), new MulticastSocket());
        } else {
            final DatagramChannel channel = DatagramChannel.open();
            channel.configureBlocking(false);
            channel.socket().bind(bindAddress);
            final NioUdpChannel udpChannel = new NioUdpChannel(this, channel);
            udpChannel.setReadThread(readThread);
            udpChannel.setWriteThread(writeThread);
            //noinspection unchecked
            ChannelListeners.invokeChannelListener(udpChannel, bindListener);
            return udpChannel;
        }
    }

    private final ThreadLocal<Selector> selectorThreadLocal = new ThreadLocal<Selector>() {
        public void remove() {
            // if no selector was created, none will be closed
            IoUtils.safeClose(get());
            super.remove();
        }
    };

    Selector getSelector() throws IOException {
        final ThreadLocal<Selector> threadLocal = selectorThreadLocal;
        Selector selector = threadLocal.get();
        if (selector == null) {
            selector = selectorCreator.open();
            threadLocal.set(selector);
        }
        return selector;
    }
}
