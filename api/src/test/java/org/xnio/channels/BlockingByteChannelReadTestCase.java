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

package org.xnio.channels;

import java.util.concurrent.TimeUnit;

import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * Test for {@link BlockingByteChannel} read-related operations.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class BlockingByteChannelReadTestCase extends AbstractBlockingReadableByteChannelTest<BlockingByteChannel> {
    @Override
    protected BlockingByteChannel createBlockingReadableByteChannel(ConnectedStreamChannelMock channelMock) {
        return new BlockingByteChannel(channelMock);
    }

    @Override
    protected BlockingByteChannel createBlockingReadableByteChannel(ConnectedStreamChannelMock channelMock, long timeout,
            TimeUnit timeoutUnit) {
        return new BlockingByteChannel(channelMock, timeout, timeoutUnit);
    }

    @Override
    protected BlockingByteChannel createBlockingReadableByteChannel(ConnectedStreamChannelMock channelMock, long readTimeout,
            TimeUnit readTimeoutUnit, long writeTimeout, TimeUnit writeTimeoutUnit) {
        return new BlockingByteChannel(channelMock, readTimeout, readTimeoutUnit, writeTimeout, writeTimeoutUnit);
    }

    @Override
    protected void setReadTimeout(BlockingByteChannel channel, long readTimeout, TimeUnit readTimeoutUnit) {
        channel.setReadTimeout(readTimeout, readTimeoutUnit);
    }
}
