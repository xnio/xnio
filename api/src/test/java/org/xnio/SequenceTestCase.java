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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;

/**
 * Test for {@link Sequence}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class SequenceTestCase {

    @Test
    public void emptySequence() {
        final Sequence<String> sequence = Sequence.of();
        assertNotNull(sequence);
        
        assertSame(sequence, Sequence.of(new ArrayList<String>()));
        assertSame(sequence, Sequence.empty());

        Sequence<Object> untypedSequence = sequence.cast(Object.class);
        assertSame(sequence, untypedSequence);
        assertSame(sequence, untypedSequence.cast(String.class));
        assertEquals(sequence, untypedSequence);

        assertSame(sequence, Sequence.of(sequence));

        assertEquals(0, sequence.size());
        assertTrue(sequence.isEmpty());

        Iterator<String> iterator = sequence.iterator();
        assertNotNull(iterator);
        assertFalse(iterator.hasNext());

        assertEquals(0, sequence.toArray().length);
        assertEquals(sequence.hashCode(), sequence.hashCode());
        assertEquals(sequence.hashCode(), Sequence.empty().hashCode());
    }

    @Test
    public void unitarySequence() {
        final Sequence<String> sequence = Sequence.of("single");
        assertNotNull(sequence);

        Sequence<Object> untypedSequence = sequence.cast(Object.class);
        assertSame(sequence, untypedSequence);
        assertSame(sequence, untypedSequence.cast(String.class));

        assertEquals(sequence, untypedSequence);
        assertEquals(sequence, Sequence.of("single"));
        assertEquals(Sequence.of("single"), sequence);

        assertSame(sequence, Sequence.of(sequence));

        List<String> list = new ArrayList<String>();
        list.add("single");
        assertEquals(sequence, Sequence.of(list));
        assertEquals(Sequence.of(list), sequence);

        assertEquals(1, sequence.size());
        assertFalse(sequence.isEmpty());

        Iterator<String> iterator = sequence.iterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals("single", iterator.next());
        assertFalse(iterator.hasNext());
        Exception expected = null;
        try {
            iterator.remove();
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(1, sequence.size());
        assertArrayEquals(new String[] {"single"}, sequence.toArray());

        assertEquals(sequence.hashCode(), sequence.hashCode());
        assertEquals(sequence.hashCode(), Sequence.of("single").hashCode());
    }

    @Test
    public void simpleSequence() {
        final Sequence<String> sequence = Sequence.of("a", "b", "c", "d");
        assertNotNull(sequence);

        Sequence<Object> untypedSequence = sequence.cast(Object.class);
        assertSame(sequence, untypedSequence);
        assertSame(sequence, untypedSequence.cast(String.class));

        assertEquals(sequence, untypedSequence);
        assertEquals(sequence, Sequence.of("a", "b", "c", "d"));
        assertEquals(Sequence.of("a", "b", "c", "d"), sequence);

        assertSame(sequence, Sequence.of(sequence));

        List<String> list = new ArrayList<String>();
        list.add("a");
        list.add("b");
        list.add("c");
        list.add("d");
        assertEquals(sequence, Sequence.of(list));
        assertEquals(Sequence.of(list), sequence);
        assertTrue(sequence.equals((Object) Sequence.of(list)));
        assertFalse(sequence.equals(new Object()));
        assertFalse(sequence.equals(null));
        assertFalse(sequence.equals((Object) Sequence.empty()));
        assertFalse(sequence.equals((Object) Sequence.of("a", "b", "c", "d", "e")));

        assertEquals(4, sequence.size());
        assertFalse(sequence.isEmpty());

        Iterator<String> iterator = sequence.iterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals("a", iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals("b", iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals("c", iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals("d", iterator.next());
        assertFalse(iterator.hasNext());
        Exception expected = null;
        try {
            iterator.remove();
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertEquals(4, sequence.size());
        assertArrayEquals(new String[] {"a", "b", "c", "d"}, sequence.toArray());
        assertEquals("c", sequence.get(2));
        assertEquals("b", sequence.get(1));
        assertEquals("d", sequence.get(3));
        assertEquals("a", sequence.get(0));

        assertEquals(sequence.hashCode(), sequence.hashCode());
        assertEquals(sequence.hashCode(), Sequence.of("a", "b", "c", "d").hashCode());
    }

    @Test
    public void invalidSequence() {
        NullPointerException expected = null;
        try {
            Sequence.of((Object) null);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Sequence.of(new Object(), new Object(), null, new Object());
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        List<String> list = new ArrayList<String>();
        list.add("a");
        list.add(null);
        list.add("c");
        try {
            Sequence.of(list);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);
    }
}
