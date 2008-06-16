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

package org.jboss.xnio.channels;

import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.io.IOException;

/**
 * A stream source channel.  This type of channel is a readable source for bytes.
 */
public interface StreamSourceChannel extends ReadableByteChannel, ScatteringByteChannel, SuspendableReadChannel, Configurable {
    /**
     * Places this readable channel at "end of stream".  Further reads will result in EOF.
     *
     * @throws IOException if an I/O error occurs
     */
    void shutdownReads() throws IOException;
}
