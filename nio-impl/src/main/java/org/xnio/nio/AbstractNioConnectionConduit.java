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

import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import org.xnio.Connection;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
abstract class AbstractNioConnectionConduit<N extends AbstractSelectableChannel, C extends Connection> extends AbstractNioConduit<N> {
    private final C connection;

    protected AbstractNioConnectionConduit(final C connection, final SelectionKey selectionKey, final WorkerThread workerThread) {
        super(selectionKey, workerThread);
        assert connection.getWorker() == workerThread.getWorker();
        assert workerThread.getSelector() == selectionKey.selector();
        this.connection = connection;
    }

    abstract boolean tryClose();

    C getConnection() {
        return connection;
    }
}
