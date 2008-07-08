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

package org.jboss.xnio.helpers;

import java.io.Closeable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.ChannelSource;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.channels.StreamChannel;

/**
 *
 */
public final class ConnectionHelper<T extends StreamChannel> {
    private Closeable connection;
    private int reconnectTime = -1;
    private ScheduledExecutorService scheduledExecutor;
    private ChannelSource<T> channelSource;
    private IoHandler<? super T> handler;

    public int getReconnectTime() {
        return reconnectTime;
    }

    public void setReconnectTime(final int reconnectTime) {
        this.reconnectTime = reconnectTime;
    }

    public ScheduledExecutorService getScheduledExecutor() {
        return scheduledExecutor;
    }

    public void setScheduledExecutor(final ScheduledExecutorService scheduledExecutor) {
        this.scheduledExecutor = scheduledExecutor;
    }

    public ChannelSource<T> getClient() {
        return channelSource;
    }

    public void setClient(final ChannelSource<T> channelSource) {
        this.channelSource = channelSource;
    }

    public IoHandler<? super T> getHandler() {
        return handler;
    }

    public void setHandler(final IoHandler<? super T> handler) {
        this.handler = handler;
    }

    public void start() {
        final Executor reconnectExecutor;
        if (reconnectTime == -1) {
            reconnectExecutor = IoUtils.nullExecutor();
        } else if (reconnectTime == 0) {
            reconnectExecutor = IoUtils.directExecutor();
        } else {
            reconnectExecutor = IoUtils.delayedExecutor(scheduledExecutor, (long) reconnectTime, TimeUnit.MILLISECONDS);
        }
        connection = IoUtils.<T>createConnection(channelSource, handler, reconnectExecutor);
    }

    public void stop() {
        IoUtils.safeClose(connection);
    }
}
