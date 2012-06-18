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
