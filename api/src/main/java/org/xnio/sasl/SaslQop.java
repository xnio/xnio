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

import javax.security.sasl.Sasl;

/**
 * The SASL quality-of-protection value.
 *
 * @see Sasl#QOP
 */
public enum SaslQop {

    /**
     * A QOP value specifying authentication only.
     */
    AUTH("auth"),
    /**
     * A QOP value specifying authentication plus integrity protection.
     */
    AUTH_INT("auth-int"),
    /**
     * A QOP value specifying authentication plus integrity and confidentiality protection.
     */
    AUTH_CONF("auth-conf"),
    ;

    private final String s;

    SaslQop(String s) {
        this.s = s;
    }

    /**
     * Get the SASL QOP level for the given string.
     *
     * @param name the QOP level
     * @return the QOP value
     */
    public static SaslQop fromString(String name) {
        if ("auth".equals(name)) {
            return AUTH;
        } else if ("auth-int".equals(name)) {
            return AUTH_INT;
        } else if ("auth-conf".equals(name)) {
            return AUTH_CONF;
        } else {
            throw msg.invalidQop(name);
        }
    }

    /**
     * Get the string representation of this SASL QOP value.
     *
     * @return the string representation
     */
    public String getString() {
        return s;
    }

    /**
     * Get the human-readable string representation of this SASL QOP value.
     *
     * @return the string representation
     */
    public String toString() {
        return s;
    }
}
