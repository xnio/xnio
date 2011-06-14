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

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class TypeOption<T> extends Option<Class<? extends T>> {

    private static final long serialVersionUID = 2449094406108952764L;

    private final transient Class<T> type;
    private final transient ValueParser<Class<? extends T>> parser;

    TypeOption(final Class<?> declClass, final String name, final Class<T> type) {
        super(declClass, name);
        this.type = type;
        parser = Option.getClassParser(type);
    }

    public Class<? extends T> cast(final Object o) {
        return ((Class<?>) o).asSubclass(type);
    }

    public Class<? extends T> parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
        return (Class<? extends T>) parser.parseValue(string, classLoader);
    }
}
