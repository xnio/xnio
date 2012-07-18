/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * Test for {@link Property}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class PropertyTestCase {

    @Test
    public void test() {
        final Property[] properties = new Property[]{ Property.of("PROP_1", "1"),  Property.of("PROP_2", "2"),
                Property.of("PROP_3", "3"), Property.of("PROP_4", "4"), Property.of("PROP_5", "5"),
                Property.of("PROP_6", "6"), Property.of("PROP_7", "7"), Property.of("PROP_8", "8"),
                Property.of("PROP_9", "9"), Property.of("PROP_0", "0"), Property.of("PROP_1", "2"),
                Property.of("PROP", "5"), Property.of("PROP_1", "10"), Property.of("PROP_20", "2")};

        assertEquals("PROP_1", properties[0].getKey());
        assertEquals("1", properties[0].getValue());
        assertEquals("PROP_2", properties[1].getKey());
        assertEquals("2", properties[1].getValue());
        assertEquals("PROP_3", properties[2].getKey());
        assertEquals("3", properties[2].getValue());
        assertEquals("PROP_4", properties[3].getKey());
        assertEquals("4", properties[3].getValue());
        assertEquals("PROP_5", properties[4].getKey());
        assertEquals("5", properties[4].getValue());

        checkAllAreNotEqual(properties);

        IllegalArgumentException expected = null;
        try {
            Property.of(null, "value");
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Property.of("key", null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    private void checkAllAreNotEqual(Property...properties) {
        Property property = properties[0];
        int i = -1;
        do {
            assertEquals(property, property);
            assertFalse(property.equals(new Object()));
            for (Property compareTo: properties) {
                if (property != compareTo) {
                    assertFalse(property.toString() + " is equal to " + compareTo, property.equals((Object)compareTo));
                    assertFalse(property.toString() + " is equal to " + compareTo, property.equals(compareTo));
                    // two calls to hashCode must return the same result
                    assertEquals(property.hashCode(), property.hashCode());
                    assertEquals(compareTo.hashCode(), compareTo.hashCode());
                }
            }
        } while(++i < properties.length && (property = properties[i]) != null);
    }
}
