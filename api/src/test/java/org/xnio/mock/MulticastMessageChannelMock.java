/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.xnio.mock;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.xnio.ChannelListener.Setter;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.MulticastMessageChannel;
import org.xnio.channels.SocketAddressBuffer;

/**
 * Mock for {@link MulticastMessageChannel}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class MulticastMessageChannelMock implements MulticastMessageChannel, ChannelMock {
    private final InetSocketAddress bindAddress;
    private final OptionMap optionMap;
    private String info = null; // any extra information regarding this channel used by tests


    public MulticastMessageChannelMock(InetSocketAddress bindAddress, OptionMap optionMap) {
        this.bindAddress = bindAddress;
        this.optionMap = optionMap;
    }

    @Override
    public int receiveFrom(SocketAddressBuffer addressBuffer, ByteBuffer buffer) throws IOException {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public long receiveFrom(SocketAddressBuffer addressBuffer, ByteBuffer[] buffers) throws IOException {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public long receiveFrom(SocketAddressBuffer addressBuffer, ByteBuffer[] buffers, int offs, int len) throws IOException {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public void suspendReads() {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public void resumeReads() {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public boolean isReadResumed() {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public void wakeupReads() {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public void shutdownReads() throws IOException {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public void awaitReadable() throws IOException {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public XnioExecutor getReadThread() {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public XnioWorker getWorker() {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public boolean isOpen() {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public boolean sendTo(SocketAddress target, ByteBuffer buffer) throws IOException {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public boolean sendTo(SocketAddress target, ByteBuffer[] buffers) throws IOException {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public boolean sendTo(SocketAddress target, ByteBuffer[] buffers, int offset, int length) throws IOException {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public void suspendWrites() {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public void resumeWrites() {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public boolean isWriteResumed() {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public void wakeupWrites() {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public void shutdownWrites() throws IOException {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public void awaitWritable() throws IOException {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public XnioExecutor getWriteThread() {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public boolean flush() throws IOException {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public SocketAddress getLocalAddress() {
        return bindAddress;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <A extends SocketAddress> A getLocalAddress(Class<A> type) {
        if (type.getClass().isAssignableFrom(InetSocketAddress.class)) {
            return (A) bindAddress;
        }
        return null;
    }

    @Override
    public Key join(InetAddress group, NetworkInterface iface) throws IOException {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public Key join(InetAddress group, NetworkInterface iface, InetAddress source) throws IOException {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public Setter<? extends MulticastMessageChannel> getReadSetter() {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public Setter<? extends MulticastMessageChannel> getCloseSetter() {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public Setter<? extends MulticastMessageChannel> getWriteSetter() {
        throw new UnsupportedOperationException("MulticastMessageChannelMock does not support this operation");
    }

    @Override
    public OptionMap getOptionMap() {
        return optionMap;
    }

    @Override
    public String getInfo() {
        return info;
    }

    @Override
    public void setInfo(String i) {
        info = i;
    }
}