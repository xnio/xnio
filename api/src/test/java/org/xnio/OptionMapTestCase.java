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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.Test;

/**
 * Test for {@link OptionMap}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class OptionMapTestCase {

    // a few extra options that will be used with options contained in Options, for testing OptionMap
    public static final Option<Long> LONG_OPTION = Option.simple(OptionMapTestCase.class, "LONG_OPTION", Long.class);
    public static final Option<Long> ABSENT_LONG_OPTION = Option.simple(OptionMapTestCase.class, "ABSENT_LONG_OPTION", Long.class);
    public static final Option<Sequence<Boolean>> BOOLEAN_SEQUENCE_OPTION = Option.sequence(OptionMapTestCase.class, "BOOLEAN_SEQUENCE_OPTION", Boolean.class);
    public static final Option<Sequence<Integer>> INT_SEQUENCE_OPTION = Option.sequence(OptionMapTestCase.class, "INT_SEQUENCE_OPTION", Integer.class);
    public static final Option<Sequence<Long>> LONG_SEQUENCE_OPTION = Option.sequence(OptionMapTestCase.class, "LONG_SEQUENCE_OPTION", Long.class);

    @Test
    public void emptyOptionMap() {
        // check size
        assertEquals(0, OptionMap.EMPTY.size());
        // check iterator
        final Iterator<Option<?>> iterator = OptionMap.EMPTY.iterator();
        assertNotNull(iterator);
        assertFalse(iterator.hasNext());
        assertNotNull(OptionMap.EMPTY.toString());
    }

    // call only if map does not contain ALLOW_BLOCKING, FILE_ACCESS, MAX_INBOUND_MESSAGE_SIZE, and STACK_SIZE
    private void checkAbsentOptions(final OptionMap optionMap) {
        assertNull(optionMap.get(Options.ALLOW_BLOCKING));
        assertEquals(false, optionMap.get(Options.ALLOW_BLOCKING, false));
        assertEquals(true, optionMap.get(Options.ALLOW_BLOCKING, true));

        assertNull(optionMap.get(Options.FILE_ACCESS));
        assertSame(FileAccess.READ_ONLY, optionMap.get(Options.FILE_ACCESS, FileAccess.READ_ONLY));
        assertSame(FileAccess.READ_WRITE, optionMap.get(Options.FILE_ACCESS, FileAccess.READ_WRITE));

        assertNull(optionMap.get(Options.MAX_INBOUND_MESSAGE_SIZE));
        assertEquals(30000, optionMap.get(Options.MAX_INBOUND_MESSAGE_SIZE, 30000));
        assertEquals(150, optionMap.get(Options.MAX_INBOUND_MESSAGE_SIZE, 150));

        assertNull(optionMap.get(Options.STACK_SIZE));
        assertEquals(100, optionMap.get(Options.STACK_SIZE, 100));
        assertEquals(200, optionMap.get(Options.STACK_SIZE, 200));
    }

    @Test
    public void unitaryOptionMap() {
        final OptionMap optionMap = OptionMap.create(Options.WORKER_ESTABLISH_WRITING, true);
        assertNotNull(optionMap);

        // check size
        assertEquals(1, optionMap.size());
        // check iterator
        final Iterator<Option<?>> iterator = optionMap.iterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(Options.WORKER_ESTABLISH_WRITING, iterator.next());
        assertFalse(iterator.hasNext());
        // check get methods
        assertEquals(true, optionMap.get(Options.WORKER_ESTABLISH_WRITING));
        assertEquals(true, optionMap.get(Options.WORKER_ESTABLISH_WRITING, false));
        assertEquals(true, optionMap.get(Options.WORKER_ESTABLISH_WRITING, true));
        checkAbsentOptions(optionMap);
        // check toString
        String optionMapToString = optionMap.toString();
        assertNotNull(optionMapToString);
        assertTrue(optionMapToString.contains("Options.WORKER_ESTABLISH_WRITING"));
        assertTrue(optionMapToString.contains("true"));
    }

    @Test
    public void optionMapWithTwoOptions() {
        final OptionMap optionMap = OptionMap.create(Options.WORKER_ESTABLISH_WRITING, false, Options.WORKER_NAME, "WORKER1");
        assertNotNull(optionMap);

        // check size
        assertEquals(2, optionMap.size());
        // check iterator
        final Iterator<Option<?>> iterator = optionMap.iterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        final Option<?> firstOption = iterator.next();
        assertTrue(iterator.hasNext());
        if (firstOption == Options.WORKER_ESTABLISH_WRITING) {
            assertEquals(Options.WORKER_NAME, iterator.next());
        } else if (firstOption == Options.WORKER_NAME) {
            assertEquals(Options.WORKER_ESTABLISH_WRITING, iterator.next());
        } else {
            fail("Unexpected option found in map: "+ firstOption);
        }
        assertFalse(iterator.hasNext());
        // check get methods
        assertEquals(false, optionMap.get(Options.WORKER_ESTABLISH_WRITING));
        assertEquals(false, optionMap.get(Options.WORKER_ESTABLISH_WRITING, false));
        assertEquals(false, optionMap.get(Options.WORKER_ESTABLISH_WRITING, true));
        assertEquals("WORKER1", optionMap.get(Options.WORKER_NAME));
        assertEquals("WORKER1", optionMap.get(Options.WORKER_NAME, ""));
        assertEquals("WORKER1", optionMap.get(Options.WORKER_NAME, "WORKER3"));
        checkAbsentOptions(optionMap);
        // check toString
        String optionMapToString = optionMap.toString();
        assertNotNull(optionMapToString);
        assertTrue(optionMapToString.contains("Options.WORKER_ESTABLISH_WRITING"));
        assertTrue(optionMapToString.contains("false"));
        assertTrue(optionMapToString.contains("Options.WORKER_NAME"));
        assertTrue(optionMapToString.contains("WORKER1"));
    }

    @Test
    public void checkOptionMapCreatedByBuilder() throws Exception {
        final OptionMap.Builder optionMapBuilder = OptionMap.builder();
        assertNotNull(optionMapBuilder);

        optionMapBuilder.parse(Options.STACK_SIZE, "900");
        optionMapBuilder.parse(Options.ALLOW_BLOCKING, "false", getClass().getClassLoader());

        final Properties properties = new Properties();
        properties.put("+ADD+." + Options.CLOSE_ABORT, "true");
        properties.put("--."+ Options.RECEIVE_BUFFER, "50000");
        properties.put("--." + Options.SSL_PEER_PORT, "989");
        properties.put("+ADD." + Options.SEND_BUFFER, "20000");
        properties.put("+ADD+." + Options.SSL_PEER_PORT, 990); // numbers are not parsed, only strings!
        properties.put("--." + Options.CLOSE_ABORT, "false");
        properties.put("+ADD+." + Options.CORK, "ture"); // typo that will cause an illegal argument exception on parsing
        properties.put("+ADD+." + Options.SSL_APPLICATION_BUFFER_REGION_SIZE, "cant parse this"); // won't be able to read this value
        properties.put("--." + Options.SSL_APPLICATION_BUFFER_SIZE, "cant parse this"); // won't be able to read this value

        optionMapBuilder.parseAll(properties, "--");
        optionMapBuilder.parseAll(properties, "+ADD+.", getClass().getClassLoader());
        optionMapBuilder.parseAll(properties, "-ADD-.");
        optionMapBuilder.parseAll(properties, "ADD", getClass().getClassLoader());

        optionMapBuilder.set(Options.FILE_ACCESS, FileAccess.READ_ONLY);
        optionMapBuilder.set(Options.MULTICAST, true);
        optionMapBuilder.set(Options.THREAD_PRIORITY, 5);
        optionMapBuilder.set(LONG_OPTION, 24352436);
        optionMapBuilder.setSequence(Options.SASL_DISALLOWED_MECHANISMS, "FooMechanism");
        optionMapBuilder.setSequence(BOOLEAN_SEQUENCE_OPTION, new boolean[]{ (Boolean) true, (Boolean) false, (Boolean) true, (Boolean) false});
        optionMapBuilder.setSequence(INT_SEQUENCE_OPTION, new int[]{11, 13, 17, 19, 23});
        optionMapBuilder.setSequence(LONG_SEQUENCE_OPTION, new long[]{300l, 400l, 500l});

        final Map<Option<Integer>, Integer> map = new HashMap<Option<Integer>, Integer>();
        map.put(Options.WORKER_READ_THREADS, 10);
        map.put(Options.WORKER_WRITE_THREADS, 10);
        optionMapBuilder.add(map);
        // use builder to create option map
        final OptionMap optionMap = optionMapBuilder.getMap();
        assertNotNull(optionMap);
        assertEquals(optionMap, optionMapBuilder.getMap());
        assertEquals(optionMapBuilder.getMap(), optionMap);

        // check size
        assertEquals(16, optionMap.size());

        // check iterator
        final Set<Option<? extends Object>> options = new HashSet<Option<? extends Object>>();
        options.add(Options.STACK_SIZE);
        options.add(Options.ALLOW_BLOCKING);
        options.add(Options.CLOSE_ABORT);
        options.add(Options.RECEIVE_BUFFER);
        options.add(Options.SSL_PEER_PORT);
        options.add(Options.CORK);
        options.add(Options.FILE_ACCESS);
        options.add(Options.MULTICAST);
        options.add(Options.THREAD_PRIORITY);
        options.add(LONG_OPTION);
        options.add(Options.SASL_DISALLOWED_MECHANISMS);
        options.add(BOOLEAN_SEQUENCE_OPTION);
        options.add(INT_SEQUENCE_OPTION);
        options.add(LONG_SEQUENCE_OPTION);
        options.add(Options.WORKER_READ_THREADS);
        options.add(Options.WORKER_WRITE_THREADS);
        final Set<Option<? extends Object>> optionsCopy = new HashSet<Option<? extends Object>>(options);
        final Iterator<Option<?>> iterator = optionMap.iterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        while(iterator.hasNext()) {
            Option<?> option = iterator.next();
            assertFalse("Unexpected option found: " + option, optionsCopy.isEmpty());
            assertTrue("Unexpected option found: " + option, optionsCopy.remove(option));
        }
        assertTrue(optionsCopy.isEmpty());
        assertFalse(iterator.hasNext());

        // check get methods
        assertEquals(900l, (long) optionMap.get(Options.STACK_SIZE));
        assertEquals(900l, (long) optionMap.get(Options.STACK_SIZE), 800l);
        assertEquals(false, optionMap.get(Options.ALLOW_BLOCKING));
        assertEquals(false, optionMap.get(Options.ALLOW_BLOCKING, true));
        assertEquals(true, optionMap.get(Options.CLOSE_ABORT));
        assertEquals(true, optionMap.get(Options.CLOSE_ABORT, true));
        assertEquals(50000,(int) optionMap.get(Options.RECEIVE_BUFFER));
        assertEquals(50000,(int) optionMap.get(Options.RECEIVE_BUFFER, 0));
        assertEquals(989,(int) optionMap.get(Options.SSL_PEER_PORT));
        assertEquals(989,(int) optionMap.get(Options.SSL_PEER_PORT, 200));
        assertFalse(optionMap.get(Options.CORK));
        assertFalse(optionMap.get(Options.CORK, true));
        assertEquals(FileAccess.READ_ONLY, optionMap.get(Options.FILE_ACCESS));
        assertEquals(FileAccess.READ_ONLY, optionMap.get(Options.FILE_ACCESS, FileAccess.READ_WRITE));
        assertEquals(true, optionMap.get(Options.MULTICAST));
        assertEquals(true, optionMap.get(Options.MULTICAST, false));
        assertEquals(5, (int) optionMap.get(Options.THREAD_PRIORITY));
        assertEquals(5, optionMap.get(Options.THREAD_PRIORITY, 2));
        assertEquals(24352436, (long) optionMap.get(LONG_OPTION));
        assertEquals(24352436, optionMap.get(LONG_OPTION, Long.MAX_VALUE));
        assertEquals(Sequence.of("FooMechanism"), optionMap.get(Options.SASL_DISALLOWED_MECHANISMS));
        assertEquals(Sequence.of("FooMechanism"), optionMap.get(Options.SASL_DISALLOWED_MECHANISMS, Sequence.<String>empty()));
        assertEquals(Sequence.of(true, false, true, false), optionMap.get(BOOLEAN_SEQUENCE_OPTION));
        assertEquals(Sequence.of(true, false, true, false), optionMap.get(BOOLEAN_SEQUENCE_OPTION, Sequence.<Boolean>of(true, true)));
        assertEquals(Sequence.of(11, 13, 17, 19, 23), optionMap.get(INT_SEQUENCE_OPTION));
        assertEquals(Sequence.of(11, 13, 17, 19, 23), optionMap.get(INT_SEQUENCE_OPTION, Sequence.of(1, 2, 3, 4, 5)));
        assertEquals(Sequence.of(300l, 400l, 500l), optionMap.get(LONG_SEQUENCE_OPTION));
        assertEquals(Sequence.of(300l, 400l, 500l), optionMap.get(LONG_SEQUENCE_OPTION, Sequence.<Long>empty()));
        assertEquals(10, (int) optionMap.get(Options.WORKER_READ_THREADS));
        assertEquals(10, optionMap.get(Options.WORKER_READ_THREADS, 30));
        assertEquals(10, (int) optionMap.get(Options.WORKER_WRITE_THREADS));
        assertEquals(10, optionMap.get(Options.WORKER_WRITE_THREADS, 20));
        // check absent options
        assertNull(optionMap.get(Options.BROADCAST));
        assertEquals(false, optionMap.get(Options.BROADCAST, false));
        assertEquals(true, optionMap.get(Options.BROADCAST, true));

        assertNull(optionMap.get(Options.WORKER_NAME));
        assertSame("1", optionMap.get(Options.WORKER_NAME, "1"));
        assertSame("MAIN_HOST", optionMap.get(Options.WORKER_NAME, "MAIN_HOST"));

        assertNull(optionMap.get(Options.MAX_OUTBOUND_MESSAGE_SIZE));
        assertEquals(1000, optionMap.get(Options.MAX_OUTBOUND_MESSAGE_SIZE, 1000));
        assertEquals(7890, optionMap.get(Options.MAX_OUTBOUND_MESSAGE_SIZE, 7890));

        assertNull(optionMap.get(ABSENT_LONG_OPTION));
        assertEquals(1l, optionMap.get(ABSENT_LONG_OPTION, 1l));
        assertEquals(0l, optionMap.get(ABSENT_LONG_OPTION, 0l));

        assertNull(optionMap.get(Options.SSL_APPLICATION_BUFFER_REGION_SIZE));
        assertEquals(500, optionMap.get(Options.SSL_APPLICATION_BUFFER_REGION_SIZE, 500));
        assertEquals(700, optionMap.get(Options.SSL_APPLICATION_BUFFER_REGION_SIZE, 700));
        assertNull(optionMap.get(Options.SSL_APPLICATION_BUFFER_SIZE));
        assertEquals(570, optionMap.get(Options.SSL_APPLICATION_BUFFER_SIZE, 570));
        assertEquals(880, optionMap.get(Options.SSL_APPLICATION_BUFFER_SIZE, 880));

        // check toString
        String optionMapToString = optionMap.toString();
        assertNotNull(optionMapToString);
        for (Option<?> option: options){
            assertTrue(optionMapToString.contains(option.toString()));
            assertTrue(optionMapToString.contains(optionMap.get(option).toString()));
        }
    }

    @Test
    public void invalidCreations() {
        IllegalArgumentException expected = null;
        try {
            OptionMap.create(null, true);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            OptionMap.create(Options.SASL_POLICY_NOACTIVE, null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            OptionMap.create(null, null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            OptionMap.create(null, true, Options.BROADCAST, false);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            OptionMap.create(Options.SASL_POLICY_NOACTIVE, null, Options.BROADCAST, false);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            OptionMap.create(Options.SASL_POLICY_NOACTIVE, true, null, false);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            OptionMap.create(Options.SASL_POLICY_NOACTIVE, true, Options.BROADCAST, null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void invalidBuilderOperations() {
        final OptionMap.Builder builder = OptionMap.builder();
        assertNotNull(builder);

        Exception expected = null;
        try {
            builder.set(null, "foo");
        } catch(IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            builder.set(Options.WORKER_NAME, null);
        } catch(IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            builder.set(null, false);
        } catch(IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            builder.set((Option<Integer>) null, 1);
        } catch(IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            builder.set(null, 10l);
        } catch(IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            builder.<String>setSequence((Option<Sequence<String>>)null);
        } catch(IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            builder.setSequence((Option<Sequence<Integer>>)null, new int[]{1});
        } catch(IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            builder.setSequence((Option<Sequence<Long>>)null, new long[]{1});
        } catch(IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            builder.setSequence((Option<Sequence<Boolean>>)null, new boolean[]{false});
        } catch(IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void optionMapEquality() {
        final OptionMap emptyMap1 = OptionMap.EMPTY, emptyMap2 = OptionMap.builder().getMap();
        assertEquals(emptyMap1, emptyMap2);
        assertEquals(emptyMap1, emptyMap1);
        assertEquals(emptyMap2, emptyMap1);
        assertEquals(emptyMap2, emptyMap2);
        assertEquals(emptyMap1.hashCode(), emptyMap1.hashCode());
        assertEquals(emptyMap2.hashCode(), emptyMap2.hashCode());
        assertEquals(emptyMap1.hashCode(), emptyMap2.hashCode());

        final OptionMap optionMap1 = OptionMap.create(LONG_OPTION, 20l),
            optionMap2 = OptionMap.create(LONG_OPTION, 10l, LONG_OPTION, 20l), // first option value will be ignored
            optionMap3 = OptionMap.create(LONG_OPTION, 30l);

        assertEquals(optionMap1, optionMap2);
        assertTrue(optionMap1.equals((Object) optionMap2));
        assertEquals(optionMap2, optionMap1);
        assertTrue(optionMap2.equals((Object) optionMap1));
        assertEquals(optionMap1, optionMap1);
        assertTrue(optionMap1.equals((Object) optionMap1));
        assertEquals(optionMap2, optionMap2);
        assertTrue(optionMap2.equals((Object) optionMap2));
        assertEquals(optionMap1.hashCode(), optionMap2.hashCode());
        assertEquals(optionMap2.hashCode(), optionMap1.hashCode());
        assertEquals(optionMap1.hashCode(), optionMap1.hashCode());
        assertEquals(optionMap2.hashCode(), optionMap2.hashCode());

        assertFalse(optionMap1.equals(emptyMap1));
        assertFalse(optionMap1.equals((Object) emptyMap1));
        assertFalse(optionMap2.equals(emptyMap1));
        assertFalse(optionMap2.equals((Object) emptyMap1));
        assertFalse(optionMap3.equals(emptyMap1));
        assertFalse(optionMap3.equals((Object) emptyMap1));
        assertFalse(emptyMap1.equals(optionMap1));
        assertFalse(emptyMap1.equals((Object) optionMap1));
        assertFalse(emptyMap1.equals(optionMap2));
        assertFalse(emptyMap1.equals((Object) optionMap2));
        assertFalse(emptyMap1.equals(optionMap3));
        assertFalse(emptyMap1.equals((Object) optionMap3));
        assertFalse(optionMap1.equals(emptyMap2));
        assertFalse(optionMap1.equals((Object) emptyMap2));
        assertFalse(optionMap2.equals(emptyMap2));
        assertFalse(optionMap2.equals((Object) emptyMap2));
        assertFalse(optionMap3.equals(emptyMap2));
        assertFalse(optionMap3.equals((Object) emptyMap2));
        assertFalse(emptyMap2.equals(optionMap1));
        assertFalse(emptyMap2.equals((Object) optionMap1));
        assertFalse(emptyMap2.equals(optionMap2));
        assertFalse(emptyMap2.equals((Object) optionMap2));
        assertFalse(emptyMap2.equals(optionMap3));
        assertFalse(emptyMap2.equals((Object) optionMap3));

        assertFalse(optionMap1.equals(optionMap3));
        assertFalse(optionMap1.equals((Object) optionMap3));
        assertFalse(optionMap2.equals(optionMap3));
        assertFalse(optionMap2.equals((Object) optionMap3));
        assertFalse(optionMap3.equals(optionMap1));
        assertFalse(optionMap3.equals((Object) optionMap1));
        assertFalse(optionMap3.equals(optionMap2));
        assertFalse(optionMap3.equals((Object) optionMap2));

        assertFalse(emptyMap1.equals(null));
        assertFalse(emptyMap1.equals((OptionMap) null));
        assertFalse(emptyMap2.equals(null));
        assertFalse(emptyMap2.equals((OptionMap) null));
        assertFalse(optionMap1.equals(null));
        assertFalse(optionMap1.equals((OptionMap) null));
        assertFalse(optionMap2.equals(null));
        assertFalse(optionMap2.equals((OptionMap) null));
        assertFalse(optionMap3.equals(null));
        assertFalse(optionMap3.equals((OptionMap) null));

        assertFalse(emptyMap1.equals(new Object()));
        assertFalse(emptyMap2.equals(new Object()));
        assertFalse(optionMap1.equals(new Object()));
        assertFalse(optionMap2.equals(new Object()));
        assertFalse(optionMap3.equals(new Object()));
    }
}
