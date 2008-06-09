package org.jboss.xnio.core.nio;

import java.nio.channels.Pipe;
import java.util.concurrent.Executor;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.spi.PipeEnd;
import org.jboss.xnio.spi.Lifecycle;

/**
 *
 */
public final class NioPipeConnection implements Lifecycle, org.jboss.xnio.spi.Pipe {

    private NioProvider nioProvider;
    private IoHandler<? super StreamChannel> leftHandler;
    private IoHandler<? super StreamChannel> rightHandler;
    private NioPipeChannelImpl leftSide;
    private NioPipeChannelImpl rightSide;
    private Executor executor;
    private Executor leftSideExecutor;
    private Executor rightSideExecutor;
    private final PipeEnd<StreamChannel> leftEnd = new PipeEnd<StreamChannel>() {
        public void setHandler(final IoHandler<? super StreamChannel> ioHandler) {
            leftHandler = ioHandler;
        }

        public void setExecutor(final Executor executor) {
            leftSideExecutor = executor;
        }
    };
    private final PipeEnd<StreamChannel> rightEnd = new PipeEnd<StreamChannel>() {
        public void setHandler(final IoHandler<? super StreamChannel> ioHandler) {
            rightHandler = ioHandler;
        }

        public void setExecutor(final Executor executor) {
            rightSideExecutor = executor;
        }
    };

    public NioProvider getNioCore() {
        return nioProvider;
    }

    public void setNioProvider(final NioProvider nioProvider) {
        this.nioProvider = nioProvider;
    }

    public NioPipeChannelImpl getLeftSide() {
        return leftSide;
    }

    public void setLeftSide(final NioPipeChannelImpl leftSide) {
        this.leftSide = leftSide;
    }

    public NioPipeChannelImpl getRightSide() {
        return rightSide;
    }

    public void setRightSide(final NioPipeChannelImpl rightSide) {
        this.rightSide = rightSide;
    }

    public IoHandler<? super StreamChannel> getLeftHandler() {
        return leftHandler;
    }

    public void setLeftHandler(final IoHandler<? super StreamChannel> leftHandler) {
        this.leftHandler = leftHandler;
    }

    public IoHandler<? super StreamChannel> getRightHandler() {
        return rightHandler;
    }

    public void setRightHandler(final IoHandler<? super StreamChannel> rightHandler) {
        this.rightHandler = rightHandler;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    public PipeEnd<StreamChannel> getLeftEnd() {
        return leftEnd;
    }

    public PipeEnd<StreamChannel> getRightEnd() {
        return rightEnd;
    }

    public void create() throws Exception {
        if (leftHandler == null) {
            throw new NullPointerException("leftHandler is null");
        }
        if (rightHandler == null) {
            throw new NullPointerException("rightHandler is null");
        }
        if (nioProvider == null) {
            throw new NullPointerException("nioCore is null");
        }
        if (executor == null) {
            executor = nioProvider.getExecutor();
        }
        if (leftSideExecutor == null) {
            leftSideExecutor = executor;
        }
        if (rightSideExecutor == null) {
            rightSideExecutor = executor;
        }
    }

    public void start() throws Exception {
        final Pipe leftToRight = Pipe.open();
        final Pipe rightToLeft = Pipe.open();
        final Pipe.SourceChannel leftToRightSource = leftToRight.source();
        final Pipe.SinkChannel leftToRightSink = rightToLeft.sink();
        final Pipe.SourceChannel rightToLeftSource = rightToLeft.source();
        final Pipe.SinkChannel rightToLeftSink = leftToRight.sink();
        leftToRightSource.configureBlocking(false);
        leftToRightSink.configureBlocking(false);
        rightToLeftSource.configureBlocking(false);
        rightToLeftSink.configureBlocking(false);
        final NioPipeChannelImpl leftSide = new NioPipeChannelImpl(leftToRightSource, leftToRightSink, leftHandler, nioProvider);
        final NioPipeChannelImpl rightSide = new NioPipeChannelImpl(rightToLeftSource, rightToLeftSink, rightHandler, nioProvider);
        this.leftSide = leftSide;
        this.rightSide = rightSide;
        leftSideExecutor.execute(new Runnable() {
            public void run() {
                leftHandler.handleOpened(leftSide);
            }
        });
        rightSideExecutor.execute(new Runnable() {
            public void run() {
                rightHandler.handleOpened(rightSide);
            }
        });
    }

    public void stop() throws Exception {
        IoUtils.safeClose(leftSide);
        IoUtils.safeClose(rightSide);
        leftSide = null;
        rightSide = null;
    }

    public void destroy() throws Exception {
    }
}
