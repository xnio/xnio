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

package org.xnio.sasl;

import static org.xnio._private.Messages.msg;

/**
 * The SASL cipher strength value.
 *
 * @see javax.security.sasl.Sasl#STRENGTH
 */
public enum SaslStrength {

    /**
     * Specify low cipher strength.
     */
    LOW("low"),
    /**
     * Specify medium cipher strength.
     */
    MEDIUM("medium"),
    /**
     * Specify high cipher strength.
     */
    HIGH("high"),
    ;

    private final String toString;

    SaslStrength(String toString) {
        this.toString = toString;
    }

    /**
     * Get the SASL Strength level for the given string.
     *
     * @param name the Strength level
     * @return the Strength value
     */
    public static SaslStrength fromString(String name) {
        if ("low".equals(name)) {
            return LOW;
        } else if ("medium".equals(name)) {
            return MEDIUM;
        } else if ("high".equals(name)) {
            return HIGH;
        } else {
            throw msg.invalidStrength(name);
        }
    }

    /**
     * Returns the human-readable reprentation of this Strength value.
     *
     * @return the string representation
     */
    public String toString() {
        return toString;
    }
}
