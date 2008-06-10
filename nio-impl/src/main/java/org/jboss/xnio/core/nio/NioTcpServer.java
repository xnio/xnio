package org.jboss.xnio.core.nio;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.Executor;
import java.util.Map;
import java.util.Collections;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.channels.ConnectedStreamChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.spi.TcpServer;
import org.jboss.xnio.spi.Lifecycle;

/**
 *
 */
public final class NioTcpServer implements Lifecycle, TcpServer {
    private static final Logger log = Logger.getLogger(NioTcpServer.class);

    private NioHandle[] handles;
    private ServerSocket[] serverSockets;
    private ServerSocketChannel[] serverSocketChannels;
    private Executor executor;

    private IoHandlerFactory<? super ConnectedStreamChannel<SocketAddress>> handlerFactory;

    private boolean reuseAddress = true;
    private int receiveBufferSize = -1;
    private int backlog = -1;
    private boolean keepAlive = false;
    private boolean oobInline = false;
    private boolean tcpNoDelay = false;

    private NioProvider nioProvider;
    private SocketAddress[] bindAddresses = new SocketAddress[0];

    // accessors

    public NioProvider getNioProvider() {
        return nioProvider;
    }

    public void setNioProvider(final NioProvider nioProvider) {
        this.nioProvider = nioProvider;
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(final boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public void setReceiveBufferSize(final int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public int getBacklog() {
        return backlog;
    }

    public void setBacklog(final int backlog) {
        this.backlog = backlog;
    }

    public IoHandlerFactory<? super ConnectedStreamChannel<SocketAddress>> getHandlerFactory() {
        return handlerFactory;
    }

    public void setHandlerFactory(final IoHandlerFactory<? super ConnectedStreamChannel<SocketAddress>> handlerFactory) {
        this.handlerFactory = handlerFactory;
    }

    public SocketAddress[] getBindAddresses() {
        return bindAddresses;
    }

    public void setBindAddresses(final SocketAddress[] bindAddresses) {
        this.bindAddresses = bindAddresses;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(final boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public boolean isOobInline() {
        return oobInline;
    }

    public void setOobInline(final boolean oobInline) {
        this.oobInline = oobInline;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(final boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    // lifecycle

    public void create() throws IOException {
        if (nioProvider == null) {
            throw new NullPointerException("nioCore is null");
        }
        if (handlerFactory == null) {
            throw new NullPointerException("handlerFactory is null");
        }
    }

    public void start() throws IOException {
        final int bindCount = bindAddresses.length;
        serverSocketChannels = new ServerSocketChannel[bindCount];
        serverSockets = new ServerSocket[bindCount];
        handles = new NioHandle[bindCount];
        for (int i = 0; i < bindCount; i++) {
            try {
                ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
                serverSocketChannel.configureBlocking(false);
                ServerSocket serverSocket = serverSocketChannel.socket();
                serverSocket.setReuseAddress(reuseAddress);
                if (receiveBufferSize > 0) {
                    serverSocket.setReceiveBufferSize(receiveBufferSize);
                }
                NioHandle handle = nioProvider.addConnectHandler(serverSocketChannel, new Handler(i));
                if (backlog > 0) {
                    serverSocket.bind(bindAddresses[i], backlog);
                } else {
                    serverSocket.bind(bindAddresses[i]);
                }
                serverSocketChannels[i] = serverSocketChannel;
                serverSockets[i] = serverSocket;
                handles[i] = handle;
            } catch (IOException ex) {
                // undo the opened sockets
                for (; i >= 0; i --) {
                    IoUtils.safeClose(serverSocketChannels[i]);
                }
                throw ex;
            }
        }
        for (int i = 0; i < bindCount; i++) {
            handles[i].getSelectionKey().interestOps(SelectionKey.OP_ACCEPT).selector().wakeup();
        }
    }

    public void stop() {
        int bindCount = bindAddresses.length;
        for (int i = 0; i < bindCount; i ++) {
            if (handles != null && handles.length > i && handles[i] != null) {
                if (handles[i] != null) try {
                    handles[i].cancelKey();
                } catch (Throwable t) {
                    log.trace(t, "Cancel key failed");
                }
            }
            if (serverSocketChannels != null && serverSocketChannels.length > i && serverSocketChannels[i] != null) {
                if (serverSocketChannels[i] != null) try {
                    serverSocketChannels[i].close();
                } catch (Throwable t) {
                    log.trace(t, "Cancel key failed");
                }
            }
        }
    }

    public void destroy() throws IOException {
    }

    public Object getOption(final String name) throws UnsupportedOptionException, IOException {
        throw new UnsupportedOptionException("No options supported by this server type");
    }

    public Map<String, Class<?>> getOptions() {
        return Collections.emptyMap();
    }

    public Configurable setOption(final String name, final Object value) throws IllegalArgumentException, IOException {
        throw new UnsupportedOptionException("No options supported by this server type");
    }

    // NioCore interface

    private final class Handler implements Runnable {
        private final int idx;

        public Handler(final int idx) {
            this.idx = idx;
        }

        public void run() {
            try {
                final SocketChannel socketChannel = serverSocketChannels[idx].accept();
                if (socketChannel != null) {
                    boolean ok = false;
                    try {
                        socketChannel.configureBlocking(false);
                        final Socket socket = socketChannel.socket();
                        socket.setKeepAlive(keepAlive);
                        socket.setOOBInline(oobInline);
                        socket.setTcpNoDelay(tcpNoDelay);
                        // IDEA thinks this is an unsafe cast, but it really isn't.  But to shut it up...
                        //noinspection unchecked
                        final IoHandler<? super ConnectedStreamChannel<SocketAddress>> streamIoHandler = handlerFactory.createHandler();
                        final NioSocketChannelImpl channel = new NioSocketChannelImpl(nioProvider, socketChannel, streamIoHandler);
                        try {
                            streamIoHandler.handleOpened(channel);
                            ok = true;
                        } catch (Throwable t) {
                            log.error(t, "Opened handler failed");
                        }
                    } finally {
                        if (! ok) {
                            // do NOT call close handler, since open handler was either not called or it failed
                            IoUtils.safeClose(socketChannel);
                        }
                    }
                }
            } catch (ClosedChannelException e) {
                log.trace("Channel closed: %s", e.getMessage());
                return;
            } catch (IOException e) {
                log.trace(e, "I/O error on TCP server");
            }
        }
    }

}