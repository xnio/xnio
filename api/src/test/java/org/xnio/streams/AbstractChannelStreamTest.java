/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.xnio.streams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 * Abstract test for channel streams.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public abstract class AbstractChannelStreamTest <T extends Closeable> {

    /**
     * Returns the operation (it could be read or write) timeout of {@code stream}.
     * 
     * @param stream    the channel input or output stream
     * @param timeUnit  the timeout unit
     * @return          the read or write timeout of {@code stream}
     */
    protected abstract long getOperationTimeout(T stream, TimeUnit timeUnit);

    /**
     * Sets the read or write timeout for {@code stream}.
     * 
     * @param stream   the channel input or output stream
     * @param timeout  the timeout
     * @param timeUnit the timeout unit
     */
    protected abstract void setOperationTimeout(T stream, int timeout, TimeUnit timeUnit);

    /**
     * Creates the channel input or output stream with operation timeout enabled.
     * 
     * @param timeout             the operation timeout
     * @param timeUnit            the operation timeout unit
     * @param internalBufferSize  the size of the stream's internal buffer size, if applicable
     * @return                    the created channel stream
     */
    protected abstract T createChannelStream(long timeout, TimeUnit timeUnit);

    @Test
    public void setOperationTimeout() {
        // create stream
        final T stream = createChannelStream(0, TimeUnit.SECONDS);
        assertEquals(0, getOperationTimeout(stream, TimeUnit.MICROSECONDS));
        // try to set read timeout -1
        Exception setOperationTimeoutException = null;
        try {
            setOperationTimeout(stream, -1, TimeUnit.HOURS);
        } catch (IllegalArgumentException e) {
            setOperationTimeoutException = e;
        }
        assertNotNull(setOperationTimeoutException);
        // try to set read timeout with null timeunit
        setOperationTimeoutException = null;
        try {
            setOperationTimeout(stream, 5, null);
        } catch (NullPointerException e) {
            setOperationTimeoutException = e;
        }
        assertNotNull(setOperationTimeoutException);
        // try to get read timeout with null timeunit
        Exception getOperationTimeoutException = null;
        try {
            getOperationTimeout(stream, null);
        } catch (NullPointerException e) {
            getOperationTimeoutException = e;
        }
        assertNotNull(getOperationTimeoutException);
        // set timeout to 1 microsecond
        setOperationTimeout(stream, 1, TimeUnit.MICROSECONDS);
        assertEquals(0, getOperationTimeout(stream, TimeUnit.MILLISECONDS));
        assertEquals(1000, getOperationTimeout(stream, TimeUnit.NANOSECONDS)); // timeout is not rounded up
        assertEquals(0, getOperationTimeout(stream, TimeUnit.SECONDS));
        // set timeout to 0 milliseconds
        setOperationTimeout(stream, 0, TimeUnit.MILLISECONDS);
        assertEquals(0, getOperationTimeout(stream, TimeUnit.MILLISECONDS));
        assertEquals(0, getOperationTimeout(stream, TimeUnit.MICROSECONDS));
        // set timeout to 10 minutes
        setOperationTimeout(stream, 10, TimeUnit.MINUTES);
        assertEquals(10, getOperationTimeout(stream, TimeUnit.MINUTES));
        assertEquals(600, getOperationTimeout(stream, TimeUnit.SECONDS));
        assertEquals(600000, getOperationTimeout(stream, TimeUnit.MILLISECONDS));
        assertEquals(0, getOperationTimeout(stream, TimeUnit.HOURS));
    }
}
