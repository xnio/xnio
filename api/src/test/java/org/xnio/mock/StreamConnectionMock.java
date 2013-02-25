/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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
import java.net.SocketAddress;

import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;

/**
 * {@link StreamConnection}  mock.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public class StreamConnectionMock extends StreamConnection implements Mock {

    // the option map
    private OptionMap optionMap;
    // the peer address
    private SocketAddress peerAddress;
    // the local address
    private SocketAddress localAddress;
    // the server responsible for accepting this connection
    private AcceptingChannelMock server;
    // any extra information regarding this channel used by tests, usually used to check for correct delegation between api and impl
    private String info = null;

    public StreamConnectionMock(ConduitMock conduit) {
        super(conduit.getXnioIoThread());
        setSourceConduit(conduit);
        setSinkConduit(conduit);
    }

    /**
     * Sets the accepting server that created this connection.
     */
    void setServer(AcceptingChannelMock server) {
        this.server = server;
    }

    /**
     * Returns the accepting server that created this connection.
     */
    public AcceptingChannelMock getServer() {
        return server;
    }


    @Override
    public SocketAddress getPeerAddress() {
        return peerAddress;
    }

    /**
     * Sets the peer address.
     */
    public void setPeerAddress(SocketAddress peerAddress) {
        this.peerAddress = peerAddress;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    /**
     * Sets the local address.
     */
    public void setLocalAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        return optionMap == null? false: optionMap.contains(option);
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return optionMap == null? null: optionMap.get(option);
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        final OptionMap.Builder optionMapBuilder = OptionMap.builder();
        T previousValue = null;
        if (optionMap != null) {
            optionMapBuilder.addAll(optionMap);
            previousValue = optionMap.get(option);
        }
        optionMapBuilder.set(option, value);
        optionMap = optionMapBuilder.getMap();
        return previousValue;
    }

    /**
     * Sets the option map.
     */
    public void setOptionMap(OptionMap optionMap) {
        this.optionMap = optionMap;
    }

    @Override
    public OptionMap getOptionMap() {
        return optionMap;
    }

    @Override
    protected void notifyWriteClosed() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void notifyReadClosed() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setInfo(String info) {
        this.info = info;
    }

    @Override
    public String getInfo() {
        return info;
    }

}