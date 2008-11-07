/**
 *
 */
package org.jboss.xnio.nio.test;

import static org.jboss.xnio.Buffers.flip;
import static org.jboss.xnio.IoUtils.safeClose;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import junit.framework.TestCase;

import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.channels.ConnectedStreamChannel;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.nio.NioProvider;
import org.jboss.xnio.nio.NioTcpConnector;
import org.jboss.xnio.nio.NioTcpServer;
import org.jboss.xnio.test.support.LoggingHelper;

/**
 * @author robhadfield
 *
 */
public class ManagementTestCase extends TestCase{
    static {
        LoggingHelper.init();
    }
    private static final int SERVER_PORT = 12345;
    private static final Logger log = Logger.getLogger(ManagementTestCase.class);
    private static final byte[] TESTDATA = new byte[] {00,11,22,33,44,55,66,77};

    MBeanServer mBeanServer;
    Xnio xnio;

    @Override
    public void setUp() throws IOException {
        mBeanServer = MBeanServerFactory.createMBeanServer("org.jboss.xnio");
        xnio = Xnio.create();
    }

    @Override
    public void tearDown() {
        IoUtils.safeClose(xnio);
        MBeanServerFactory.releaseMBeanServer(mBeanServer);
    }

    public void testTwoWayTransfer() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger clientSent = new AtomicInteger(0);
        final AtomicInteger clientReceived = new AtomicInteger(0);
        final AtomicInteger serverSent = new AtomicInteger(0);
        final AtomicInteger serverReceived = new AtomicInteger(0);
        final AtomicBoolean delayClientStop = new AtomicBoolean();
        final AtomicBoolean delayServerStop = new AtomicBoolean();
        doConnectionTest(new Runnable() {
            public void run() {
                try {
                    assertTrue(latch.await(1200L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new IoHandler<ConnectedStreamChannel<SocketAddress>>() {
            public void handleOpened(final ConnectedStreamChannel<SocketAddress> channel) {
                channel.resumeReads();
                channel.resumeWrites();
            }

            public void handleReadable(final ConnectedStreamChannel<SocketAddress> channel) {
            }


            public void handleWritable(final ConnectedStreamChannel<SocketAddress> channel) {
                try {
                    final ByteBuffer buffer = ByteBuffer.allocate(100);
                    buffer.put(TESTDATA);
                    channel.write(flip(buffer));
                    channel.shutdownWrites();
                    checkWriterConditions();
                    channel.close();
                } catch (Throwable t) {
                    t.printStackTrace();
                    throw new RuntimeException(t);
                }
            }

            public void handleClosed(final ConnectedStreamChannel<SocketAddress> channel) {
                latch.countDown();
            }
        }, new IoHandler<ConnectedStreamChannel<SocketAddress>>() {
            public void handleOpened(final ConnectedStreamChannel<SocketAddress> channel) {
                channel.resumeReads();
                channel.resumeWrites();
            }

            public void handleReadable(final ConnectedStreamChannel<SocketAddress> channel) {
                try {
                    final int c = channel.read(ByteBuffer.allocate(100));
                    assertEquals(c, TESTDATA.length);
                    checkReaderConditions();
                    channel.close();
                } catch (Throwable t) {
                    t.printStackTrace();
                    throw new RuntimeException(t);
                }
            }

            public void handleWritable(final ConnectedStreamChannel<SocketAddress> channel) {
            }

            public void handleClosed(final ConnectedStreamChannel<SocketAddress> channel) {
                latch.countDown();
            }
        });
        assertEquals(serverSent.get(), clientReceived.get());
        assertEquals(clientSent.get(), serverReceived.get());
    }

    private void doConnectionTest(final Runnable body, final IoHandler<? super TcpChannel> clientHandler, final IoHandler<? super TcpChannel> serverHandler) throws Exception {
        synchronized (this) {
            final NioProvider provider = new NioProvider();
            provider.start();
            try {
                final NioTcpServer nioTcpServer = new NioTcpServer();
                nioTcpServer.setNioProvider(provider);
                nioTcpServer.setReuseAddress(true);
                nioTcpServer.setBindAddresses(new SocketAddress[] { new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT)});
                nioTcpServer.setHandlerFactory(new IoHandlerFactory<TcpChannel>() {
                    public IoHandler<? super TcpChannel> createHandler() {
                        return serverHandler;
                    }
                });
                nioTcpServer.start();
                try {
                    final NioTcpConnector nioTcpConnector = new NioTcpConnector();
                    nioTcpConnector.setNioProvider(provider);
                    nioTcpConnector.setConnectTimeout(10);
                    nioTcpConnector.start();
                    try {
                        final IoFuture<TcpChannel> ioFuture = nioTcpConnector.connectTo(new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT), clientHandler);
                        final TcpChannel channel = ioFuture.get();
                        try {
                            body.run();
                            channel.close();
                        } finally {
                            safeClose(channel);
                        }
                    } finally {
                        nioTcpConnector.stop();
                    }
                } finally {
                    nioTcpServer.stop();
                }
            } finally {
                provider.stop();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void checkWriterConditions() throws Exception{
        // Check writer has written TESTDATA
        ObjectName xnioDomain = new ObjectName("org.jboss.xnio:*");
        List<ObjectInstance> mBeans = new ArrayList<ObjectInstance>(
                mBeanServer.queryMBeans(xnioDomain, null));
        boolean found = false;
        for (ObjectInstance oi : mBeans) {
            Long bytesWritten = getAttributeValue(oi, "BytesWritten");
            Long messagesWritten = getAttributeValue(oi, "MessagesWritten");
            if ((bytesWritten > 0) || (messagesWritten > 0)) {
                found = true;
                assertEquals(new Long(1),messagesWritten);
                assertEquals(new Long(TESTDATA.length),bytesWritten);
            }
        }
        assertTrue(found);
    }

    @SuppressWarnings("unchecked")
    private void checkReaderConditions() throws Exception{
     // Check reader has read TESTDATA
        ObjectName xnioDomain = new ObjectName("org.jboss.xnio:*");
        List<ObjectInstance> mBeans = new ArrayList<ObjectInstance>(
                mBeanServer.queryMBeans(xnioDomain, null));
        boolean found = false;
        for (ObjectInstance oi : mBeans) {
            Long bytesRead = getAttributeValue(oi, "BytesRead");
            Long messagesRead = getAttributeValue(oi, "MessagesRead");
            if ((bytesRead > 0) || (messagesRead > 0)) {
                found = true;
                assertEquals(new Long(1),messagesRead);
                assertEquals(new Long(TESTDATA.length),bytesRead);
            }
        }
        assertTrue(found);
    }

    private Long getAttributeValue(final ObjectInstance oi, final String attribName) {
        log.trace("MBean has objectName %s",oi.getObjectName());
        try {
            return (Long) mBeanServer.getAttribute(oi.getObjectName(),attribName);
        } catch (Exception e) {
            log.error(e,"getAttribute failed! (Can be due to object being deregistered)");
            return 0L;
        }
    }

}
