/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

package org.jboss.xnio;

import java.io.Serializable;
import java.io.ObjectStreamException;
import java.io.InvalidObjectException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * A strongly-typed option to configure an aspect of a service or connection.  Options are immutable and use identity comparisons
 * and hash codes.  Options should always be declared as <code>public static final</code> members in order to support serialization.
 *
 * @param <T> the option value type
 */
public abstract class Option<T> implements Serializable {

    private static final long serialVersionUID = -1564427329140182760L;

    private final Class<?> declClass;
    private final String name;

    Option(final Class<?> declClass, final String name) {
        this.declClass = declClass;
        if (name == null) {
            throw new NullPointerException("name is null");
        }
        this.name = name;
    }

    /**
     * Create an option with a simple type.  The class object given <b>must</b> represent some immutable type, otherwise
     * unexpected behavior may result.
     *
     * @param declClass the declaring class of the option
     * @param name the (field) name of this option
     * @param type the class of the value associated with this option
     * @return the option instance
     */
    public static <T> Option<T> simple(final Class<?> declClass, final String name, final Class<T> type) {
        return new SingleOption<T>(declClass, name, type);
    }

    /**
     * Create an option with a sequence type.  The class object given <b>must</b> represent some immutable type, otherwise
     * unexpected behavior may result.
     *
     * @param declClass the declaring class of the option
     * @param name the (field) name of this option
     * @param elementType the class of the sequence element value associated with this option
     * @return the option instance
     */
    public static <T> Option<Sequence<T>> sequence(final Class<?> declClass, final String name, final Class<T> elementType) {
        return new SequenceOption<T>(declClass, name, elementType);
    }

    /**
     * Get the name of this option.
     *
     * @return the option name
     */
    public String getName() {
        return name;
    }

    /**
     * Get a human-readible string representation of this object.
     *
     * @return the string representation
     */
    public String toString() {
        return declClass.getName() + "." + name;
    }

