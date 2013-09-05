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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.xnio._private.Messages.msg;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class SequenceOption<T> extends Option<Sequence<T>> {

    private static final long serialVersionUID = -4328676629293125136L;

    private final transient Class<T> elementType;
    private final transient ValueParser<T> parser;

    SequenceOption(final Class<?> declClass, final String name, final Class<T> elementType) {
        super(declClass, name);
        if (elementType == null) {
            throw msg.nullParameter("elementType");
        }
        this.elementType = elementType;
        parser = Option.getParser(elementType);
    }

    public Sequence<T> cast(final Object o) {
        if (o == null) {
            return null;
        } else if (o instanceof Sequence) {
            return ((Sequence<?>)o).cast(elementType);
        } else if (o instanceof Object[]){
            return Sequence.of((Object[])o).cast(elementType);
        } else if (o instanceof Collection) {
            return Sequence.of((Collection<?>)o).cast(elementType);
        } else {
            throw new ClassCastException("Not a sequence");
        }
    }

    public Sequence<T> parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
        final List<T> list = new ArrayList<T>();
        if (string.isEmpty()) {
            return Sequence.empty();
        }
        for (String value : string.split(",")) {
            list.add(parser.parseValue(value, classLoader));
        }
        return Sequence.of(list);
    }
}
