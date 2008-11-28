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
import org.jboss.xnio.channels.StreamSinkChannel;
import org.jboss.xnio.channels.StreamSourceChannel;

/**
 *
 */
public final class NioOneWayPipeConnection implements Closeable {

    private final NioPipeSourceChannelImpl sourceSide;
    private final NioPipeSinkChannelImpl sinkSide;

    NioOneWayPipeConnection(final NioXnio nioProvider, final IoHandler<? super StreamSourceChannel> sourceHandler, final IoHandler<? super StreamSinkChannel> sinkHandler, final Executor executor) throws IOException {
        final Pipe pipe = Pipe.open();
        final Pipe.SourceChannel source = pipe.source();
        final Pipe.SinkChannel sink = pipe.sink();
        source.configureBlocking(false);
        sink.configureBlocking(false);
        final NioPipeSourceChannelImpl sourceSide = new NioPipeSourceChannelImpl(source, sourceHandler, nioProvider);
        final NioPipeSinkChannelImpl sinkSide = new NioPipeSinkChannelImpl(sink, sinkHandler, nioProvider);
        this.sourceSide = sourceSide;
        this.sinkSide = sinkSide;
        nioProvider.addManaged(sourceSide);
        nioProvider.addManaged(sinkSide);
        executor.execute(new Runnable() {
            public void run() {
                HandlerUtils.<StreamSourceChannel>handleOpened(sourceHandler, sourceSide);
            }
        });
        executor.execute(new Runnable() {
            public void run() {
                HandlerUtils.<StreamSinkChannel>handleOpened(sinkHandler, sinkSide);
            }
        });
    }

    public NioPipeSourceChannelImpl getSourceSide() {
        return sourceSide;
    }

    public NioPipeSinkChannelImpl getSinkSide() {
        return sinkSide;
    }

    public void close() throws IOException {
        IoUtils.safeClose(sourceSide);
        IoUtils.safeClose(sinkSide);
    }
}