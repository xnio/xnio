/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.jboss.xnio.test;

import junit.framework.TestCase;
import org.jboss.xnio.Streams;
import org.jboss.xnio.ObjectSink;
import org.jboss.xnio.ObjectSource;
import org.jboss.xnio.test.support.LoggingHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.io.IOException;
import java.io.EOFException;

/**
 *
 */
public final class StreamsTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

    public void testCollectionObjectSink() {
        final List<String> strings = new ArrayList<String>();
        final ObjectSink<String> sink = Streams.getCollectionObjectSink(strings);
        try {
            final String one = "Test One";
            final String two = "Test Two";
            sink.accept(one);
            sink.accept(two);
            assertSame(one, strings.get(0));
            assertSame(two, strings.get(1));
            assertEquals(2, strings.size());
            sink.close();
        } catch (IOException e) {
            fail(e.toString());
        }
    }

    public void testIteratorObjectSource() {
        final List<String> strings = new ArrayList<String>();
        final String one = "Test One";
        final String two = "Test Two";
        strings.add(one);
        strings.add(two);
        final ObjectSource<String> source = Streams.getIteratorObjectSource(strings.iterator());
        try {
            assertTrue(source.hasNext());
            assertSame(one, source.next());
            assertTrue(source.hasNext());
            assertSame(two, source.next());
            assertFalse(source.hasNext());
        } catch (IOException e) {
            fail(e.toString());
        }
        try {
            source.next();
            fail("No exception thrown after end of stream");
        } catch (EOFException e) {
            // OK
        } catch (IOException e) {
            fail("Unexpected exception: " + e);
        }
    }

    public void testEnumerationObjectSource() {
        final Vector<String> strings = new Vector<String>();
        final String one = "Test One";
        final String two = "Test Two";
        strings.add(one);
        strings.add(two);
        final ObjectSource<String> source = Streams.getEnumerationObjectSource(strings.elements());
        try {
            assertTrue(source.hasNext());
            assertSame(one, source.next());
            assertTrue(source.hasNext());
            assertSame(two, source.next());
            assertFalse(source.hasNext());
        } catch (IOException e) {
            fail(e.toString());
        }
        try {
            source.next();
            fail("No exception thrown after end of stream");
        } catch (EOFException e) {
            // OK
        } catch (IOException e) {
            fail("Unexpected exception: " + e);
        }
    }

}