    /**
     * Get an option from a string name, using the given classloader.  If the classloader is {@code null}, the bootstrap
     * classloader will be used.
     *
     * @param name the option string
     * @param classLoader the class loader
     * @return the option
     * @throws IllegalArgumentException if the given option name is not valid
     */
    public static Option<?> fromString(String name, ClassLoader classLoader) throws IllegalArgumentException {
        final int lastDot = name.lastIndexOf('.');
        if (lastDot == -1) {
            throw new IllegalArgumentException("Invalid option name");
        }
        final String fieldName = name.substring(lastDot + 1);
        final String className = name.substring(0, lastDot);
        try {
            final Field field = Class.forName(className, true, classLoader).getField(fieldName);
            final int modifiers = field.getModifiers();
            if (! Modifier.isPublic(modifiers)) {
                throw new IllegalArgumentException("Invalid Option instance (the field is not public)");
            }
            if (! Modifier.isStatic(modifiers)) {
                throw new IllegalArgumentException("Invalid Option instance (the field is not static)");
            }
            final Option<?> option = (Option<?>) field.get(null);
            if (option == null) {
                throw new IllegalArgumentException("Invalid null Option");
            }
            return option;
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("No such field");
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Illegal access", e);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class '" + className + "' not found");
        }
    }

    /**
     * Return the given object as the type of this option.  If the cast could not be completed, an exception is thrown.
     *
     * @param o the object to cast
     * @return the cast object
     * @throws ClassCastException if the object is not of a compatible type
     */
    public abstract T cast(Object o) throws ClassCastException;

    /**
     * Parse a string value for this option.
     *
     * @param string the string
     * @return the parsed value
     * @throws IllegalArgumentException if the argument could not be parsed
     */
    public abstract T parseValue(String string) throws IllegalArgumentException;

    /**
     * Resolve this instance for serialization.
     *
     * @return the resolved object
     * @throws java.io.ObjectStreamException if the object could not be resolved
     */
    protected final Object readResolve() throws ObjectStreamException {
        try {
            final Field field = declClass.getField(name);
            final int modifiers = field.getModifiers();
            if (! Modifier.isProtected(modifiers)) {
                throw new InvalidObjectException("Invalid Option instance (the field is not public)");
            }
            if (! Modifier.isStatic(modifiers)) {
                throw new InvalidObjectException("Invalid Option instance (the field is not static)");
            }
            final Option<?> option = (Option<?>) field.get(null);
            if (option == null) {
                throw new InvalidObjectException("Invalid null Option");
            }
            return option;
        } catch (NoSuchFieldException e) {
            throw new InvalidObjectException("Invalid Option instance (no matching field)");
        } catch (IllegalAccessException e) {
            throw new InvalidObjectException("Invalid Option instance (Illegal access on field get)");
        }
    }

    /**
     * Create a builder for an immutable option set.
     *
     * @return the builder
     */
    public static SetBuilder setBuilder() {
        return new SetBuilder();
    }

    /**
     * A builder for an immutable option set.
     */
    public static class SetBuilder {
        private List<Option<?>> optionSet = new ArrayList<Option<?>>();

        SetBuilder() {
        }

        /**
         * Add an option to this set.
         *
         * @param option the option to add
         * @return this builder
         */
        public SetBuilder add(Option<?> option) {
            if (option == null) {
                throw new NullPointerException("option is null");
            }
            optionSet.add(option);
            return this;
        }

        /**
         * Add all options from a collection to this set.
         *
         * @param options the options to add
         * @return this builder
         */
        public SetBuilder addAll(Collection<Option<?>> options) {
            if (options == null) {
                throw new NullPointerException("options is null");
            }
            for (Option<?> option : options) {
                add(option);
            }
            return this;
        }

        /**
         * Create the immutable option set instance.
         *
         * @return the option set
         */
        public Set<Option<?>> create() {
            return Collections.unmodifiableSet(new LinkedHashSet<Option<?>>(optionSet));
        }
    }

    interface ValueParser<T> {
        T parseValue(String string) throws IllegalArgumentException;
    }

    private static final Map<Class<?>, ValueParser<?>> parsers;

    private static final ValueParser<?> noParser = new ValueParser<Object>() {
        public Object parseValue(final String string) throws IllegalArgumentException {
            throw new IllegalArgumentException("No parser for this value type");
        }
    };

    static {
        final Map<Class<?>, ValueParser<?>> map = new HashMap<Class<?>, ValueParser<?>>();
        map.put(Byte.class, new ValueParser<Byte>() {
            public Byte parseValue(final String string) throws IllegalArgumentException {
                return Byte.decode(string.trim());
            }
        });
        map.put(Short.class, new ValueParser<Short>() {
            public Short parseValue(final String string) throws IllegalArgumentException {
                return Short.decode(string.trim());
            }
        });
        map.put(Integer.class, new ValueParser<Integer>() {
            public Integer parseValue(final String string) throws IllegalArgumentException {
                return Integer.decode(string.trim());
            }
        });
        map.put(Long.class, new ValueParser<Long>() {
            public Long parseValue(final String string) throws IllegalArgumentException {
                return Long.decode(string.trim());
            }
        });
        map.put(String.class, new ValueParser<String>() {
            public String parseValue(final String string) throws IllegalArgumentException {
                return string.trim();
            }
        });
        map.put(Boolean.class, new ValueParser<Boolean>() {
            public Boolean parseValue(final String string) throws IllegalArgumentException {
                return Boolean.valueOf(string.trim());
            }
        });
        parsers = map;
    }

    @SuppressWarnings({ "unchecked" })
    static <T> ValueParser<T> getParser(final Class<T> argType) {
        if (argType.isEnum()) {
            return new ValueParser<T>() {
                @SuppressWarnings({ "unchecked" })
                public T parseValue(final String string) throws IllegalArgumentException {
                    return (T) Enum.valueOf((Class)argType, string.trim());
                }
            };
        } else {
            final Option.ValueParser<?> value = parsers.get(argType);
            return (Option.ValueParser<T>) (value == null ? noParser : value);
        }
    }
}

final class SingleOption<T> extends Option<T> {

    private static final long serialVersionUID = 2449094406108952764L;

    private transient final Class<T> type;
    private transient final ValueParser<T> parser;

    SingleOption(final Class<?> declClass, final String name, final Class<T> type) {
        super(declClass, name);
        this.type = type;
        parser = Option.getParser(type);
    }

    public T cast(final Object o) {
        return type.cast(o);
    }

    public T parseValue(final String string) throws IllegalArgumentException {
        return parser.parseValue(string);
    }
}

final class SequenceOption<T> extends Option<Sequence<T>> {

    private static final long serialVersionUID = -4328676629293125136L;

    private transient final Class<T> elementType;
    private transient final ValueParser<T> parser;

    SequenceOption(final Class<?> declClass, final String name, final Class<T> elementType) {
        super(declClass, name);
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

    public Sequence<T> parseValue(final String string) throws IllegalArgumentException {
        final List<T> list = new ArrayList<T>();
        for (String value : string.split(",")) {
            list.add(parser.parseValue(value));
        }
        return Sequence.of(list);
    }
}
