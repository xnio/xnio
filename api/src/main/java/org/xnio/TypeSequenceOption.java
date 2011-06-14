/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.xnio;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class TypeSequenceOption<T> extends Option<Sequence<Class<? extends T>>> {

    private static final long serialVersionUID = -4328676629293125136L;

    private final transient Class<T> elementDeclType;
    private final transient ValueParser<Class<? extends T>> parser;

    TypeSequenceOption(final Class<?> declClass, final String name, final Class<T> elementDeclType) {
        super(declClass, name);
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
        for (String value : string.split(",")) {
            list.add(parser.parseValue(value, classLoader));
        }
        return Sequence.of(list);
    }
}
