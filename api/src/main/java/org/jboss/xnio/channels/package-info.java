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

/**
 * Defines an enhanced set of channels.  A channel is an abstraction representing a means of performing data transfer
 * between processes and systems.
 * <p/>
 * The channels provided by this package are extended beyond the basic NIO channels, including special
 * channel types for point-to-point and point-to-multipoint message-oriented channels, multicast support,
 * and generalized support for stream channels (such as serial ports).
 * <p/>
 * In addition, these channels provide a simplified alternative to selectors by way of the {@link org.jboss.xnio.channels.SuspendableChannel}
 * interface.  Rather than associating a channel with a selector, and subsequently imposing complex locking and
 * usage restrictions on that association, you simply define a listener which informs the channel when the listener is
 * ready to handle read or write notifications from the channel.
 * <p/>
 * See {@link java.nio.channels} for more information about channels.
 *
 * @apiviz.exclude java.nio.channels.Channel
 */
package org.jboss.xnio.channels;
