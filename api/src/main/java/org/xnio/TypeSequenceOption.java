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
final class TypeSequenceOption<T> extends Option<Sequence<Class<? extends T>>> {

    private static final long serialVersionUID = -4328676629293125136L;

    private final transient Class<T> elementDeclType;
    private final transient ValueParser<Class<? extends T>> parser;

    TypeSequenceOption(final Class<?> declClass, final String name, final Class<T> elementDeclType) {
        super(declClass, name);
        if (elementDeclType == null) {
            throw msg.nullParameter("elementDeclType");
        }
        this.elementDeclType = elementDeclType;
        parser = Option.getClassParser(elementDeclType);
    }

    public Sequence<Class<? extends T>> cast(final Object o) {
        if (o == null) {
            return null;
        } else if (o instanceof Sequence) {
            return castSeq((Sequence<?>) o, elementDeclType);
        } else if (o instanceof Object[]){
            return castSeq(Sequence.of((Object[]) o), elementDeclType);
        } else if (o instanceof Collection) {
            return castSeq(Sequence.of((Collection<?>) o), elementDeclType);
        } else {
            throw new ClassCastException("Not a sequence");
        }
    }

    @SuppressWarnings("unchecked")
    static <T> Sequence<Class<? extends T>> castSeq(Sequence<?> seq, Class<T> type) {
        for (Object o : seq) {
            ((Class<?>)o).asSubclass(type);
        }
        return (Sequence<Class<? extends T>>) seq;
    }

    public Sequence<Class<? extends T>> parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
        final List<Class<? extends T>> list = new ArrayList<Class<? extends T>>();
        if (string.isEmpty()) {
            return Sequence.empty();
        }
        for (String value : string.split(",")) {
            list.add(parser.parseValue(value, classLoader));
        }
        return Sequence.of(list);
    }
}
