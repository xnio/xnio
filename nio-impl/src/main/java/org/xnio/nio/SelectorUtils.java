/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

import static org.xnio.nio.Log.log;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.TimeUnit;

import org.xnio.Xnio;

final class SelectorUtils {
    private SelectorUtils() {
    }

    public static void await(NioXnio nioXnio, SelectableChannel channel, int op) throws IOException {
        if (NioXnio.IS_HP_UX) {
            // HP-UX has buggy write wakeup semantics
            await(nioXnio, channel, op, 1, TimeUnit.SECONDS);
            return;
        }
        Xnio.checkBlockingAllowed();
        final Selector selector = nioXnio.getSelector();
        final SelectionKey selectionKey;
        try {
            selectionKey = channel.register(selector, op);
        } catch (ClosedChannelException e) {
            return;
        }
        selector.select();
        selector.selectedKeys().clear();
        if (Thread.currentThread().isInterrupted()) {
            throw log.interruptedIO();
        }
        selectionKey.cancel();
        selector.selectNow();
    }

    public static void await(NioXnio nioXnio, SelectableChannel channel, int op, long time, TimeUnit unit) throws IOException {
        if (time <= 0) {
            await(nioXnio, channel, op);
            return;
        }
        Xnio.checkBlockingAllowed();
        final Selector selector = nioXnio.getSelector();
        final SelectionKey selectionKey;
        try {
            selectionKey = channel.register(selector, op);
        } catch (ClosedChannelException e) {
            return;
        }
        long timeoutInMillis = unit.toMillis(time);
        selector.select(timeoutInMillis == 0 ? 1: timeoutInMillis);
        selector.selectedKeys().clear();
        if (Thread.currentThread().isInterrupted()) {
            throw log.interruptedIO();
        }
        selectionKey.cancel();
        selector.selectNow();
    }
}
