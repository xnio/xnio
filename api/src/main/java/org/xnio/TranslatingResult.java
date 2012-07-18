/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009 Red Hat, Inc. and/or its affiliates.
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
