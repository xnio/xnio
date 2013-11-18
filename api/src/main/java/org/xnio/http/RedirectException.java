/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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

import java.io.IOException;

/**
 * An extension of {@link IOException} used to convey that a connection has failed as a redirect has been encountered.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class RedirectException extends IOException {

    private final int statusCode;
    private final String location;

    /**
     * Constructs a new {@code RedirectException} instance.  The message is left blank ({@code null}), and no cause is
     * specified.
     *
     * @param statusCode the status code
     * @param location the redirection location, if any
     */
    public RedirectException(final int statusCode, final String location) {
        this.statusCode = statusCode;
        this.location = location;
    }

    /**
     * Constructs a new {@code RedirectException} instance with an initial message.  No cause is specified.
     *
     * @param msg the message
     * @param statusCode the status code
     * @param location the redirection location, if any
     */
    public RedirectException(final String msg, final int statusCode, final String location) {
        super(msg);
        this.statusCode = statusCode;
        this.location = location;
    }

    /**
     * Constructs a new {@code RedirectException} instance with an initial cause.  If a non-{@code null} cause is
     * specified, its message is used to initialize the message of this {@code RedirectException}; otherwise the message
     * is left blank ({@code null}).
     *
     * @param cause the cause
     * @param statusCode the status code
     * @param location the redirection location, if any
     */
    public RedirectException(final Throwable cause, final int statusCode, final String location) {
        super(cause);
        this.statusCode = statusCode;
        this.location = location;
    }

    /**
     * Constructs a new {@code RedirectException} instance with an initial message and cause.
     *
     * @param msg the message
     * @param cause the cause
     * @param statusCode the status code
     * @param location the redirection location, if any
     */
    public RedirectException(final String msg, final Throwable cause, final int statusCode, final String location) {
        super(msg, cause);
        this.statusCode = statusCode;
        this.location = location;
    }

    /**
     * Get the HTTP status code.  This is the reason for the redirect.
     *
     * @return the status code
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Get the redirection target location.
     *
     * @return the redirection target location
     */
    public String getLocation() {
        return location;
    }

}
