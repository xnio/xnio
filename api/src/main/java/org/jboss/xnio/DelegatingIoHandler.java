/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.jboss.xnio;

import java.nio.channels.Channel;

/**
 * A base delegating I/O handler.  Implementors may separately set read and write handlers.  Ideal for state pattern
 * handlers for example.
 */
public abstract class DelegatingIoHandler<T extends Channel> implements IoHandler<T> {

    private volatile IoReadHandler<T> readHandler;
    private volatile IoWriteHandler<T> writeHandler;

    /**
     * Handles a readable notification by delegating it to the set read handler, if there is one; otherwise the
     * notification is ignored.
     *
     * @param channel the channel that is readable
     */
    public final void handleReadable(final T channel) {
        final IoReadHandler<T> readHandler = this.readHandler;
        if (readHandler != null) {
            readHandler.handleReadable(channel);
        }
    }

    /**
     * Handles a writable notification by delegating it to the set write handler, if there is one; otherwise the
     * notification is ignored.
     *
     * @param channel the channel that is writable
     */
    public final void handleWritable(final T channel) {
        final IoWriteHandler<T> writeHandler = this.writeHandler;
        if (writeHandler != null) {
            writeHandler.handleWritable(channel);
        }
    }

    /**
     * Get the read handler.
     *
     * @return the read handler
     */
    protected IoReadHandler<T> getReadHandler() {
        return readHandler;
    }

    /**
     * Set the read handler.
     *
     * @param readHandler the new read handler
     */
    protected void setReadHandler(final IoReadHandler<T> readHandler) {
        this.readHandler = readHandler;
    }

    /**
     * Get the write handler.
     *
     * @return the write handler
     */
    protected IoWriteHandler<T> getWriteHandler() {
        return writeHandler;
    }

    /**
     * Set the write handler.
     *
     * @param writeHandler the new write handler
     */
    protected void setWriteHandler(final IoWriteHandler<T> writeHandler) {
        this.writeHandler = writeHandler;
    }
}
