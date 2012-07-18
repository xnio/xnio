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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.xnio.mock.ConnectedStreamChannelMock;

/**
 * Test for {@link Closer}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class CloserTestCase {

    @Test
    public void close() {
        final ConnectedStreamChannelMock channel = new ConnectedStreamChannelMock();
        final Closer closer = new Closer(channel);
        assertTrue(channel.isOpen());
        closer.run();
        assertFalse(channel.isOpen());
    }

    @Test
    public void closeNullChannel() {
        final Closer closer = new Closer(null);
        NullPointerException notExpected = null;
        try {
            closer.run();
        } catch (NullPointerException e) {
            notExpected = e;
        }
        assertNull(notExpected);
    }
}
