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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Test for {@link LocalSocketAddress}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class LocalSocketAddressTestCase {
    @Test
    public void test1() {
        final LocalSocketAddress socketAddress = new LocalSocketAddress("name");
        assertEquals("name", socketAddress.getName());
        assertTrue(socketAddress.toString().contains("name"));
    }

    @Test
    public void test2() {
        final LocalSocketAddress socketAddress = new LocalSocketAddress("address");
        assertEquals("address", socketAddress.getName());
        assertTrue(socketAddress.toString().contains("address"));
    }

    @Test
    public void testNull() {
        IllegalArgumentException expected = null;
        try {
            new LocalSocketAddress(null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }
}
