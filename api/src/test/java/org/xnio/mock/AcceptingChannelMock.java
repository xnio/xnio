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
