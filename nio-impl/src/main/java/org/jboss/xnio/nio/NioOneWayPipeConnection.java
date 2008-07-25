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
import org.jboss.xnio.channels.StreamSinkChannel;
import org.jboss.xnio.channels.StreamSourceChannel;

/**
 *
 */
public final class NioOneWayPipeConnection implements Lifecycle {

    private NioProvider nioProvider;
    private IoHandler<? super StreamSourceChannel> sourceHandler;
    private IoHandler<? super StreamSinkChannel> sinkHandler;
    private NioPipeSourceChannelImpl sourceSide;
    private NioPipeSinkChannelImpl sinkSide;
    private Executor executor;
    private Executor sourceSideExecutor;
    private Executor sinkSideExecutor;

    public NioProvider getNioProvider() {
        return nioProvider;
    }

    public void setNioProvider(final NioProvider nioProvider) {
        this.nioProvider = nioProvider;
    }

    public NioPipeSourceChannelImpl getSourceSide() {
        return sourceSide;
    }

    public void setSourceSide(final NioPipeSourceChannelImpl sourceSide) {
        this.sourceSide = sourceSide;
    }

    public NioPipeSinkChannelImpl getSinkSide() {
        return sinkSide;
    }

    public void setSinkSide(final NioPipeSinkChannelImpl sinkSide) {
        this.sinkSide = sinkSide;
    }

    public IoHandler<? super StreamSourceChannel> getSourceHandler() {
        return sourceHandler;
    }

    public void setSourceHandler(final IoHandler<? super StreamSourceChannel> sourceHandler) {
        this.sourceHandler = sourceHandler;
    }

    public IoHandler<? super StreamSinkChannel> getSinkHandler() {
        return sinkHandler;
    }

    public void setSinkHandler(final IoHandler<? super StreamSinkChannel> sinkHandler) {
        this.sinkHandler = sinkHandler;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    public Executor getSourceSideExecutor() {
        return sourceSideExecutor;
    }

    public void setSourceSideExecutor(final Executor sourceSideExecutor) {
        this.sourceSideExecutor = sourceSideExecutor;
    }

    public Executor getSinkSideExecutor() {
        return sinkSideExecutor;
    }

    public void setSinkSideExecutor(final Executor sinkSideExecutor) {
        this.sinkSideExecutor = sinkSideExecutor;
    }

    public void start() throws IOException {
        if (sourceHandler == null) {
            throw new NullPointerException("leftHandler is null");
        }
        if (sinkHandler == null) {
            throw new NullPointerException("rightHandler is null");
        }
        if (nioProvider == null) {
            throw new NullPointerException("nioProvider is null");
        }
        if (executor == null) {
            executor = nioProvider.getExecutor();
        }
        if (sourceSideExecutor == null) {
            sourceSideExecutor = executor;
        }
        if (sinkSideExecutor == null) {
            sinkSideExecutor = executor;
        }
        final Pipe pipe = Pipe.open();
        final Pipe.SourceChannel source = pipe.source();
        final Pipe.SinkChannel sink = pipe.sink();
        source.configureBlocking(false);
        sink.configureBlocking(false);
        final NioPipeSourceChannelImpl sourceSide = new NioPipeSourceChannelImpl(source, sourceHandler, nioProvider);
        final NioPipeSinkChannelImpl sinkSide = new NioPipeSinkChannelImpl(sink, sinkHandler, nioProvider);
        this.sourceSide = sourceSide;
        this.sinkSide = sinkSide;
        sourceSideExecutor.execute(new Runnable() {
            public void run() {
                HandlerUtils.<StreamSourceChannel>handleOpened(sourceHandler, sourceSide);
            }
        });
        sinkSideExecutor.execute(new Runnable() {
            public void run() {
                HandlerUtils.<StreamSinkChannel>handleOpened(sinkHandler, sinkSide);
            }
        });
        nioProvider.addChannel(sourceSide);
        nioProvider.addChannel(sinkSide);
    }

    public void stop() throws IOException {
        nioProvider.removeChannel(sourceSide);
        nioProvider.removeChannel(sinkSide);
        IoUtils.safeClose(sourceSide);
        IoUtils.safeClose(sinkSide);
        sourceSide = null;
        sinkSide = null;
    }
}