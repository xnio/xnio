package org.jboss.xnio.core.nio.test;

import junit.framework.TestCase;
import org.jboss.xnio.spi.Lifecycle;
import org.jboss.xnio.spi.Provider;
import org.jboss.xnio.spi.UdpServer;
import org.jboss.xnio.core.nio.NioProvider;
import org.jboss.xnio.channels.MulticastDatagramChannel;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.IoHandler;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.net.Inet4Address;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.IOException;

/**
 *
 */
public final class NioUdpTestCase extends TestCase {
    private static final int SERVER_PORT = 12345;

    private synchronized void start(Lifecycle lifecycle) throws IOException {
        lifecycle.start();
    }

    private synchronized void stop(Lifecycle lifecycle) throws IOException {
        lifecycle.stop();
    }

    private void safeStop(Lifecycle lifecycle) {
        try {
            stop(lifecycle);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void doServerSideTest(final boolean multicast, final IoHandler<MulticastDatagramChannel> handler, final Runnable body) throws IOException {
        final Provider nioProvider = new NioProvider();
        nioProvider.start();
        try {
            doServerSidePart(multicast, handler, body, nioProvider);
            nioProvider.stop();
        } finally {
            safeStop(nioProvider);
        }
    }

    private void doServerSidePart(final boolean multicast, final IoHandler<MulticastDatagramChannel> handler, final Runnable body, final Provider nioProvider) throws IOException {
        final InetSocketAddress bindAddress = new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT);
        doPart(multicast, handler, body, nioProvider, bindAddress);
    }

    private void doClientSidePart(final boolean multicast, final IoHandler<MulticastDatagramChannel> handler, final Runnable body, final Provider nioProvider) throws IOException {
        final InetSocketAddress bindAddress = new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 0, 0, 0, 0 }), 0);
        doPart(multicast, handler, body, nioProvider, bindAddress);
    }

    private void doPart(final boolean multicast, final IoHandler<MulticastDatagramChannel> handler, final Runnable body, final Provider nioProvider, final InetSocketAddress bindAddress) throws IOException {
        final UdpServer server = multicast ? nioProvider.createMulticastUdpServer() : nioProvider.createUdpServer();
        server.setBindAddresses(new SocketAddress[] { bindAddress });
        server.setHandlerFactory(new IoHandlerFactory<MulticastDatagramChannel>() {
            public IoHandler<? super MulticastDatagramChannel> createHandler() {
                return handler;
            }
        });
        server.start();
        try {
            body.run();
            server.stop();
        } finally {
            safeStop(server);
        }
    }

    private void doClientServerSide(final boolean multicast, final IoHandler<MulticastDatagramChannel> serverHandler, final IoHandler<MulticastDatagramChannel> clientHandler, final Runnable body) throws IOException {
        final Provider nioProvider = new NioProvider();
        nioProvider.start();
        try {
            doServerSidePart(multicast, serverHandler, new Runnable() {
                public void run() {
                    try {
                        doClientSidePart(multicast, clientHandler, body, nioProvider);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }, nioProvider);
            nioProvider.stop();
        } finally {
            safeStop(nioProvider);
        }
    }

    private void doServerCreate(boolean multicast) throws Exception {
        final AtomicBoolean openedOk = new AtomicBoolean(false);
        final AtomicBoolean closedOk = new AtomicBoolean(false);
        doServerSideTest(multicast, new IoHandler<MulticastDatagramChannel>() {
            public void handleOpened(final MulticastDatagramChannel channel) {
                openedOk.set(true);
            }

            public void handleReadable(final MulticastDatagramChannel channel) {
            }

            public void handleWritable(final MulticastDatagramChannel channel) {
            }

            public void handleClosed(final MulticastDatagramChannel channel) {
                closedOk.set(true);
            }
        }, new Runnable() {
            public void run() {
            }
        });
        assertTrue(openedOk.get());
        assertTrue(closedOk.get());
    }

    public void testNioServerCreate() throws Exception {
        doServerCreate(false);
    }

    public void testNioServerCreateMulticast() throws Exception {
        doServerCreate(true);
    }
}
