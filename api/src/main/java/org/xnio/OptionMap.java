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

package org.xnio;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Properties;
import java.io.Serializable;
import org.jboss.logging.Logger;

/**
 * An immutable map of options to option values.  No {@code null} keys or values are permitted.
 */
public final class OptionMap implements Iterable<Option<?>>, Serializable {

    private static final long serialVersionUID = 3632842565346928132L;

    private final Map<Option<?>, Object> value;

    private OptionMap(final Map<Option<?>, Object> value) {
        this.value = value;
    }

    /**
     * Determine whether this option map contains the given option.
     *
     * @param option the option to check
     * @return {@code true} if the option is present in the option map
     */
    public boolean contains(Option<?> option) {
        return value.containsKey(option);
    }

    /**
     * Get the value of an option from this option map.
     *
     * @param option the option to get
     * @param <T> the type of the option
     * @return the option value, or {@code null} if it is not present
     */
    public <T> T get(Option<T> option) {
        return option.cast(value.get(option));
    }

    /**
     * Get the value of an option from this option map, with a specified default if the value is missing.
     *
     * @param option the option to get
     * @param defaultValue the value to return if the option is not set
     * @param <T> the type of the option
     * @return the option value, or {@code null} if it is not present
     */
    public <T> T get(Option<T> option, T defaultValue) {
        final Object o = value.get(option);
        return o == null ? defaultValue : option.cast(o);
    }

    /**
     * Get a boolean value from this option map, with a specified default if the value is missing.
     *
     * @param option the option to get
     * @param defaultValue the default value if the option is not present
     * @return the result
     */
    public boolean get(Option<Boolean> option, boolean defaultValue) {
        final Object o = value.get(option);
        return o == null ? defaultValue : option.cast(o).booleanValue();
    }

    /**
     * Get a int value from this option map, with a specified default if the value is missing.
     *
     * @param option the option to get
     * @param defaultValue the default value if the option is not present
     * @return the result
     */
    public int get(Option<Integer> option, int defaultValue) {
        final Object o = value.get(option);
        return o == null ? defaultValue : option.cast(o).intValue();
    }

    /**
     * Get a long value from this option map, with a specified default if the value is missing.
     *
     * @param option the option to get
     * @param defaultValue the default value if the option is not present
     * @return the result
     */
    public long get(Option<Long> option, long defaultValue) {
        final Object o = value.get(option);
        return o == null ? defaultValue : option.cast(o).longValue();
    }

    /**
     * Iterate over the options in this map.
     *
     * @return an iterator over the options
     */
    public Iterator<Option<?>> iterator() {
        return Collections.unmodifiableCollection(value.keySet()).iterator();
    }

    /**
     * Get the number of options stored in this map.
     *
     * @return the number of options
     */
    public int size() {
        return value.size();
    }

    /**
     * The empty option map.
     */
    public static final OptionMap EMPTY = new OptionMap(Collections.<Option<?>, Object>emptyMap());

    /**
     * Create a new builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a single-valued option map.
     *
     * @param option the option to put in the map
     * @param value the option value
     * @param <T> the option value type
     * @return the option map
     *
     * @since 3.0
     */
    public static <T> OptionMap create(Option<T> option, T value) {
        if (option == null) {
            throw new IllegalArgumentException("option is null");
        }
        if (value == null) {
            throw new IllegalArgumentException("value is null");
        }
        return new OptionMap(Collections.<Option<?>, Object>singletonMap(option, option.cast(value)));
    }

