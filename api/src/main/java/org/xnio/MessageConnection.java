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

package org.xnio;

import org.xnio.channels.CloseListenerSettable;
import org.xnio.conduits.ConduitReadableMessageChannel;
import org.xnio.conduits.ConduitWritableMessageChannel;
import org.xnio.conduits.MessageSinkConduit;
import org.xnio.conduits.MessageSourceConduit;

import static org.xnio._private.Messages.msg;

/**
 * A message-oriented connection between peers.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class MessageConnection extends Connection implements CloseListenerSettable<MessageConnection> {

    private ConduitReadableMessageChannel sourceChannel;
    private ConduitWritableMessageChannel sinkChannel;
    private ChannelListener<? super MessageConnection> closeListener;

    /**
     * Construct a new instance.
     *
     * @param thread the I/O thread
     */
    protected MessageConnection(final XnioIoThread thread) {
        super(thread);
    }

    public void setCloseListener(final ChannelListener<? super MessageConnection> listener) {
        this.closeListener = listener;
    }

    public ChannelListener<? super MessageConnection> getCloseListener() {
        return closeListener;
    }

    public ChannelListener.Setter<MessageConnection> getCloseSetter() {
        return new Setter<MessageConnection>(this);
    }

    protected void setSourceConduit(MessageSourceConduit conduit) {
        this.sourceChannel = conduit == null ? null : new ConduitReadableMessageChannel(this, conduit);
    }

    protected void setSinkConduit(MessageSinkConduit conduit) {
        this.sinkChannel = conduit == null ? null : new ConduitWritableMessageChannel(this, conduit);
    }

    void invokeCloseListener() {
        ChannelListeners.invokeChannelListener(this, closeListener);
    }

    private static <T> T notNull(T orig) throws IllegalStateException {
        if (orig == null) {
            throw msg.channelNotAvailable();
        }
        return orig;
    }

    /**
     * Get the source channel.
     *
     * @return the source channel
     */
    public ConduitReadableMessageChannel getSourceChannel() {
        return notNull(sourceChannel);
    }

    /**
     * Get the sink channel.
     *
     * @return the sink channel
     */
    public ConduitWritableMessageChannel getSinkChannel() {
        return notNull(sinkChannel);
    }
}
