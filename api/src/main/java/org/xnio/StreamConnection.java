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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

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
 * @author Flavia Rainone
 */
public abstract class StreamConnection extends Connection implements CloseListenerSettable<StreamConnection> {

    /**
     * An empty listener used as a flag, to indicate that close listener has been invoked.
     */
    private static final ChannelListener<? super StreamConnection> INVOKED_CLOSE_LISTENER_FLAG = (StreamConnection connection)->{};

    private ConduitStreamSourceChannel sourceChannel;
    private ConduitStreamSinkChannel sinkChannel;
    private AtomicReference<ChannelListener<? super StreamConnection>> closeListener;

    /**
     * Construct a new instance.
     *
     * @param thread the I/O thread
     */
    protected StreamConnection(final XnioIoThread thread) {
        super(thread);
        closeListener = new AtomicReference<>();
    }

    public void setCloseListener(final ChannelListener<? super StreamConnection> listener) {
        ChannelListener<? super StreamConnection> currentListener;
        ChannelListener<? super StreamConnection> newListener;
        do {
            newListener = listener;
            currentListener = closeListener.get();
            if (currentListener != null) {
                // channel is closed, just invoke the new listener and do not update closeListener
                if (currentListener == INVOKED_CLOSE_LISTENER_FLAG) {
                    ChannelListeners.invokeChannelListener(this, listener);
                    return;
                } else {
                    newListener = mergeListeners(currentListener, listener);
                }
            }
        } while (!closeListener.compareAndSet(currentListener, newListener));
    }

    private final ChannelListener<? super StreamConnection> mergeListeners(final ChannelListener<? super StreamConnection> listener1, final ChannelListener<? super StreamConnection> listener2) {
        return (StreamConnection channel) -> {
            listener1.handleEvent(channel);
            listener2.handleEvent(channel);
        };
    }

    @Override protected void notifyReadClosed() {

        try {
            this.getSourceChannel().shutdownReads();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override protected void notifyWriteClosed() {
        try {
            this.getSinkChannel().shutdownWrites();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ChannelListener<? super StreamConnection> getCloseListener() {
        return closeListener.get();
    }

    public ChannelListener.Setter<? extends StreamConnection> getCloseSetter() {
        return new Setter<>(this);
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
        // use a flag to indicate that closeListener has been invoked
        final ChannelListener<? super StreamConnection> listener = closeListener.getAndSet(INVOKED_CLOSE_LISTENER_FLAG);
        ChannelListeners.invokeChannelListener(this, listener);
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
