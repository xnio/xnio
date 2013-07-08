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

package org.xnio;

/**
 * Possible file access modes.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public enum FileAccess {
    READ_ONLY(true, false),
    READ_WRITE(true, true),
    WRITE_ONLY(false, true),
    ;
    private final boolean read;
    private final boolean write;

    private FileAccess(final boolean read, final boolean write) {
        this.read = read;
        this.write = write;
    }

    boolean isRead() {
        return read;
    }

    boolean isWrite() {
        return write;
    }
}
