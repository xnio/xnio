/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

/**
 * A {@code FutureConnection} implementation that wraps a different type of {@code FutureConnection}.  Used to create general wrappers
 * that convert one channel type to another.
 *
 * @param <A> the address type
 * @param <T> the type of this future result
 * @param <D> the type of the delegate result
 */
public abstract class AbstractConvertingFutureConnection<A, T, D> extends AbstractConvertingIoFuture<T, D> implements FutureConnection<A, T> {

    protected AbstractConvertingFutureConnection(final FutureConnection<A, ? extends D> delegate) {
        super(delegate);
    }

    public FutureConnection<A, T> cancel() {
        super.cancel();
        return this;
    }

    protected FutureConnection<A, ? extends D> getDelegate() {
        return (FutureConnection<A, ? extends D>) super.getDelegate();
    }

    public A getLocalAddress() {
        return getDelegate().getLocalAddress();
    }
}
