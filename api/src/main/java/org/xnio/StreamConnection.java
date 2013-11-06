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
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

import static org.xnio._private.Messages.msg;

/**
 * A connection between peers.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class StreamConnection extends Connection implements CloseListenerSettable<StreamConnection> {

    private ConduitStreamSourceChannel sourceChannel;
    private ConduitStreamSinkChannel sinkChannel;
    private ChannelListener<? super StreamConnection> closeListener;

    /**
     * Construct a new instance.
     *
     * @param thread the I/O thread
     */
    protected StreamConnection(final XnioIoThread thread) {
        super(thread);
    }

    public void setCloseListener(final ChannelListener<? super StreamConnection> listener) {
        this.closeListener = listener;
    }

    public ChannelListener<? super StreamConnection> getCloseListener() {
        return closeListener;
    }

    public ChannelListener.Setter<? extends StreamConnection> getCloseSetter() {
        return new Setter<StreamConnection>(this);
    }

    /**
     * Set the source conduit for this channel.  The source channel will automatically be updated.
     *
     * @param conduit the source conduit for this channel
     */
    protected void setSourceConduit(StreamSourceConduit conduit) {
        this.sourceChannel = conduit == null ? null : new ConduitStreamSourceChannel(this, conduit);
    }

    /**
     * Set the sink conduit for this channel.  The sink channel will automatically be updated.
     *
     * @param conduit the sink conduit for this channel
     */
    protected void setSinkConduit(StreamSinkConduit conduit) {
        this.sinkChannel = conduit == null ? null : new ConduitStreamSinkChannel(this, conduit);
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
    public ConduitStreamSourceChannel getSourceChannel() {
        return notNull(sourceChannel);
    }

    /**
     * Get the sink channel.
     *
     * @return the sink channel
     */
    public ConduitStreamSinkChannel getSinkChannel() {
        return notNull(sinkChannel);
    }
}
