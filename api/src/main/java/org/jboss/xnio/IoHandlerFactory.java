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

package org.jboss.xnio;

import java.nio.channels.Channel;

/**
 * A factory for I/O handlers.  Instances of this interface produce handler instances for clients and servers that
 * deal with arbitrary numbers of connections.
 *
 * @param <T> the type of channel that the handler can handle
 *
 * @apiviz.landmark
 */
public interface IoHandlerFactory<T extends Channel> {
    /**
     * Create a handler for a potential new channel.
     *
     * @return the handler
     */
    IoHandler<? super T> createHandler();
}