    /**
     * Create a two-valued option map.  If both options are the same key, then only the second one is added
     * to the map.
     *
     * @param option1 the first option to put in the map
     * @param value1 the first option value
     * @param option2 the second option to put in the map
     * @param value2 the second option value
     * @param <T1> the first option value type
     * @param <T2> the second option value type
     * @return the option map
     *
     * @since 3.0
     */
    public static <T1, T2> OptionMap create(Option<T1> option1, T1 value1, Option<T2> option2, T2 value2) {
        if (option1 == null) {
            throw new IllegalArgumentException("option1 is null");
        }
        if (value1 == null) {
            throw new IllegalArgumentException("value1 is null");
        }
        if (option2 == null) {
            throw new IllegalArgumentException("option2 is null");
        }
        if (value2 == null) {
            throw new IllegalArgumentException("value2 is null");
        }
        if (option1 == option2) {
            return create(option2, value2);
        }
        final IdentityHashMap<Option<?>, Object> map = new IdentityHashMap<Option<?>, Object>(2);
        map.put(option1, value1);
        map.put(option2, value2);
        return new OptionMap(map);
    }

    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append('{');
        final Iterator<Map.Entry<Option<?>, Object>> iterator = value.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<Option<?>, Object> entry = iterator.next();
            builder.append(entry.getKey()).append("=>").append(entry.getValue());
            if (iterator.hasNext()) {
                builder.append(',');
            }
        }
        builder.append('}');
        return builder.toString();
    }

    /**
     * Determine whether this option map is equal to another.
     *
     * @param other the other option map
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(Object other) {
        return other instanceof OptionMap && equals((OptionMap)other);
    }

    /**
     * Determine whether this option map is equal to another.
     *
     * @param other the other option map
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(OptionMap other) {
        return this == other || other != null && value.equals(other.value);
    }

    /**
     * Get the hash code for this option map.
     *
     * @return the hash code
     */
    public int hashCode() {
        return value.hashCode();
    }

    /**
     * A builder for immutable option maps.  Create an instance with the {@link OptionMap#builder()} method.
     */
    public static final class Builder {

        private static final Logger log = Logger.getLogger("org.xnio.option.parse");

        private Builder() {
        }

        private static class OVPair<T> {
            Option<T> option;
            T value;

            private OVPair(final Option<T> option, final T value) {
                this.option = option;
                this.value = value;
            }
        }

        private List<OVPair<?>> list = new ArrayList<OVPair<?>>();

        /**
         * Set a key-value pair, parsing the value from the given string.
         *
         * @param key the key
         * @param stringValue the string value
         * @param <T> the option type
         * @return this builder
         */
        public <T> Builder parse(Option<T> key, String stringValue) {
            set(key, key.parseValue(stringValue, key.getClass().getClassLoader()));
            return this;
        }

        /**
         * Set a key-value pair, parsing the value from the given string.
         *
         * @param key the key
         * @param stringValue the string value
         * @param classLoader the class loader to use for parsing the value
         * @param <T> the option type
         * @return this builder
         */
        public <T> Builder parse(Option<T> key, String stringValue, ClassLoader classLoader) {
            set(key, key.parseValue(stringValue, classLoader));
            return this;
        }

        /**
         * Add all options from a properties file.  Finds all entries which start with a given prefix followed by '.';
         * the remainder of the property key (after the prefix) is the option name, and the value is the option value.
         * <p>If the prefix does not end with '.' character, a '.' will be appended to it before parsing.
         *
         * @param props the properties to read
         * @param prefix the prefix
         * @param optionClassLoader the class loader to use to resolve option names
         * @return this builder
         */
        public Builder parseAll(Properties props, String prefix, ClassLoader optionClassLoader) {
            if (! prefix.endsWith(".")) {
                prefix = prefix + ".";
            }
            for (String name : props.stringPropertyNames()) {
                if (name.startsWith(prefix)) {
                    final String optionName = name.substring(prefix.length());
                    try {
                        final Option<?> option = Option.fromString(optionName, optionClassLoader);
                        parse(option, props.getProperty(name), optionClassLoader);
                    } catch (IllegalArgumentException e) {
                        log.warnf("Invalid option '%s' in property '%s': %s", optionName, name, e);
                    }
                }
            }
            return this;
        }

        /**
         * Add all options from a properties file.  Finds all entries which start with a given prefix followed by '.';
         * the remainder of the property key (after the prefix) is the option name, and the value is the option value.
         *<p>If the prefix does not end with '.' character, a '.' will be appended to it before parsing.
         *
         * @param props the properties to read
         * @param prefix the prefix
         * @return this builder
         */
        public Builder parseAll(Properties props, String prefix) {
            if (! prefix.endsWith(".")) {
                prefix = prefix + ".";
            }
            for (String name : props.stringPropertyNames()) {
                if (name.startsWith(prefix)) {
                    final String optionName = name.substring(prefix.length());
                    try {
                        final Option<?> option = Option.fromString(optionName, getClass().getClassLoader());
                        parse(option, props.getProperty(name));
                    } catch (IllegalArgumentException e) {
                        log.warnf("Invalid option '%s' in property '%s': %s", optionName, name, e);
                    }
                }
            }
            return this;
        }

        /**
         * Set a key-value pair.
         *
         * @param key the key
         * @param value the value
         * @param <T> the option type
         * @return this builder
         */
        public <T> Builder set(Option<T> key, T value) {
            if (value == null) {
                throw new NullPointerException("value is null");
            }
            list.add(new OVPair<T>(key, value));
            return this;
        }

        /**
         * Set an int value for an Integer key.
         *
         * @param key the option
         * @param value the value
         * @return this builder
         */
        public Builder set(Option<Integer> key, int value) {
            list.add(new OVPair<Integer>(key, Integer.valueOf(value)));
            return this;
        }

        /**
         * Set int values for an Integer sequence key.
         *
         * @param key the key
         * @param values the values
         * @return this builder
         */
        public Builder setSequence(Option<Sequence<Integer>> key, int... values) {
            Integer[] a = new Integer[values.length];
            for (int i = 0; i < values.length; i++) {
                a[i] = Integer.valueOf(values[i]);
            }
            list.add(new OVPair<Sequence<Integer>>(key, Sequence.of(a)));
            return this;
        }

        /**
         * Set a long value for a Long key.
         *
         * @param key the option
         * @param value the value
         * @return this builder
         */
        public Builder set(Option<Long> key, long value) {
            list.add(new OVPair<Long>(key, Long.valueOf(value)));
            return this;
        }

        /**
         * Set long values for a Long sequence key.
         *
         * @param key the key
         * @param values the values
         * @return this builder
         */
        public Builder setSequence(Option<Sequence<Long>> key, long... values) {
            Long[] a = new Long[values.length];
            for (int i = 0; i < values.length; i++) {
                a[i] = Long.valueOf(values[i]);
            }
            list.add(new OVPair<Sequence<Long>>(key, Sequence.of(a)));
            return this;
        }

        /**
         * Set a boolean value for a Boolean key.
         *
         * @param key the option
         * @param value the value
         * @return this builder
         */
        public Builder set(Option<Boolean> key, boolean value) {
            list.add(new OVPair<Boolean>(key, Boolean.valueOf(value)));
            return this;
        }


        /**
         * Set boolean values for an Boolean sequence key.
         *
         * @param key the key
         * @param values the values
         * @return this builder
         */
        public Builder setSequence(Option<Sequence<Boolean>> key, boolean... values) {
            Boolean[] a = new Boolean[values.length];
            for (int i = 0; i < values.length; i++) {
                a[i] = Boolean.valueOf(values[i]);
            }
            list.add(new OVPair<Sequence<Boolean>>(key, Sequence.of(a)));
            return this;
        }

        /**
         * Set a key-value pair, where the value is a sequence type.
         *
         * @param key the key
         * @param values the values
         * @param <T> the option type
         * @return this builder
         */
        public <T> Builder setSequence(Option<Sequence<T>> key, T... values) {
            list.add(new OVPair<Sequence<T>>(key, Sequence.of(values)));
            return this;
        }

        private <T> void copy(Map<?, ?> map, Option<T> option) {
            set(option, option.cast(map.get(option)));
        }

        /**
         * Add all the entries of a map.  Any keys of the map which are not valid {@link Option}s, or whose
         * values are not valid arguments for the given {@code Option}, will cause an exception to be thrown.
         * Any keys which occur more than once in this builder will be overwritten with the last occurring value.
         *
         * @param map the map
         * @return this builder
         * @throws ClassCastException if any entries of the map are not valid option-value pairs
         */
        public Builder add(Map<?, ?> map) throws ClassCastException {
            for (Object key : map.keySet()) {
                final Option<?> option = Option.class.cast(key);
                copy(map, option);
            }
            return this;
        }

        private <T> void copy(OptionMap optionMap, Option<T> option) {
            set(option, optionMap.get(option));
        }

        /**
         * Add all entries from an existing option map to the one being built.
         * Any keys which occur more than once in this builder will be overwritten with the last occurring value.
         *
         * @param optionMap the original option map
         * @return this builder
         */
        public Builder addAll(OptionMap optionMap) {
            for (Option<?> option : optionMap) {
                copy(optionMap, option);
            }
            return this;
        }

        /**
         * Build a map that reflects the current state of this builder.
         *
         * @return the new immutable option map
         */
        public OptionMap getMap() {
            final List<OVPair<?>> list = this.list;
            if (list.size() == 0) {
                return EMPTY;
            } else if (list.size() == 1) {
                final OVPair<?> pair = list.get(0);
                return new OptionMap(Collections.<Option<?>, Object>singletonMap(pair.option, pair.value));
            } else {
                final Map<Option<?>, Object> map = new IdentityHashMap<Option<?>, Object>();
                for (OVPair<?> ovPair : list) {
                    map.put(ovPair.option, ovPair.value);
                }
                return new OptionMap(map);
            }
        }
    }
}
