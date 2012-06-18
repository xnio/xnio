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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 * Test for {@link LimitedBlockingQueue}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class LimitedBlockingQueueTestCase {

    private void test(final LimitedBlockingQueue<Character> limitedQueue, int limitedCapacity) throws InterruptedException {
        assertEquals(limitedCapacity, limitedQueue.remainingCapacity());
        assertEquals(0, limitedQueue.size());
        assertTrue(limitedQueue.add('a'));
        assertEquals(limitedCapacity -1, limitedQueue.remainingCapacity());
        assertTrue(limitedQueue.add('b'));
        assertEquals(limitedCapacity -2, limitedQueue.remainingCapacity());
        assertTrue(limitedQueue.offerUnchecked('c'));
        assertEquals(limitedCapacity -3, limitedQueue.remainingCapacity());
        assertTrue(limitedQueue.offer('d'));
        assertEquals(limitedCapacity -4, limitedQueue.remainingCapacity());
        assertTrue(limitedQueue.add('e'));
        assertEquals(limitedCapacity -5, limitedQueue.remainingCapacity());
        assertTrue(limitedQueue.add('f'));
        assertEquals(limitedCapacity -6, limitedQueue.remainingCapacity());
        assertTrue(limitedQueue.offer('g'));
        assertEquals(limitedCapacity -7, limitedQueue.remainingCapacity());
        assertTrue(limitedQueue.add('h'));
        assertEquals(limitedCapacity -8, limitedQueue.remainingCapacity());
        assertTrue(limitedQueue.add('i'));
        assertEquals(limitedCapacity -9, limitedQueue.remainingCapacity());
        assertTrue(limitedQueue.offerUnchecked('j'));
        assertEquals(limitedCapacity -10, limitedQueue.remainingCapacity());
        assertEquals(10, limitedQueue.size());

        Exception expected = null;
        try {
            assertFalse(limitedQueue.add('k'));
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            limitedQueue.add('l');
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            limitedQueue.add('m');
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(limitedCapacity -10, limitedQueue.remainingCapacity());
        assertFalse(limitedQueue.offer('n'));
        assertFalse(limitedQueue.offer('o'));
        assertFalse(limitedQueue.offer('p'));

        expected = null;
        try {
            limitedQueue.put('0');
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            limitedQueue.offer('1', 100, TimeUnit.MILLISECONDS);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);

        assertSame('a', limitedQueue.poll());
        assertEquals(limitedCapacity -9, limitedQueue.remainingCapacity());
        assertSame('b', limitedQueue.poll());
        assertEquals(limitedCapacity -8, limitedQueue.remainingCapacity());
        assertSame('c', limitedQueue.poll());
        assertEquals(limitedCapacity -7, limitedQueue.remainingCapacity());

        assertSame('d', limitedQueue.peek());
        assertSame('d', limitedQueue.peek());
        assertSame('d', limitedQueue.peek());
        assertSame('d', limitedQueue.peek());
        assertSame('d', limitedQueue.peek());
        assertEquals(limitedCapacity -7, limitedQueue.remainingCapacity());

        assertSame('d', limitedQueue.take());
        assertSame('e', limitedQueue.take());
        assertSame('f', limitedQueue.take());
        assertEquals(limitedCapacity -4, limitedQueue.remainingCapacity());

        final Iterator<Character> iterator = limitedQueue.iterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertSame('g', iterator.next());
        assertTrue(iterator.hasNext());
        assertSame('h', iterator.next());
        iterator.remove();
        assertEquals(limitedCapacity -3, limitedQueue.remainingCapacity());
        assertTrue(iterator.hasNext());
        assertSame('i', iterator.next());
        assertTrue(iterator.hasNext());
        assertSame('j', iterator.next());
        assertFalse(iterator.hasNext());

        assertEquals(3, limitedQueue.size());
        assertSame('g', limitedQueue.poll());
        assertSame('i', limitedQueue.poll(10, TimeUnit.MICROSECONDS));
        assertSame('j', limitedQueue.poll());
        assertNull(limitedQueue.poll(10, TimeUnit.NANOSECONDS));

        final List<Character> list = new ArrayList<Character>(8);
        limitedQueue.drainTo(list);
        assertTrue(list.isEmpty());
        limitedQueue.drainTo(list, 20);
        assertTrue(list.isEmpty());
        assertEquals(limitedCapacity, limitedQueue.remainingCapacity());
        assertEquals(0, limitedQueue.size());

        limitedQueue.add('1');
        limitedQueue.add('2');
        limitedQueue.add('3');
        limitedQueue.add('4');
        limitedQueue.add('5');
        assertEquals(5, limitedQueue.size());
        assertEquals(limitedCapacity -5, limitedQueue.remainingCapacity());

        limitedQueue.drainTo(list, 0);
        assertTrue(list.isEmpty());

        limitedQueue.drainTo(list, 2);
        assertEquals(2, list.size());
        assertEquals(3, limitedQueue.size());
        assertEquals(limitedCapacity -3, limitedQueue.remainingCapacity());
        assertSame('1', list.get(0));
        assertSame('2', list.get(1));

        limitedQueue.drainTo(list);
        assertEquals(5, list.size());
        assertTrue(limitedQueue.isEmpty());
        assertSame('1', list.get(0));
        assertSame('2', list.get(1));
        assertSame('3', list.get(2));
        assertSame('4', list.get(3));
        assertSame('5', list.get(4));

        assertEquals(0, limitedQueue.size());
        assertEquals(limitedCapacity, limitedQueue.remainingCapacity());
    }

    @Test
    public void limitBlockingQueueSize() throws InterruptedException {
        final BlockingQueue<Character> queue = new ArrayBlockingQueue<Character>(30);
        final LimitedBlockingQueue<Character> limitedQueue = new LimitedBlockingQueue<Character>(queue, 10);
        test(limitedQueue, 10);

        for(int i = 0; i < 10; i ++) {
            assertTrue(limitedQueue.offerUnchecked((char) i));
        }
        assertEquals(0, limitedQueue.remainingCapacity());
        assertTrue(limitedQueue.offerUnchecked('#'));
        assertEquals(-1, limitedQueue.remainingCapacity());
    }

    @Test
    public void tooBigBlockingQueueSize() throws InterruptedException {
        final BlockingQueue<Character> queue = new ArrayBlockingQueue<Character>(10);
        final LimitedBlockingQueue<Character> limitedQueue = new LimitedBlockingQueue<Character>(queue, 20);
        test(limitedQueue, 20);
        
        for(int i = 0; i < 10; i ++) {
            assertTrue(limitedQueue.offerUnchecked((char) i));
        }
        assertEquals(10, limitedQueue.remainingCapacity());
        assertFalse(limitedQueue.offerUnchecked('#'));
        assertEquals(10, limitedQueue.remainingCapacity());
    }
}
