/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
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

import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A Hashtable variant which keeps property names in order, for use by MBean {@code ObjectName}s.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ObjectProperties extends Hashtable<String, String> {

    private static final long serialVersionUID = -4691081844415343670L;

    private final Map<String, String> realMap;

    public static Property property(String key, String value) {
        return new Property(key, value);
    }

    public static ObjectProperties properties(Property... properties) {
        return new ObjectProperties(properties);
    }

    public ObjectProperties(final int initialCapacity, final float loadFactor) {
        realMap = new LinkedHashMap<String, String>(initialCapacity, loadFactor);
    }

    public ObjectProperties(final int initialCapacity) {
        realMap = new LinkedHashMap<String, String>(initialCapacity);
    }

    public ObjectProperties() {
        realMap = new LinkedHashMap<String, String>();
    }

    public ObjectProperties(final Map<? extends String, ? extends String> t) {
        realMap = new LinkedHashMap<String, String>(t);
    }

    public ObjectProperties(Property... properties) {
        realMap = new LinkedHashMap<String, String>(properties.length);
        for (Property property : properties) {
            realMap.put(property.getKey(), property.getValue());
        }
    }

    public int size() {
        return realMap.size();
    }

    public boolean isEmpty() {
        return realMap.isEmpty();
    }

    public Enumeration<String> keys() {
        return Collections.enumeration(realMap.keySet());
    }

    public Enumeration<String> elements() {
        return Collections.enumeration(realMap.values());
    }

    public boolean contains(final Object value) {
        return realMap.containsValue(value);
    }

    public boolean containsValue(final Object value) {
        return realMap.containsValue(value);
    }

    public boolean containsKey(final Object key) {
        return realMap.containsKey(key);
    }

    public String get(final Object key) {
        return realMap.get(key);
    }

    protected void rehash() {
    }

    public String put(final String key, final String value) {
        return realMap.put(key, value);
    }

    public String remove(final Object key) {
        return realMap.remove(key);
    }

    public void putAll(final Map<? extends String, ? extends String> t) {
        realMap.putAll(t);
    }

    public void clear() {
        realMap.clear();
    }

    public Object clone() {
        return super.clone();
    }

    public String toString() {
        return realMap.toString();
    }

    public Set<String> keySet() {
        return realMap.keySet();
    }

    public Set<Map.Entry<String, String>> entrySet() {
        return realMap.entrySet();
    }

    public Collection<String> values() {
        return realMap.values();
    }

    /**
     * A single property in a properties list.
     */
    public static final class Property {
        private final String key;
        private final String value;

        public Property(final String key, final String value) {
            if (key == null) {
                throw new IllegalArgumentException("key is null");
            }
            if (value == null) {
                throw new IllegalArgumentException("value is null");
            }
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }
    }
}
