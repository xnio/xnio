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

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.xnio.MessageConnection;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.XnioIoThread;
import org.xnio.conduits.MessageSinkConduit;
import org.xnio.conduits.MessageSourceConduit;
import org.xnio.conduits.ReadReadyHandler;
import org.xnio.conduits.WriteReadyHandler;

/**
 * {@link MessageConnection} mock.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public class MessageConnectionMock extends MessageConnection implements Mock {

    // the peer address
    private final SocketAddress peerAddress;
    // the local address
    private final SocketAddress localAddress;
    // the option map
    private final OptionMap optionMap;
    // info used to check correct delegation from API to mocks
    private String info;

    protected MessageConnectionMock(final XnioIoThread thread, SocketAddress localAddress, SocketAddress peerAddress, OptionMap optionMap) {
        super(thread);
        this.localAddress = localAddress;
        this.peerAddress = peerAddress;
        this.optionMap = optionMap;
        final Mockery context = new JUnit4Mockery();
        final MessageSourceConduit sourceConduit = context.mock(MessageSourceConduit.class, "source conduit");
        final MessageSinkConduit sinkConduit = context.mock(MessageSinkConduit.class, "sink conduit");
        context.checking(new Expectations() {{
            allowing(sourceConduit).getReadThread();
            will(returnValue(thread));
            allowing(sourceConduit).setReadReadyHandler(with(any(ReadReadyHandler.class)));
            allowing(sourceConduit).getWorker();
            will(returnValue(thread.getWorker()));

            allowing(sinkConduit).getWriteThread();
            will(returnValue(thread));
            allowing(sinkConduit).setWriteReadyHandler(with(any(WriteReadyHandler.class)));
            allowing(sinkConduit).getWorker();
            will(returnValue(thread.getWorker()));
        }});
        setSourceConduit(sourceConduit);
        setSinkConduit(sinkConduit);
    }

    @Override
    public SocketAddress getPeerAddress() {
        return peerAddress;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    @Override
    protected void notifyWriteClosed() {
        throw new UnsupportedOperationException("operation not implemented by mock");
    }

    @Override
    protected void notifyReadClosed() {
        throw new UnsupportedOperationException("operation not implemented by mock");
    }

    @Override
    public void setInfo(String info) {
        this.info = info;
    }

    @Override
    public String getInfo() {
        return info;
    }

    @Override
    public OptionMap getOptionMap() {
        return optionMap;
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return optionMap.get(option);
    }
}
