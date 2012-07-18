/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2010 Red Hat, Inc. and/or its affiliates.
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

package org.xnio.channels;

import java.io.InterruptedIOException;

/**
 * Thrown when a blocking write operation times out.
 */
public class WriteTimeoutException extends InterruptedIOException {

    private static final long serialVersionUID = 2058056832934733469L;

    /**
     * Constructs a {@code WriteTimeoutException} with no detail message. The cause is not initialized, and may subsequently
     * be initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    public WriteTimeoutException() {
    }

    /**
     * Constructs a {@code WriteTimeoutException} with the specified detail message. The cause is not initialized, and may
     * subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param msg the detail message
     */
    public WriteTimeoutException(final String msg) {
        super(msg);
    }

    /**
     * Constructs a {@code WriteTimeoutException} with the specified cause. The detail message is set to:
     * <pre>(cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of {@code cause}).
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public WriteTimeoutException(final Throwable cause) {
        initCause(cause);
    }

    /**
     * Constructs a {@code WriteTimeoutException} with the specified detail message and cause.
     *
     * @param msg the detail message
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public WriteTimeoutException(final String msg, final Throwable cause) {
        super(msg);
        initCause(cause);
    }
}
