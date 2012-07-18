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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.Test;

/**
 * Test for {@link Options}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class OptionsTestCase {

    @Test
    public void testAllOptions() throws Exception {
        for (Field field: Options.class.getDeclaredFields()) {
            if (Option.class.isAssignableFrom(field.getType())) {
                int fieldModifiers = field.getModifiers();
                assertTrue(Modifier.isPublic(fieldModifiers));
                assertTrue(Modifier.isStatic(fieldModifiers));

                // retrieve field value
                Option<?> option = (Option<?>) field.get(null);
                assertSame(field.getName(), option.getName());
                checkSerialization(option);
            }
        }
    }

    private void checkSerialization(Option<?>  option) throws Exception {
        final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        final ObjectOutput output = new ObjectOutputStream(new BufferedOutputStream(byteOutputStream));
        try{
          output.writeObject(option);
        }
        finally{
          output.close();
        }
 
        final ByteArrayInputStream byteInputStream = new ByteArrayInputStream(byteOutputStream.toByteArray());
        final ObjectInput input = new ObjectInputStream(new BufferedInputStream(byteInputStream));
        final Option<?> recoveredOption;
        try{
          recoveredOption = (Option<?>) input.readObject();
        }
        finally{
          input.close();
        }
        assertNotNull(recoveredOption);
        assertSame(option, recoveredOption);
    }
}
