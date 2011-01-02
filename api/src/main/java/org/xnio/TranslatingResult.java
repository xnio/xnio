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

package org.xnio;

import java.io.IOException;

/**
 * Abstract base class for {@code Result}s which translate from one type to another.
 *
 * @param <T> the result type to accept
 * @param <O> the result type to pass to the delegate
 */
public abstract class TranslatingResult<T, O> implements Result<T> {
    private final Result<O> output;

    protected TranslatingResult(final Result<O> output) {
        this.output = output;
    }

    public boolean setException(final IOException exception) {
        return output.setException(exception);
    }

    public boolean setCancelled() {
        return output.setCancelled();
    }

    public boolean setResult(final T result) {
        try {
            return output.setResult(translate(result));
        } catch (IOException e) {
            return output.setException(e);
        }
    }

    protected abstract O translate(T input) throws IOException;
}
