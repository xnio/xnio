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

package org.jboss.xnio.spi;

/**
 * An XNIO provider.
 */
public interface Provider extends ExecutorUser, Lifecycle {
    /**
     * Create a TCP server instance.
     *
     * @return a TCP server
     */
    TcpServerService createTcpServer();

    /**
     * Create a TCP connector instance.
     *
     * @return a TCP connector
     */
    TcpConnectorService createTcpConnector();

    /**
     * Create a UDP server instance.
     *
     * @return a UDP server
     */
    UdpServerService createUdpServer();

    /**
     * Create a multicast-capable UDP server instance.
     *
     * @return a UDP server
     */
    UdpServerService createMulticastUdpServer();

    /**
     * Create a pipe instance.
     *
     * @return a pipe
     */
    PipeService createPipe();

    /**
     * Create a one-way pipe instance.
     *
     * @return a one-way pipe
     */
    OneWayPipeService createOneWayPipe();
}
