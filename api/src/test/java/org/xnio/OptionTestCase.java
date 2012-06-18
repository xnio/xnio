/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Member;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * Test for {@link Option} and subclasses.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class OptionTestCase {

    public static enum Color {WHITE, YELLOW, BLUE, RED, GREEN, BLACK};

    // simple options
    public static final Option<Boolean> SIMPLE_BOOLEAN_OPTION = Option.simple(OptionTestCase.class, "SIMPLE_BOOLEAN_OPTION", Boolean.class);
    public static final Option<Byte> SIMPLE_BYTE_OPTION = Option.simple(OptionTestCase.class, "SIMPLE_BYTE_OPTION", Byte.class);
    public static final Option<Character> SIMPLE_CHAR_OPTION = Option.simple(OptionTestCase.class, "SIMPLE_CHAR_OPTION", Character.class);
    public static final Option<Short> SIMPLE_SHORT_OPTION = Option.simple(OptionTestCase.class, "SIMPLE_SHORT_OPTION", Short.class);
    public static final Option<Integer> SIMPLE_INT_OPTION = Option.simple(OptionTestCase.class, "SIMPLE_INT_OPTION", Integer.class);
    public static final Option<Long> SIMPLE_LONG_OPTION = Option.simple(OptionTestCase.class, "SIMPLE_LONG_OPTION", Long.class);
    public static final Option<String> SIMPLE_STRING_OPTION = Option.simple(OptionTestCase.class, "SIMPLE_STRING_OPTION", String.class);
    public static final Option<Property> SIMPLE_PROPERTY_OPTION = Option.simple(OptionTestCase.class, "SIMPLE_PROPERTY_OPTION", Property.class);
    public static final Option<Calendar> SIMPLE_CALENDAR_OPTION = Option.simple(OptionTestCase.class, "SIMPLE_CALENDAR_OPTION", Calendar.class);
    public static final Option<Color> SIMPLE_COLOR_OPTION = Option.simple(OptionTestCase.class, "SIMPLE_COLOR_OPTION", Color.class);
    // invalid simple options
    static final Option<Short> NON_PUBLIC_SIMPLE_OPTION = Option.simple(OptionTestCase.class, "NON_PUBLIC_SIMPLE_OPTION", Short.class);
    public Option<Boolean> NON_STATIC_SIMPLE_OPTION = Option.simple(OptionTestCase.class, "NON_STATIC_SIMPLE_OPTION", Boolean.class);
    // sequence option
    public static final Option<Sequence<String>> STRING_SEQUENCE_OPTION = Option.sequence(OptionTestCase.class, "STRING_SEQUENCE_OPTION", String.class);
    // invalid sequence options
    private static final Option<Sequence<Integer>> PRIVATE_SEQUENCE_OPTION = Option.sequence(OptionTestCase.class, "PRIVATE_SEQUENCE_OPTION", Integer.class);
    public final Option<Sequence<Byte>> NON_STATIC_SEQUENCE_OPTION = Option.sequence(OptionTestCase.class, "NON_STATIC_SE_OPTION", Byte.class);
    // type option
    @SuppressWarnings("rawtypes")
    public static final Option<Class<? extends Collection>> COLLECTION_TYPE_OPTION = Option.type(OptionTestCase.class, "COLLECTION_TYPE_OPTION", Collection.class);
    // invalid type options
    static final Option<Class<? extends Member>> NON_PUBLIC_TYPE_OPTION = Option.type(OptionTestCase.class, "NON_PUBLIC_TYPE_OPTION", Member.class);
    public final Option<Class<? extends OutputStream>> NON_STATIC_TYPE_OPTION = Option.type(OptionTestCase.class, "NON_STATIC_TYPE_OPTION", OutputStream.class);
    // type sequence option
    public static final Option<Sequence<Class<? extends Writer>>> WRITER_TYPE_SEQUENCE_OPTION = Option.typeSequence(OptionTestCase.class, "WRITER_TYPE_SEQUENCE_OPTION", Writer.class);
    // invalid type sequence options
    private static final Option<Sequence<Class<? extends Writer>>> PRIVATE_TYPE_SEQUENCE_OPTION = Option.typeSequence(OptionTestCase.class, "PRIVATE_TYPE_SEQUENCE_OPTION", Writer.class);
    public final Option<Sequence<Class<? extends Writer>>> NON_STATIC_TYPE_SEQUENCE_OPTION = Option.typeSequence(OptionTestCase.class, "NON_STATIC_TYPE_SEQUENCE_OPTION", Writer.class);
    // null option
    public static final Option<?> NULL_OPTION = null;

    @Test
    public void simpleBooleanOption() throws Exception {
        final Option<Boolean> option = SIMPLE_BOOLEAN_OPTION;

        // test cast
        assertSame(Boolean.TRUE, option.cast(true));
        assertSame(Boolean.FALSE, option.cast(false));
        ClassCastException expected = null;
        try {
            option.cast(new Object());
        } catch (ClassCastException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test getName()
        assertEquals("SIMPLE_BOOLEAN_OPTION", option.getName());
        // test hashCode()
        assertEquals(option.hashCode(), option.hashCode());
        // test parsing
        assertSame(Boolean.TRUE, option.parseValue("true", getClass().getClassLoader()));
        assertSame(Boolean.FALSE, option.parseValue("false", getClass().getClassLoader()));
        assertSame(Boolean.FALSE, option.parseValue("nothing", getClass().getClassLoader()));
        // test serialization
        Option<Boolean> recoveredOption = this.<Option<Boolean>>checkSerialization(option);
        assertEquals(option.getName(), recoveredOption.getName());
        // test toString()
        assertNotNull(option.toString());
    }

    @Test
    public void simpleByteOption() throws Exception {
        final Option<Byte> option = SIMPLE_BYTE_OPTION;

        // test cast
        assertEquals(Byte.valueOf((byte) 10), option.cast((byte) 10));
        assertEquals(Byte.valueOf((byte) 0), option.cast((byte) 0));
        Exception expected = null;
        try {
            option.cast(new Object());
        } catch (ClassCastException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test getName()
        assertEquals("SIMPLE_BYTE_OPTION", option.getName());
        // test hashCode()
        assertEquals(option.hashCode(), option.hashCode());
        // test parsing
        assertEquals(Byte.valueOf((byte) 25), option.parseValue("25", getClass().getClassLoader()));
        assertEquals(Byte.valueOf((byte) -15), option.parseValue("-15", getClass().getClassLoader()));
        expected = null;
        try {
            option.parseValue("nothing", getClass().getClassLoader());
        } catch (NumberFormatException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test serialization
        Option<Byte> recoveredOption = this.<Option<Byte>>checkSerialization(option);
        assertEquals(option.getName(), recoveredOption.getName());
        // test toString()
        assertNotNull(option.toString());
    }

    @Test
    public void simpleCharOption() throws Exception {
        final Option<Character> option = SIMPLE_CHAR_OPTION;

        // test cast
        assertEquals(Character.valueOf('1'), option.cast('1'));
        assertEquals(Character.valueOf('a'), option.cast('a'));
        Exception expected = null;
        try {
            option.cast(new Object());
        } catch (ClassCastException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test getName()
        assertEquals("SIMPLE_CHAR_OPTION", option.getName());
        // test hashCode()
        assertEquals(option.hashCode(), option.hashCode());
        // no parsing of chars for this test

        // test serialization
        Option<Character> recoveredOption = this.<Option<Character>>checkSerialization(option);
        assertEquals(option.getName(), recoveredOption.getName());
        // test toString()
        assertNotNull(option.toString());
    }

    @Test
    public void simpleShortOption() throws Exception {
        final Option<Short> option = SIMPLE_SHORT_OPTION;

        // test cast
        assertEquals(Short.valueOf((short) 50), option.cast((short) 50));
        assertEquals(Short.valueOf((short) -1), option.cast((short) -1));
        Exception expected = null;
        try {
            option.cast(new Object());
        } catch (ClassCastException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test getName()
        assertEquals("SIMPLE_SHORT_OPTION", option.getName());
        // test hashCode()
        assertEquals(option.hashCode(), option.hashCode());
        // test parsing
        assertEquals(Short.valueOf((short) 37), option.parseValue("37", getClass().getClassLoader()));
        assertEquals(Short.valueOf((short) 1000), option.parseValue("1000", getClass().getClassLoader()));
        expected = null;
        try {
            option.parseValue("anything", getClass().getClassLoader());
        } catch (NumberFormatException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test serialization
        Option<Short> recoveredOption = this.<Option<Short>>checkSerialization(option);
        assertEquals(option.getName(), recoveredOption.getName());
        // test toString()
        assertNotNull(option.toString());
    }

    @Test
    public void simpleIntegerOption() throws Exception {
        final Option<Integer> option = SIMPLE_INT_OPTION;

        // test cast
        assertEquals(Integer.valueOf(25987), option.cast(25987));
        assertEquals(Integer.valueOf(-10345), option.cast(-10345));
        Exception expected = null;
        try {
            option.cast(new Object());
        } catch (ClassCastException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test getName()
        assertEquals("SIMPLE_INT_OPTION", option.getName());
        // test hashCode()
        assertEquals(option.hashCode(), option.hashCode());
        // test parsing
        assertEquals(Integer.valueOf(-10), option.parseValue("-10", getClass().getClassLoader()));
        assertEquals(Integer.valueOf(1), option.parseValue("1", getClass().getClassLoader()));
        expected = null;
        try {
            option.parseValue("foo", getClass().getClassLoader());
        } catch (NumberFormatException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test serialization
        Option<Integer> recoveredOption = this.<Option<Integer>>checkSerialization(option);
        assertEquals(option.getName(), recoveredOption.getName());
        // test toString()
        assertNotNull(option.toString());
    }

    @Test
    public void simpleLongOption() throws Exception {
        final Option<Long> option = SIMPLE_LONG_OPTION;

        // test cast
        assertEquals(Long.valueOf(30l), option.cast(30l));
        assertEquals(Long.valueOf(-10345024546l), option.cast(-10345024546l));
        Exception expected = null;
        try {
            option.cast(new Object());
        } catch (ClassCastException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test getName()
        assertEquals("SIMPLE_LONG_OPTION", option.getName());
        // test hashCode()
        assertEquals(option.hashCode(), option.hashCode());
        // test parsing
        assertEquals(Long.valueOf(-10), option.parseValue("-10", getClass().getClassLoader()));
        assertEquals(Long.valueOf(25987000000l), option.parseValue("25987000000", getClass().getClassLoader()));
        expected = null;
        try {
            option.parseValue("zip", getClass().getClassLoader());
        } catch (NumberFormatException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test serialization
        Option<Long> recoveredOption = this.<Option<Long>>checkSerialization(option);
        assertEquals(option.getName(), recoveredOption.getName());
        // test toString()
        assertNotNull(option.toString());
    }

    @Test
    public void simpleStringOption() throws Exception {
        final Option<String> option = SIMPLE_STRING_OPTION;

        // test cast
        assertEquals("any text", option.cast("any text"));
        assertEquals("do", option.cast("do"));
        Exception expected = null;
        try {
            option.cast(new Object());
        } catch (ClassCastException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test getName()
        assertEquals("SIMPLE_STRING_OPTION", option.getName());
        // test hashCode()
        assertEquals(option.hashCode(), option.hashCode());
        // test parsing
        assertEquals("parse this", option.parseValue("parse this", getClass().getClassLoader()));
        assertEquals("", option.parseValue("", getClass().getClassLoader()));
        // test serialization
        Option<String> recoveredOption = this.<Option<String>>checkSerialization(option);
        assertEquals(option.getName(), recoveredOption.getName());
        // test toString()
        assertNotNull(option.toString());
    }

    @Test
    public void simplePropertyOption() throws Exception {
        final Option<Property> option = SIMPLE_PROPERTY_OPTION;

        // test cast
        assertEquals(Property.of("KEY", "value"), option.cast(Property.of("KEY", "value")));
        assertEquals(Property.of("key", "VALUE"), option.cast(Property.of("key", "VALUE")));
        Exception expected = null;
        try {
            option.cast(new Object());
        } catch (ClassCastException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test getName()
        assertEquals("SIMPLE_PROPERTY_OPTION", option.getName());
        // tet hashCode()
        assertEquals(option.hashCode(), option.hashCode());
        // test parsing
        assertEquals(Property.of("1", "1"), option.parseValue("1=1", getClass().getClassLoader()));
        assertEquals(Property.of("key", "value"), option.parseValue("key=value", getClass().getClassLoader()));
        expected = null;
        try {
            option.parseValue("-", getClass().getClassLoader());
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test serialization
        Option<Property> recoveredOption = this.<Option<Property>>checkSerialization(option);
        assertEquals(option.getName(), recoveredOption.getName());
        // test toString()
        assertNotNull(option.toString());
    }

    @Test
    public void simpleObjectOption() throws Exception {
        // check how would it be like to create an option of any type that not String, Property, byte, short, etc.
        final Option<Calendar> option = SIMPLE_CALENDAR_OPTION;

        // test cast
        final Calendar calendar1 = Calendar.getInstance(), calendar2 = Calendar.getInstance();
        calendar2.setTimeInMillis(0);
        assertSame(calendar1, option.cast(calendar1));
        assertSame(calendar2, option.cast(calendar2));
        Exception expected = null;
        try {
            option.cast(new Object());
        } catch (ClassCastException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test getName()
        assertEquals("SIMPLE_CALENDAR_OPTION", option.getName());
        // test hashCode()
        assertEquals(option.hashCode(), option.hashCode());
        // test parsing
        expected = null;
        try {
            option.parseValue(DateFormat.getInstance().format(calendar1.getTime()), getClass().getClassLoader());
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test serialization
        Option<Calendar> recoveredOption = this.<Option<Calendar>>checkSerialization(option);
        assertEquals(option.getName(), recoveredOption.getName());
        // test toString()
        assertNotNull(option.toString());
    }

    @Test
    public void simpleEnumOption() throws Exception {
        final Option<Color> option = SIMPLE_COLOR_OPTION;

        // test cast
        assertEquals(Color.WHITE, option.cast(Color.WHITE));
        assertEquals(Color.BLUE, option.cast(Color.BLUE));
        Exception expected = null;
        try {
            option.cast(new Object());
        } catch (ClassCastException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test getName()
        assertEquals("SIMPLE_COLOR_OPTION", option.getName());
        // test hashCode()
        assertEquals(option.hashCode(), option.hashCode());
        // test parsing
        assertSame(Color.RED, option.parseValue(Color.RED.toString(), getClass().getClassLoader()));
        assertSame(Color.GREEN, option.parseValue(Color.GREEN.toString(), getClass().getClassLoader()));
        // test serialization
        Option<Color> recoveredOption = this.<Option<Color>>checkSerialization(option);
        assertEquals(option.getName(), recoveredOption.getName());
        // test toString()
        assertNotNull(option.toString());
    }

    @Test
    public void invalidSimpleOption() throws Exception {
        Exception expected = null;
        try {
            Option.simple(OptionTestCase.class, null, String.class);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Option.simple(null, "name", String.class);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Option.simple(OptionTestCase.class, "name", null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(Option.simple(OptionTestCase.class, "name", Integer.class));
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(NON_STATIC_SIMPLE_OPTION);
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(NON_PUBLIC_SIMPLE_OPTION);
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(Option.simple(OptionTestCase.class, "NULL_OPTION", Object.class));
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(Option.simple(OptionTestCase.class, "name", Integer.class));
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void sequenceOption() throws Exception {
        final Option<Sequence<String>> option = STRING_SEQUENCE_OPTION;

        // test cast
        final Sequence<String> sequence1 = Sequence.empty();
        final Sequence<String> sequence2 = Sequence.of("1", "2");
        assertEquals(sequence1, option.cast(sequence1));
        assertEquals(sequence1, option.cast(new Object[0]));
        assertEquals(sequence1, option.cast(new ArrayList<String>()));
        assertEquals(sequence2, option.cast(sequence2));
        assertEquals(sequence2, option.cast(new Object[] {"1", "2"}));
        final Collection<String> collection2 = new ArrayList<String>();
        collection2.add("1");
        assertFalse(sequence2.equals(option.cast(collection2)));
        collection2.add("2");
        assertEquals(sequence2, option.cast(collection2));
        Exception expected = null;
        try {
            option.cast(new Object());
        } catch (ClassCastException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test getName()
        assertEquals("STRING_SEQUENCE_OPTION", option.getName());
        // test hashCode()
        assertEquals(option.hashCode(), option.hashCode());
        // test parsing
        expected = null;
        assertEquals(sequence1, option.parseValue("", getClass().getClassLoader()));
        assertEquals(sequence2, option.parseValue("1,2", getClass().getClassLoader()));
        // test serialization
        Option<Sequence<String>> recoveredOption = this.<Option<Sequence<String>>>checkSerialization(option);
        assertEquals(option.getName(), recoveredOption.getName());
        // test toString()
        assertNotNull(option.toString());
    }

    @Test
    public void invalidSequenceOption() throws Exception {
        Exception expected = null;
        try {
            Option.sequence(OptionTestCase.class, null, String.class);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Option.sequence(null, "name", String.class);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Option.sequence(OptionTestCase.class, "name", null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(Option.sequence(OptionTestCase.class, "name", Integer.class));
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(NON_STATIC_SEQUENCE_OPTION);
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(PRIVATE_SEQUENCE_OPTION);
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(Option.sequence(OptionTestCase.class, "NULL_OPTION", Object.class));
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(Option.sequence(OptionTestCase.class, "name", Integer.class));
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void typeOption() throws Exception {
        final Option<Class<? extends Collection>> option = COLLECTION_TYPE_OPTION;

        // test cast
        assertEquals(List.class, option.cast(List.class));
        assertEquals(HashSet.class, option.cast(HashSet.class));
        Exception expected = null;
        try {
            option.cast(new Object());
        } catch (ClassCastException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test getName()
        assertEquals("COLLECTION_TYPE_OPTION", option.getName());
        // test hashCode()
        assertEquals(option.hashCode(), option.hashCode());
        // test parsing
        assertEquals(Set.class, option.parseValue(Set.class.getName(), getClass().getClassLoader()));
        assertEquals(ArrayList.class, option.parseValue(ArrayList.class.getName(), getClass().getClassLoader()));

        expected = null;
        try {
            option.parseValue(String.class.getName(), getClass().getClassLoader());
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        try {
            option.parseValue("nonono", getClass().getClassLoader());
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        // test serialization
        Option<?> recoveredOption = this.<Option<?>>checkSerialization(option);
        assertEquals(option.getName(), recoveredOption.getName());
        // test toString()
        assertNotNull(option.toString());
    }

    @Test
    public void invalidTypeOption() throws Exception {
        Exception expected = null;
        try {
            Option.type(OptionTestCase.class, null, String.class);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Option.type(null, "name", String.class);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Option.type(OptionTestCase.class, "name", null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(Option.type(OptionTestCase.class, "name", Collection.class));
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(NON_STATIC_TYPE_OPTION);
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(NON_PUBLIC_TYPE_OPTION);
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(Option.type(OptionTestCase.class, "NULL_OPTION", Object.class));
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(Option.type(OptionTestCase.class, "OPTION", Integer.class));
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void typeSequenceOption() throws Exception {
        final Option<Sequence<Class<? extends Writer>>> option = WRITER_TYPE_SEQUENCE_OPTION;

        // test cast
        final List<Class<? extends Writer>> writerList1 = new ArrayList<Class<? extends Writer>>();
        writerList1.add(FileWriter.class);
        writerList1.add(BufferedWriter.class);
        final Sequence<Class<? extends Writer>> sequence1 = Sequence.<Class<? extends Writer>>of(FileWriter.class, BufferedWriter.class);
        final Sequence<Class<? extends Writer>> sequence2 = Sequence.empty();
        assertEquals(sequence1, option.cast(sequence1));
        assertEquals(sequence1, option.cast(new Object[]{FileWriter.class, BufferedWriter.class}));
        assertEquals(sequence1, option.cast(writerList1));
        assertEquals(sequence2, option.cast(sequence2));
        assertEquals(sequence2, option.cast(new Object[0]));
        assertEquals(sequence2, option.cast(new ArrayList<Class<? extends Writer>>()));
        Exception expected = null;
        try {
            option.cast(new Object());
        } catch (ClassCastException e) {
            expected = e;
        }
        assertNotNull(expected);
        // test getName()
        assertEquals("WRITER_TYPE_SEQUENCE_OPTION", option.getName());
        // test hashCode()
        assertEquals(option.hashCode(), option.hashCode());
        // test parsing
        final Sequence<Class<? extends Writer>> sequence3 = Sequence.<Class<? extends Writer>> of(OutputStreamWriter.class);
        assertEquals(sequence3, option.parseValue(OutputStreamWriter.class.getName(), getClass().getClassLoader()));
        assertEquals(sequence2, option.parseValue("", getClass().getClassLoader()));
        assertEquals(sequence1, option.parseValue(FileWriter.class.getName() + "," + BufferedWriter.class.getName(), getClass().getClassLoader()));

        expected = null;
        try {
            option.parseValue(String.class.getName(), getClass().getClassLoader());
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        try {
            option.parseValue("nonono", getClass().getClassLoader());
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        // test serialization
        Option<?> recoveredOption = this.<Option<?>>checkSerialization(option);
        assertEquals(option.getName(), recoveredOption.getName());
        // test toString()
        assertNotNull(option.toString());
    }

    @Test
    public void invalidTypeSequenceOption() throws Exception {
        Exception expected = null;
        try {
            Option.typeSequence(OptionTestCase.class, null, String.class);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Option.typeSequence(null, "name", String.class);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Option.typeSequence(OptionTestCase.class, "name", null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(Option.typeSequence(OptionTestCase.class, "OPTION", Collection.class));
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(NON_STATIC_TYPE_SEQUENCE_OPTION);
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(PRIVATE_TYPE_SEQUENCE_OPTION);
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(Option.type(OptionTestCase.class, "NULL_OPTION", Object.class));
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            checkSerialization(Option.type(OptionTestCase.class, "OPTION", Integer.class));
        } catch (InvalidObjectException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void optionFromString() {
        String prefix = getClass().getName() + ".";
        ClassLoader classLoader = getClass().getClassLoader();
        assertSame(SIMPLE_BOOLEAN_OPTION, Option.fromString(prefix + "SIMPLE_BOOLEAN_OPTION", classLoader));
        assertSame(SIMPLE_BYTE_OPTION, Option.fromString(prefix + "SIMPLE_BYTE_OPTION", classLoader));
        assertSame(SIMPLE_CHAR_OPTION, Option.fromString(prefix + "SIMPLE_CHAR_OPTION", classLoader));
        assertSame(SIMPLE_SHORT_OPTION, Option.fromString(prefix + "SIMPLE_SHORT_OPTION", classLoader));
        assertSame(SIMPLE_INT_OPTION, Option.fromString(prefix + "SIMPLE_INT_OPTION", classLoader));
        assertSame(SIMPLE_LONG_OPTION, Option.fromString(prefix + "SIMPLE_LONG_OPTION", classLoader));
        assertSame(SIMPLE_STRING_OPTION, Option.fromString(prefix + "SIMPLE_STRING_OPTION", classLoader));
        assertSame(SIMPLE_PROPERTY_OPTION, Option.fromString(prefix + "SIMPLE_PROPERTY_OPTION", classLoader));
        assertSame(SIMPLE_CALENDAR_OPTION, Option.fromString(prefix + "SIMPLE_CALENDAR_OPTION", classLoader));
        assertSame(SIMPLE_COLOR_OPTION, Option.fromString(prefix + "SIMPLE_COLOR_OPTION", classLoader));
        assertSame(STRING_SEQUENCE_OPTION, Option.fromString(prefix + "STRING_SEQUENCE_OPTION", classLoader));
        assertSame(COLLECTION_TYPE_OPTION, Option.fromString(prefix + "COLLECTION_TYPE_OPTION", classLoader));
        assertSame(WRITER_TYPE_SEQUENCE_OPTION, Option.fromString(prefix + "WRITER_TYPE_SEQUENCE_OPTION", classLoader));

        IllegalArgumentException expected = null;
        try {
            Option.fromString(prefix + "NON_STATIC_SIMPLE_OPTION", classLoader);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Option.fromString(prefix + "NON_PUBLIC_SIMPLE_OPTION", classLoader);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Option.fromString(prefix + "NULL_OPTION", classLoader);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Option.fromString(prefix + "NON_EXISTENT_OPTION", classLoader);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Option.fromString("Foo.OPTION", classLoader);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Option.fromString("NO_DOT", classLoader);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void setBuilder() {
        Option.SetBuilder setBuilder = Option.setBuilder();
        assertTrue(setBuilder.create().isEmpty());

        assertSame(setBuilder, setBuilder.add(SIMPLE_BYTE_OPTION));

        final Set<Option<?>> set1 = setBuilder.create();
        assertNotNull(set1);
        assertEquals(1, set1.size());
        assertTrue(set1.contains(SIMPLE_BYTE_OPTION));

        assertSame(setBuilder, setBuilder.add(SIMPLE_COLOR_OPTION));

        final Set<Option<?>> set2 = setBuilder.create();
        assertNotNull(set2);
        assertEquals(2, set2.size());
        assertTrue(set2.contains(SIMPLE_BYTE_OPTION));
        assertTrue(set2.contains(SIMPLE_COLOR_OPTION));

        NullPointerException expected = null;
        try {
            setBuilder.add(NULL_OPTION);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);

        final List<Option<?>> options = new ArrayList<Option<?>>();
        assertSame(setBuilder, setBuilder.addAll(options));

        final Set<Option<?>> set3 = setBuilder.create();
        assertNotNull(set3);
        assertEquals(2, set3.size());
        assertTrue(set3.contains(SIMPLE_BYTE_OPTION));
        assertTrue(set3.contains(SIMPLE_COLOR_OPTION));

        options.add(COLLECTION_TYPE_OPTION);
        options.add(STRING_SEQUENCE_OPTION);

        assertSame(setBuilder, setBuilder.addAll(options));

        final Set<Option<?>> set4 = setBuilder.create();
        assertNotNull(set4);
        assertEquals(4, set4.size());
        assertTrue(set4.contains(SIMPLE_BYTE_OPTION));
        assertTrue(set4.contains(SIMPLE_COLOR_OPTION));
        assertTrue(set4.contains(COLLECTION_TYPE_OPTION));
        assertTrue(set4.contains(STRING_SEQUENCE_OPTION));

        options.add(null);
        expected = null;
        try {
            setBuilder.addAll(options);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            setBuilder.addAll(null);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @SuppressWarnings("unchecked")
    private <T> T  checkSerialization(T object) throws Exception {
        final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        final ObjectOutput output = new ObjectOutputStream(new BufferedOutputStream(byteOutputStream));
        try{
          output.writeObject(object);
        }
        finally{
          output.close();
        }
 
        final ByteArrayInputStream byteInputStream = new ByteArrayInputStream(byteOutputStream.toByteArray());
        final ObjectInput input = new ObjectInputStream(new BufferedInputStream(byteInputStream));
        final T recoveredObject;
        try{
          recoveredObject = (T) input.readObject();
        }
        finally{
          input.close();
        }
        assertNotNull(recoveredObject);
        assertEquals(object, recoveredObject);
        return recoveredObject;
    }
}