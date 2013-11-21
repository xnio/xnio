/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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
