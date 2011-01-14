/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

package org.xnio;

import java.util.HashSet;
import java.util.Set;
import org.jboss.logging.Logger;

/**
 * A base channel thread implementation which handles listener notification.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractChannelThread implements ChannelThread {

    private static final Logger listenerLog = Logger.getLogger("org.xnio.listener");

    private static final int UP = 0;
    private static final int STOPPING = 1;
    private static final int DOWN = 2;

    private volatile int state = UP;

    private final Set<Listener> listenerSet = new HashSet<Listener>();

    /** {@inheritDoc} */
    public final void shutdown() {
        final Set<Listener> listenerSet = this.listenerSet;
        final Listener[] listeners;
        synchronized (listenerSet) {
            if (state == UP) {
                state = STOPPING;
                listeners = listenerSet.toArray(new Listener[listenerSet.size()]);
            } else {
                return;
            }
        }
        for (Listener listener : listeners) {
            doHandleTerminationInitiated(listener);
        }
        startShutdown();
    }

    private void doHandleTerminationInitiated(final Listener listener) {
        try {
            listener.handleTerminationInitiated(this);
        } catch (Throwable t) {
            logFailure(t);
        }
    }

    /**
     * Check whether the current state permits adding a channel.  Should be called under lock (see {@link #getLock()}).
     *
     * @throws IllegalStateException if the thread is shutting down
     */
    protected final void checkState() throws IllegalStateException {
        if (state != UP) {
            throw new IllegalStateException(String.format("Cannot add channel to %s (stopping)", this));
        }
    }

    /**
     * Get the lock used to synchronize the state of this thread.
     *
     * @return the lock
     */
    protected final Object getLock() {
        return listenerSet;
    }

    /**
     * Determine if this thread is stopping or down.
     *
     * @return {@code true} if the thread is stopping or down
     */
    protected final boolean isStopping() {
        return state >= STOPPING;
    }

    /**
     * Called by this class when {@link #shutdown()} has been invoked after all listeners
     * have been notified.
     */
    protected abstract void startShutdown();

    /**
     * Call when the shutdown process is complete before exiting the thread.
     */
    protected final void shutdownFinished() {
        final Set<Listener> listenerSet = this.listenerSet;
        final Listener[] listeners;
        synchronized (listenerSet) {
            state = DOWN;
            listeners = listenerSet.toArray(new Listener[listenerSet.size()]);
            listenerSet.clear();
            listenerSet.notifyAll();
        }
        for (Listener listener : listeners) {
            doHandleTerminationComplete(listener);
        }
    }

    private void doHandleTerminationComplete(final Listener listener) {
        try {
            listener.handleTerminationComplete(this);
        } catch (Throwable t) {
            logFailure(t);
        }
    }

    private static void logFailure(final Throwable t) {
        listenerLog.error("Listener invocation failed", t);
    }

    /** {@inheritDoc} */
    public final void awaitTermination() throws InterruptedException {
        final Set<Listener> listenerSet = this.listenerSet;
        synchronized (listenerSet) {
            while (state != DOWN) {
                listenerSet.wait();
            }
        }
    }

    /** {@inheritDoc} */
    public final void addTerminationListener(final Listener listener) {
        final Set<Listener> listenerSet = this.listenerSet;
        final int state;
        synchronized (listenerSet) {
            state = this.state;
            switch (state) {
                case UP: listenerSet.add(listener); return;
                case STOPPING: listenerSet.add(listener); break;
                case DOWN: break;
                default: throw new IllegalStateException();
            }
        }
        doHandleTerminationInitiated(listener);
        if (state == DOWN) {
            doHandleTerminationComplete(listener);
        }
    }

    /** {@inheritDoc} */
    public final void removeTerminationListener(final Listener listener) {
        final Set<Listener> listenerSet = this.listenerSet;
        synchronized (listenerSet) {
            listenerSet.remove(listener);
        }
    }
}
