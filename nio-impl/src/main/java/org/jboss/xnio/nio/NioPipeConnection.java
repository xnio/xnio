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
import java.io.Closeable;
import java.nio.channels.Pipe;
import java.util.concurrent.Executor;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.channels.StreamChannel;

/**
 *
 */
public final class NioPipeConnection implements Closeable {

    private final NioPipeChannelImpl leftSide;
    private final NioPipeChannelImpl rightSide;

    NioPipeConnection(final NioXnio nioXnio, final IoHandler<? super StreamChannel> leftHandler, final IoHandler<? super StreamChannel> rightHandler, final Executor executor) throws IOException {
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
        final NioPipeChannelImpl leftSide = new NioPipeChannelImpl(leftToRightSource, leftToRightSink, leftHandler, nioXnio);
        final NioPipeChannelImpl rightSide = new NioPipeChannelImpl(rightToLeftSource, rightToLeftSink, rightHandler, nioXnio);
        this.leftSide = leftSide;
        this.rightSide = rightSide;
        nioXnio.addManaged(leftSide);
        nioXnio.addManaged(rightSide);
        executor.execute(new Runnable() {
            public void run() {
                if (! HandlerUtils.<StreamChannel>handleOpened(leftHandler, leftSide)) {
                    IoUtils.safeClose(leftToRightSource);
                    IoUtils.safeClose(leftToRightSink);
                    IoUtils.safeClose(rightToLeftSource);
                    IoUtils.safeClose(rightToLeftSink);
                }
            }
        });
        executor.execute(new Runnable() {
            public void run() {
                if (! HandlerUtils.<StreamChannel>handleOpened(rightHandler, rightSide)) {
                    IoUtils.safeClose(leftToRightSource);
                    IoUtils.safeClose(leftToRightSink);
                    IoUtils.safeClose(rightToLeftSource);
                    IoUtils.safeClose(rightToLeftSink);
                }
            }
        });
    }

    public NioPipeChannelImpl getLeftSide() {
        return leftSide;
    }

    public NioPipeChannelImpl getRightSide() {
        return rightSide;
    }

    public void close() throws IOException {
        IoUtils.safeClose(leftSide);
        IoUtils.safeClose(rightSide);
    }
}
