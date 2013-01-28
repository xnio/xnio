/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import org.xnio.XnioIoThread;
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
    public XnioIoThread getIoThread() {
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
