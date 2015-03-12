/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2010 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
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

/**
 * A resource which is pooled.
 *
 * @param <T> the pooled resource type
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 *
 * @deprecated See {@link ByteBufferPool}.
 */
public interface Pooled<T> extends AutoCloseable {

    /**
     * Discard this resource.  Any backing resources corresponding to this pooled resource will be rendered unavailable
     * until the pooled resource has been garbage-collected.
     */
    void discard();

    /**
     * Free this resource for immediate re-use.  The resource must not be accessed again after
     * calling this method; if it is possible that an instance is still in use, you must call {@link #discard()} instead.
     */
    void free();

    /**
     * Get the pooled resource.
     *
     * @return the pooled resource
     * @throws IllegalStateException if the resource has been freed or discarded already
     */
    T getResource() throws IllegalStateException;

    /**
     * Delegates to {@link #free()}.
     */
    void close();
}
