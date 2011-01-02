/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

package org.xnio;

import java.net.Socket;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.Closeable;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import javax.management.StandardMBean;
import javax.management.NotCompliantMBeanException;

import org.xnio.management.TcpConnectionMBean;

final class ManagedSocket extends Socket {

    private static final AtomicLongFieldUpdater<ManagedSocket> bytesReadUpdater = AtomicLongFieldUpdater.newUpdater(ManagedSocket.class, "bytesRead");
    private static final AtomicLongFieldUpdater<ManagedSocket> bytesWrittenUpdater = AtomicLongFieldUpdater.newUpdater(ManagedSocket.class, "bytesWritten");
    private static final AtomicLongFieldUpdater<ManagedSocket> msgsReadUpdater = AtomicLongFieldUpdater.newUpdater(ManagedSocket.class, "msgsRead");
    private static final AtomicLongFieldUpdater<ManagedSocket> msgsWrittenUpdater = AtomicLongFieldUpdater.newUpdater(ManagedSocket.class, "msgsWritten");

    private volatile long bytesRead = 0L;
    private volatile long bytesWritten = 0L;
    private volatile long msgsRead = 0L;
    private volatile long msgsWritten = 0L;

    private InputStream inputStream;
    private OutputStream outputStream;
    private Closeable registration;

    ManagedSocket() throws IOException {}

    ManagedSocket configure(final OptionMap optionMap) throws IOException {
        synchronized (this) {
            super.setTcpNoDelay(optionMap.get(Options.TCP_NODELAY, false));
            super.setKeepAlive(optionMap.get(Options.KEEP_ALIVE, false));
//            super.setSoLinger();
            super.setOOBInline(optionMap.get(Options.TCP_OOB_INLINE, false));
//            super.setPerformancePreferences();
            if (optionMap.contains(Options.RECEIVE_BUFFER)) {
                super.setReceiveBufferSize(optionMap.get(Options.RECEIVE_BUFFER, 0));
            }
            super.setReuseAddress(optionMap.get(Options.REUSE_ADDRESSES, false));
            if (optionMap.contains(Options.SEND_BUFFER)) {
                super.setSendBufferSize(optionMap.get(Options.SEND_BUFFER, 0));
            }
//            super.setSoTimeout();
            if (optionMap.contains(Options.IP_TRAFFIC_CLASS)) {
                super.setTrafficClass(optionMap.get(Options.IP_TRAFFIC_CLASS, 0));
            }
        }
        return this;
    }

    MBean getMBean() throws NotCompliantMBeanException {
        return new MBean();
    }

    public InputStream getInputStream() throws IOException {
        synchronized (this) {
            final InputStream inputStream = this.inputStream;
            if (inputStream == null) {
                return this.inputStream = new Input(super.getInputStream());
            } else {
                return inputStream;
            }
        }
    }

    public OutputStream getOutputStream() throws IOException {
        synchronized (this) {
            final OutputStream outputStream = this.outputStream;
            if (outputStream == null) {
                return this.outputStream = new Output(super.getOutputStream());
            } else {
                return outputStream;
            }
        }
    }

    public void setRegistration(final Closeable closeable) {
        synchronized (this) {
            registration = closeable;
        }
    }

    public void close() throws IOException {
        try {
            super.close();
        } finally {
            IoUtils.safeClose(registration);
        }
    }

    private final class Input extends FilterInputStream {

        protected Input(InputStream in) {
            super(in);
        }

        public int read(final byte[] b, final int off, final int len) throws IOException {
            int cnt = in.read(b, off, len);
            if (cnt > 0) {
                bytesReadUpdater.getAndAdd(ManagedSocket.this, (long) cnt);
                msgsReadUpdater.getAndIncrement(ManagedSocket.this);
            }
            return cnt;
        }

        public int read() throws IOException {
            final int val = in.read();
            if (val >= 0) {
                bytesReadUpdater.getAndIncrement(ManagedSocket.this);
                msgsReadUpdater.getAndIncrement(ManagedSocket.this);
            }
            return val;
        }

        public long skip(final long n) throws IOException {
            final long cnt = in.skip(n);
            if (cnt > 0) {
                bytesReadUpdater.getAndAdd(ManagedSocket.this, cnt);
            }
            return cnt;
        }
    }

    private final class Output extends FilterOutputStream {

        protected Output(OutputStream out) {
            super(out);
        }

        public void write(final int b) throws IOException {
            out.write(b);
            bytesWrittenUpdater.getAndIncrement(ManagedSocket.this);
            msgsWrittenUpdater.getAndIncrement(ManagedSocket.this);
        }

        public void write(final byte[] b, final int off, final int len) throws IOException {
            out.write(b, off, len);
            bytesWrittenUpdater.getAndAdd(ManagedSocket.this, (long) len);
            msgsWrittenUpdater.getAndIncrement(ManagedSocket.this);
        }
    }

    private final class MBean extends StandardMBean implements TcpConnectionMBean {

        public MBean() throws NotCompliantMBeanException {
            super(TcpConnectionMBean.class);
        }

        public long getBytesRead() {
            return bytesRead;
        }

        public long getBytesWritten() {
            return bytesWritten;
        }

        public long getMessagesRead() {
            return msgsRead;
        }

        public long getMessagesWritten() {
            return msgsWritten;
        }

        public String toString() {
            return "ChannelMBean";
        }

        public InetSocketAddress getPeerAddress() {
            return (InetSocketAddress) getRemoteSocketAddress();
        }

        public InetSocketAddress getBindAddress() {
            final InetSocketAddress lsa = (InetSocketAddress) getLocalSocketAddress();
            if (lsa == null) {
                return new InetSocketAddress(0);
            }
            return lsa;
        }

        public void close() {
            IoUtils.safeClose(ManagedSocket.this);
        }
    }
}
