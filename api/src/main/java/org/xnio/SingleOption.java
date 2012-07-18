/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2011 Red Hat, Inc. and/or its affiliates, and individual
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
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class SingleOption<T> extends Option<T> {

    private static final long serialVersionUID = 2449094406108952764L;

    private final transient Class<T> type;
    private final transient ValueParser<T> parser;

    SingleOption(final Class<?> declClass, final String name, final Class<T> type) {
        super(declClass, name);
        if (type == null) {
            throw new IllegalArgumentException("type is null");
        }
        this.type = type;
        parser = Option.getParser(type);
    }

    public T cast(final Object o) {
        return type.cast(o);
    }

    public T parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
        return parser.parseValue(string, classLoader);
    }
}
