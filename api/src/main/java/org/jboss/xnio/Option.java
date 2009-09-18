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
     * Create an option with a flag set type.  The class object given <b>must</b> represent some immutable type, otherwise
     * unexpected behavior may result.
     *
     * @param declClass the declaring class of the option
     * @param name the (field) name of this option
     * @param elementType the class of the flag values associated with this option
     * @param <T> the type of the flag values associated with this option
     * @return the option instance
     */
    public static <T extends Enum<T>> Option<FlagSet<T>> flags(final Class<?> declClass, final String name, final Class<T> elementType) {
        return new FlagsOption<T>(declClass, name, elementType);
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
        return super.toString() + " (" + declClass.getName() + "#" + name + ")";
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
            return field.get(null);
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
        private List<Option> optionSet = new ArrayList<Option>();

        SetBuilder() {
        }

        /**
         * Add an option to this set.
         *
         * @param option the option to add
         * @return this builder
         */
        public SetBuilder add(Option option) {
            if (option == null) {
                throw new NullPointerException("option is null");
            }
            optionSet.add(option);
            return this;
        }

        /**
         * Create the immutable option set instance.
         *
         * @return the option set
         */
        public Set<Option> create() {
            return Collections.unmodifiableSet(new LinkedHashSet<Option>(optionSet));
        }
    }
}

final class SingleOption<T> extends Option<T> {

    private static final long serialVersionUID = 2449094406108952764L;

    private transient final Class<T> type;

    SingleOption(final Class<?> declClass, final String name, final Class<T> type) {
        super(declClass, name);
        this.type = type;
    }

    public T cast(final Object o) {
        return type.cast(o);
    }
}

final class FlagsOption<T extends Enum<T>> extends Option<FlagSet<T>> {

    private static final long serialVersionUID = -5487268452958691541L;

    private transient final Class<T> elementType;

    FlagsOption(final Class<?> declClass, final String name, final Class<T> elementType) {
        super(declClass, name);
        this.elementType = elementType;
    }

    public FlagSet<T> cast(final Object o) throws ClassCastException {
        final FlagSet<?> flagSet = (FlagSet<?>) o;
        return flagSet.cast(elementType);
    }
}

final class SequenceOption<T> extends Option<Sequence<T>> {

    private static final long serialVersionUID = -4328676629293125136L;

    private transient final Class<T> elementType;

    SequenceOption(final Class<?> declClass, final String name, final Class<T> elementType) {
        super(declClass, name);
        this.elementType = elementType;
    }

    public Sequence<T> cast(final Object o) {
        if (o instanceof Sequence) {
            return ((Sequence<?>)o).cast(elementType);
        } else if (o instanceof Object[]){
            return Sequence.of((Object[])o).cast(elementType);
        } else if (o instanceof Collection) {
            return Sequence.of((Collection<?>)o).cast(elementType);
        } else {
            throw new ClassCastException("Not a sequence");
        }
    }
}
