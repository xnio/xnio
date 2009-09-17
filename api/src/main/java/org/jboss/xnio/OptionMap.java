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

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.io.Serializable;

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
     * A builder for immutable option maps.  Create an instance with the {@link OptionMap#builder()} method.
     */
    public static final class Builder {

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
         * Add a key-value pair.
         *
         * @param key the key
         * @param value the value
         * @param <T> the option type
         * @return this builder
         */
        public <T> Builder add(Option<T> key, T value) {
            if (value == null) {
                throw new NullPointerException("value is null");
            }
            list.add(new OVPair<T>(key, value));
            return this;
        }

        /**
         * Add an int value to an Integer key.
         *
         * @param key the option
         * @param value the value
         * @return this builder
         */
        public Builder add(Option<Integer> key, int value) {
            list.add(new OVPair<Integer>(key, Integer.valueOf(value)));
            return this;
        }

        /**
         * Add int values to an Integer sequence key.
         *
         * @param key the key
         * @param values the values
         * @return this builder
         */
        public Builder addSequence(Option<Sequence<Integer>> key, int... values) {
            Integer[] a = new Integer[values.length];
            for (int i = 0; i < values.length; i++) {
                a[i] = Integer.valueOf(values[i]);
            }
            list.add(new OVPair<Sequence<Integer>>(key, Sequence.of(a)));
            return this;
        }

        /**
         * Add a long value to a Long key.
         *
         * @param key the option
         * @param value the value
         * @return this builder
         */
        public Builder add(Option<Long> key, long value) {
            list.add(new OVPair<Long>(key, Long.valueOf(value)));
            return this;
        }

        /**
         * Add long values to an Long sequence key.
         *
         * @param key the key
         * @param values the values
         * @return this builder
         */
        public Builder addSequence(Option<Sequence<Long>> key, long... values) {
            Long[] a = new Long[values.length];
            for (int i = 0; i < values.length; i++) {
                a[i] = Long.valueOf(values[i]);
            }
            list.add(new OVPair<Sequence<Long>>(key, Sequence.of(a)));
            return this;
        }

        /**
         * Add a boolean value to a Boolean key.
         *
         * @param key the option
         * @param value the value
         * @return this builder
         */
        public Builder add(Option<Boolean> key, boolean value) {
            list.add(new OVPair<Boolean>(key, Boolean.valueOf(value)));
            return this;
        }


        /**
         * Add boolean values to an Boolean sequence key.
         *
         * @param key the key
         * @param values the values
         * @return this builder
         */
        public Builder addSequence(Option<Sequence<Boolean>> key, boolean... values) {
            Boolean[] a = new Boolean[values.length];
            for (int i = 0; i < values.length; i++) {
                a[i] = Boolean.valueOf(values[i]);
            }
            list.add(new OVPair<Sequence<Boolean>>(key, Sequence.of(a)));
            return this;
        }

        /**
         * Add a key-value pair, where the value is a sequence type.
         *
         * @param key the key
         * @param values the values
         * @param <T> the option type
         * @return this builder
         */
        public <T> Builder addSequence(Option<Sequence<T>> key, T... values) {
            list.add(new OVPair<Sequence<T>>(key, Sequence.of(values)));
            return this;
        }

        /**
         * Add a key-value pair, where the value is a flag type.
         *
         * @param key the key
         * @param values the values
         * @param <T> the option type
         * @return this builder
         */
        public <T extends Enum<T>> Builder addFlags(Option<FlagSet<T>> key, T... values) {
            list.add(new OVPair<FlagSet<T>>(key, FlagSet.of(values)));
            return this;
        }

        private <T> void copy(Map<?, ?> map, Option<T> option) {
            add(option, option.cast(map.get(option)));
        }

        /**
         * Add all the entries of a map.  Any keys of the map which are not valid {@link Option}s, or whose
         * values are not valid arguments for the given {@code Option}, will cause an exception to be thrown.
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
            add(option, optionMap.get(option));
        }

        /**
         * Add all entries from an existing option map to the one being built.
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
