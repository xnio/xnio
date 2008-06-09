package org.jboss.xnio.test;

import junit.framework.TestCase;
import org.jboss.xnio.Streams;
import org.jboss.xnio.ObjectSink;
import org.jboss.xnio.ObjectSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.io.IOException;
import java.io.EOFException;

/**
 *
 */
public final class StreamsTestCase extends TestCase {
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
