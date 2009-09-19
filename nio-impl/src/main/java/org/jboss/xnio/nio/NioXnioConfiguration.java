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

package org.jboss.xnio.nio;

import java.util.concurrent.ThreadFactory;
import org.jboss.xnio.XnioConfiguration;

/**
 * Configuration for the NIO XNIO provider.  The configuration items provided here are specific to the NIO provider.
 *
 * @since 1.2
 */
public final class NioXnioConfiguration extends XnioConfiguration {
    private int readSelectorThreads = 2;
    private int writeSelectorThreads = 1;
    private int connectSelectorThreads = 1;
    private int selectorCacheSize = 30;

    private ThreadFactory selectorThreadFactory;

    /**
     * Get the number of read selector threads.
     *
     * @return the number of read selector threads
     */
    public int getReadSelectorThreads() {
        return readSelectorThreads;
    }

    /**
     * Set the number of read selector threads.
     *
     * @param readSelectorThreads the number of read selector threads
     */
    public void setReadSelectorThreads(final int readSelectorThreads) {
        this.readSelectorThreads = readSelectorThreads;
    }

    /**
     * Get the number of write selector threads.
     *
     * @return the number of write selector threads
     */
    public int getWriteSelectorThreads() {
        return writeSelectorThreads;
    }

    /**
     * Set the number of read selector threads.
     *
     * @param writeSelectorThreads the number of write selector threads
     */
    public void setWriteSelectorThreads(final int writeSelectorThreads) {
        this.writeSelectorThreads = writeSelectorThreads;
    }

    /**
     * Get the number of connect selector threads.  These threads are used to handle connect and accept events.
     *
     * @return the number of connect selector threads
     */
    public int getConnectSelectorThreads() {
        return connectSelectorThreads;
    }

    /**
     * Set the number of connect selector threads.  These threads are used to handle connect and accept events.
     *
     * @param connectSelectorThreads the number of connect selector threads
     */
    public void setConnectSelectorThreads(final int connectSelectorThreads) {
        this.connectSelectorThreads = connectSelectorThreads;
    }

    /**
     * Get the thread factory to use for selector threads.
     *
     * @return the thread factory
     */
    public ThreadFactory getSelectorThreadFactory() {
        return selectorThreadFactory;
    }

    /**
     * Set the thread factory to use for selector threads.
     *
     * @param selectorThreadFactory the thread factory
     */
    public void setSelectorThreadFactory(final ThreadFactory selectorThreadFactory) {
        this.selectorThreadFactory = selectorThreadFactory;
    }

    /**
     * Get the size of the selector cache used for blocking I/O operations.
     *
     * @return the size of the selector cache
     */
    public int getSelectorCacheSize() {
        return selectorCacheSize;
    }

    /**
     * Set the size of the selector cache used for blocking I/O operations.
     *
     * @param selectorCacheSize the size of the selector cache
     */
    public void setSelectorCacheSize(final int selectorCacheSize) {
        this.selectorCacheSize = selectorCacheSize;
    }
}
