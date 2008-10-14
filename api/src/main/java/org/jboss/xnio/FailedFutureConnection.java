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

import java.io.IOException;

/**
 * An implementation of {@link FutureConnection} that represents an immediately-failed operation.
 *
 * @param <A> the address type
 * @param <T> the type of result that this operation produces
 */
public class FailedFutureConnection<A, T> extends FailedIoFuture<T> implements FutureConnection<A, T> {
    private final A localAddress;

    /**
     * Create an instance.
     *
     * @param e the failure cause
     * @param address the address to return
     */
    public FailedFutureConnection(final IOException e, final A address) {
        super(e);
        localAddress = address;
    }

    /** {@inheritDoc} */
    public A getLocalAddress() {
        return localAddress;
    }

    /** {@inheritDoc} */
    public FutureConnection<A, T> cancel() {
        return this;
    }
}
