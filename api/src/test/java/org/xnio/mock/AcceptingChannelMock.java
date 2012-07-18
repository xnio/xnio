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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.xnio.ChannelListener;
import org.xnio.ChannelListener.Setter;
import org.xnio.ChannelListener.SimpleSetter;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.OptionMap.Builder;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;

/**
 * {@link AcceptingChannel} mock.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class AcceptingChannelMock implements AcceptingChannel<ConnectedStreamChannelMock>, ChannelMock {
    private SimpleSetter<AcceptingChannelMock> acceptSetter = new SimpleSetter<AcceptingChannelMock>();
    private SimpleSetter<AcceptingChannelMock> closeSetter = new SimpleSetter<AcceptingChannelMock>();
    private volatile boolean acceptanceEnabled = true;
    private OptionMap optionMap = OptionMap.EMPTY;
    private boolean closed = false;
    private SocketAddress localAddress;
    private boolean acceptsResumed = false;
    private boolean acceptsWokenUp = false;
    private String info = null; // any extra information regarding this channel used by tests

    public AcceptingChannelMock() {
        
    }

    public void setLocalAddress(SocketAddress address) {
        localAddress = address;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    @Override
    public <A extends SocketAddress> A getLocalAddress(Class<A> type) {
        if (type.isAssignableFrom(localAddress.getClass())) {
            return type.cast(localAddress);
        }
        return null;
    }

    private XnioWorker worker = null;

    @Override
    public XnioWorker getWorker() {
        return worker;
    }
    
    public void setWorker(XnioWorker worker) {
        this.worker = worker;
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }
    
    private Option<?>[] supportedOptions;

    @Override
    public boolean supportsOption(Option<?> option) {
        final Set<Option<?>> supported = new HashSet<Option<?>>(Arrays.asList(supportedOptions));
        return supported.contains(option);
    }

    public void setSupportedOptions(Option<?> ... options) {
        supportedOptions = options;
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return optionMap.get(option);
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        Builder optionMapBuilder = OptionMap.builder();
        optionMapBuilder.addAll(optionMap);
        optionMapBuilder.set(option, value);
        T oldValue = optionMap.get(option);
        optionMap = optionMapBuilder.getMap();
        return oldValue;
    }

    @Override
    public OptionMap getOptionMap() {
        return optionMap;
    }

    public void setOptionMap(OptionMap optionMap) {
        this.optionMap = optionMap;
    }

    @Override
    public void suspendAccepts() {
        acceptsResumed = acceptsWokenUp = false;
    }

    @Override
    public void resumeAccepts() {
        acceptsResumed = true;
    }

    public boolean isAcceptResumed() {
        return acceptsResumed;
    }

    @Override
    public void wakeupAccepts() {
        acceptsResumed = acceptsWokenUp = true;
    }

    public boolean isAcceptWokenUp() {
        return acceptsWokenUp;
    }

    boolean waitedAcceptable = false;
    long awaitAcceptableTime;
    TimeUnit awaitAcceptableTimeUnit;

    public void clearWaitedAcceptable() {
        waitedAcceptable = false;
    }

    public boolean haveWaitedAcceptable() {
        return waitedAcceptable;
    }

    public long getAwaitAcceptableTime() {
        return awaitAcceptableTime;
    }

    public TimeUnit getAwaitAcceptableTimeUnit() {
        return awaitAcceptableTimeUnit;
    }

    @Override
    public void awaitAcceptable() throws IOException {
        waitedAcceptable = true;
    }

    @Override
    public void awaitAcceptable(long time, TimeUnit timeUnit) throws IOException {
        waitedAcceptable = true;
        awaitAcceptableTime = time;
        awaitAcceptableTimeUnit = timeUnit;
    }

    @Override
    public ConnectedStreamChannelMock accept() throws IOException {
        if (acceptanceEnabled) {
            //System.out.println("Acceptance enabled... returning accepted channel");
            ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
            channel.setPeerAddress(new InetSocketAddress(42630));
            return channel;
        } else {
            //System.out.println("Acceptance NOT enabled");
        }
        return null;
    }

    @Override
    public Setter<? extends AcceptingChannel<ConnectedStreamChannelMock>> getAcceptSetter() {
        return acceptSetter;
    }

    @Override
    public Setter<? extends AcceptingChannel<ConnectedStreamChannelMock>> getCloseSetter() {
        return closeSetter;
    }
    
    public ChannelListener<? super AcceptingChannelMock> getAcceptListener() {
        return acceptSetter.get();
    }

    public void enableAcceptance(boolean enable) {
        acceptanceEnabled = enable;
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
