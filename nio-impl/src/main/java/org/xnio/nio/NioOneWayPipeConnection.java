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
import java.io.Closeable;
import java.nio.channels.Pipe;
import java.util.concurrent.atomic.AtomicLong;
import org.xnio.IoUtils;
import org.xnio.management.OneWayPipeConnectionMBean;

import javax.management.StandardMBean;
import javax.management.NotCompliantMBeanException;

/**
 *
 */
final class NioOneWayPipeConnection implements Closeable {

    private final NioPipeSourceChannel sourceSide;
    private final NioPipeSinkChannel sinkSide;

    NioOneWayPipeConnection(final NioXnio nioXnio) throws IOException {
        final Pipe pipe = Pipe.open();
        final Pipe.SourceChannel source = pipe.source();
        final Pipe.SinkChannel sink = pipe.sink();
        source.configureBlocking(false);
        sink.configureBlocking(false);
        final MBean mbean;
        try {
            mbean = new MBean();
        } catch (NotCompliantMBeanException e) {
            throw new IOException("Failed to register channel mbean: " + e);
        }
        final Closeable mbeanHandle = nioXnio.registerMBean(mbean);
        final NioPipeSourceChannel sourceSide = new NioPipeSourceChannel(source, nioXnio, mbeanHandle);
        final NioPipeSinkChannel sinkSide = new NioPipeSinkChannel(sink, nioXnio, mbean.bytes, mbean.messages, mbeanHandle);
        this.sourceSide = sourceSide;
        this.sinkSide = sinkSide;
        nioXnio.addManaged(sourceSide);
        nioXnio.addManaged(sinkSide);
    }

    public NioPipeSourceChannel getSourceSide() {
        return sourceSide;
    }

    public NioPipeSinkChannel getSinkSide() {
        return sinkSide;
    }

    public void close() throws IOException {
        IoUtils.safeClose(sourceSide);
        IoUtils.safeClose(sinkSide);
    }

    private final class MBean extends StandardMBean implements OneWayPipeConnectionMBean {
        private final AtomicLong bytes = new AtomicLong();
        private final AtomicLong messages = new AtomicLong();

        private MBean() throws NotCompliantMBeanException {
            super(OneWayPipeConnectionMBean.class);
        }

        public long getBytesWritten() {
            return bytes.get();
        }

        public long getMessagesWritten() {
            return messages.get();
        }

        public void close() {
            IoUtils.safeClose(NioOneWayPipeConnection.this);
        }
    }
}