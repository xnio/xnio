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
