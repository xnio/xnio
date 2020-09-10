/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
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
package org.xnio.http;

/**
 * Exception thrown if the connection is unexpectedly closed during http upgrade
 * before the response can be fully read.
 */
public class ConnectionClosedEarlyException extends UpgradeFailedException {
    private static final long serialVersionUID = -2954011903833115915L;

    /**
     * Constructs a new {@code ConnectionClosedEarlyException} instance.  The message is left blank ({@code null}), and no cause
     * is specified.
     */
    public ConnectionClosedEarlyException() {
    }

    /**
     * Constructs a new {@code ConnectionClosedEarlyException} instance with an initial message.  No cause is specified.
     *
     * @param msg the message
     */
    public ConnectionClosedEarlyException(String msg) {
        super(msg);
    }

    /**
     * Constructs a new {@code ConnectionClosedEarlyException} instance with an initial cause.  If a non-{@code null} cause is
     * specified, its message is used to initialize the message of this {@code UpgradeFailedException}; otherwise the
     * message is left blank ({@code null}).
     *
     * @param cause the cause
     */
    public ConnectionClosedEarlyException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code ConnectionClosedEarlyException} instance with an initial message and cause.
     *
     * @param msg the message
     * @param cause the cause
     */
    public ConnectionClosedEarlyException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
