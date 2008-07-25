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

package org.jboss.xnio.nio;

import java.io.IOException;
import java.nio.channels.Pipe;
import java.util.concurrent.Executor;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.channels.StreamChannel;

/**
 *
 */
public final class NioPipeConnection implements Lifecycle {

    private NioProvider nioProvider;
    private IoHandler<? super StreamChannel> leftHandler;
    private IoHandler<? super StreamChannel> rightHandler;
    private NioPipeChannelImpl leftSide;
    private NioPipeChannelImpl rightSide;
    private Executor executor;
    private Executor leftSideExecutor;
    private Executor rightSideExecutor;

    public NioProvider getNioProvider() {
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

    public Executor getLeftSideExecutor() {
        return leftSideExecutor;
    }

    public void setLeftSideExecutor(final Executor leftSideExecutor) {
        this.leftSideExecutor = leftSideExecutor;
    }

    public Executor getRightSideExecutor() {
        return rightSideExecutor;
    }

    public void setRightSideExecutor(final Executor rightSideExecutor) {
        this.rightSideExecutor = rightSideExecutor;
    }

    public void start() throws IOException {
        if (leftHandler == null) {
            throw new NullPointerException("leftHandler is null");
        }
        if (rightHandler == null) {
            throw new NullPointerException("rightHandler is null");
        }
        if (nioProvider == null) {
            throw new NullPointerException("nioProvider is null");
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
                if (! HandlerUtils.<StreamChannel>handleOpened(leftHandler, leftSide)) {
                    IoUtils.safeClose(leftToRightSource);
                    IoUtils.safeClose(leftToRightSink);
                    IoUtils.safeClose(rightToLeftSource);
                    IoUtils.safeClose(rightToLeftSink);
                }
            }
        });
        rightSideExecutor.execute(new Runnable() {
            public void run() {
                if (! HandlerUtils.<StreamChannel>handleOpened(rightHandler, rightSide)) {
                    IoUtils.safeClose(leftToRightSource);
                    IoUtils.safeClose(leftToRightSink);
                    IoUtils.safeClose(rightToLeftSource);
                    IoUtils.safeClose(rightToLeftSink);
                }
            }
        });
        nioProvider.addChannel(leftSide);
        nioProvider.addChannel(rightSide);
    }

    public void stop() throws IOException {
        IoUtils.safeClose(leftSide);
        IoUtils.safeClose(rightSide);
        leftSide = null;
        rightSide = null;
    }
}
