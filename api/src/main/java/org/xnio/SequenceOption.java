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
final class SequenceOption<T> extends Option<Sequence<T>> {

    private static final long serialVersionUID = -4328676629293125136L;

    private final transient Class<T> elementType;
    private final transient ValueParser<T> parser;

    SequenceOption(final Class<?> declClass, final String name, final Class<T> elementType) {
        super(declClass, name);
        if (elementType == null) {
            throw new IllegalArgumentException("elementType is null");
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
