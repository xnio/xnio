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

import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.CloseableChannel;

abstract class AbstractNioChannel<C extends AbstractNioChannel<C>> implements CloseableChannel {

    // default buffer size used by subclasses
    protected static final int DEFAULT_BUFFER_SIZE = 0x10000;

    private final ChannelListener.SimpleSetter<C> closeSetter = new ChannelListener.SimpleSetter<C>();

    protected final NioXnioWorker worker;

    AbstractNioChannel(final NioXnioWorker worker) {
        this.worker = worker;
    }

    public final XnioWorker getWorker() {
        return worker;
    }

    public final ChannelListener.Setter<? extends C> getCloseSetter() {
        return closeSetter;
    }

    public XnioIoThread getIoThread() {
        return worker.chooseThread();
    }

    @SuppressWarnings("unchecked")
    protected final C typed() {
        return (C) this;
    }

    protected final void invokeCloseHandler() {
        ChannelListeners.invokeChannelListener(typed(), closeSetter.get());
    }
}
