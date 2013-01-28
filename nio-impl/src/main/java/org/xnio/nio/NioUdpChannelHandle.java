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

package org.xnio.nio;

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import org.xnio.Bits;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class NioUdpChannelHandle extends NioHandle {
    private final NioUdpChannel channel;

    NioUdpChannelHandle(final WorkerThread workerThread, final SelectionKey selectionKey, final NioUdpChannel channel) {
        super(workerThread, selectionKey);
        this.channel = channel;
    }

    void handleReady(int ops) {
        try {
            if (ops == 0) {
                // the dreaded bug
                final SelectionKey key = getSelectionKey();
                final int interestOps = key.interestOps();
                if (interestOps != 0) {
                    ops = interestOps;
                } else {
                    // urp
                    forceTermination();
                    return;
                }
            }
            if (Bits.allAreSet(ops, SelectionKey.OP_READ)) try {
                ChannelListeners.invokeChannelListener(channel, channel.getReadListener());
            } catch (Throwable ignored) {
            }
            if (Bits.allAreSet(ops, SelectionKey.OP_WRITE)) try {
                ChannelListeners.invokeChannelListener(channel, channel.getWriteListener());
            } catch (Throwable ignored) {
            }
        } catch (CancelledKeyException ignored) {}
    }

    void forceTermination() {
        IoUtils.safeClose(channel);
    }

    void terminated() {
        channel.invokeCloseHandler();
    }
}
