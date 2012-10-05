/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
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

import java.security.PrivilegedAction;

/**
 * A simple property-read privileged action.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ReadPropertyAction implements PrivilegedAction<String> {
    private final String propertyName;
    private final String defaultValue;

    /**
     * Construct a new instance.
     *
     * @param propertyName the property name
     * @param defaultValue the default value
     */
    public ReadPropertyAction(final String propertyName, final String defaultValue) {
        this.propertyName = propertyName;
        this.defaultValue = defaultValue;
    }

    /**
     * Run the action.
     *
     * @return the property or default value
     */
    public String run() {
        return System.getProperty(propertyName, defaultValue);
    }
}
