/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2011 Red Hat, Inc. and/or its affiliates, and individual
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

import java.io.IOException;
import org.xnio.ChannelListener;

/**
 * A channel which can accept connections.
 *
 * @param <C> the channel type
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface SimpleAcceptingChannel<C extends CloseableChannel> extends SuspendableAcceptChannel {

    /**
     * Attempt to accept a connection.
     *
     * @return the new connection, or {@code null} if none is available
     * @throws IOException if an I/O error occurs
     */
    C accept() throws IOException;

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends SimpleAcceptingChannel<C>> getAcceptSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends SimpleAcceptingChannel<C>> getCloseSetter();
}
