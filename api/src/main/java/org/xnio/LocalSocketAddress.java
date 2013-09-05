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

import java.net.SocketAddress;

import static org.xnio._private.Messages.msg;

/**
 * A socket address which is a local (UNIX domain) socket.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class LocalSocketAddress extends SocketAddress {

    private static final long serialVersionUID = -596342428809783686L;

    private final String name;

    /**
     * Construct a new instance.
     *
     * @param name the name of this socket address
     */
    public LocalSocketAddress(final String name) {
        if (name == null) {
            throw msg.nullParameter("name");
        }
        this.name = name;
    }

    /**
     * Get the name (filesystem path) of this local socket address.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the string representation of this socket address (its name).
     *
     * @return the string representation
     */
    public String toString() {
        return getName();
    }
}
