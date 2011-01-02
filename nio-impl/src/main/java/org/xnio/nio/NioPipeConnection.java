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
import org.xnio.management.PipeConnectionMBean;

import javax.management.StandardMBean;
import javax.management.NotCompliantMBeanException;

/**
 *
 */
final class NioPipeConnection implements Closeable {

    private final NioPipeChannel leftSide;
    private final NioPipeChannel rightSide;

    NioPipeConnection(final NioXnio nioXnio) throws IOException {
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
        final MBean mbean;
        try {
            mbean = new MBean();
        } catch (NotCompliantMBeanException e) {
            throw new IOException("Failed to register channel mbean: " + e);
        }
        final Closeable mbeanHandle = nioXnio.registerMBean(mbean);
        final NioPipeChannel leftSide = NioPipeChannel.create(leftToRightSource, leftToRightSink, nioXnio, mbean.bytesRead, mbean.messagesRead, mbeanHandle);
        final NioPipeChannel rightSide = NioPipeChannel.create(rightToLeftSource, rightToLeftSink, nioXnio, mbean.bytesWritten, mbean.messagesWritten, mbeanHandle);
        this.leftSide = leftSide;
        this.rightSide = rightSide;
        nioXnio.addManaged(leftSide);
        nioXnio.addManaged(rightSide);
    }

    public NioPipeChannel getLeftSide() {
        return leftSide;
    }

    public NioPipeChannel getRightSide() {
        return rightSide;
    }

    public void close() throws IOException {
        IoUtils.safeClose(leftSide);
        IoUtils.safeClose(rightSide);
    }

    private final class MBean extends StandardMBean implements PipeConnectionMBean {

        private final AtomicLong bytesRead = new AtomicLong();
        private final AtomicLong bytesWritten = new AtomicLong();
        private final AtomicLong messagesRead = new AtomicLong();
        private final AtomicLong messagesWritten = new AtomicLong();

        private MBean() throws NotCompliantMBeanException {
            super(PipeConnectionMBean.class);
        }

        public long getBytesRead() {
            return bytesRead.get();
        }

        public long getMessagesRead() {
            return messagesRead.get();
        }

        public long getBytesWritten() {
            return bytesWritten.get();
        }

        public long getMessagesWritten() {
            return messagesWritten.get();
        }

        public void close() {
            IoUtils.safeClose(NioPipeConnection.this);
        }
    }
}
