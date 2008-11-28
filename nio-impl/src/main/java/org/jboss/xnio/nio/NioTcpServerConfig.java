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

package org.jboss.xnio.nio;

import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.channels.TcpChannel;
import java.util.concurrent.Executor;
import java.net.SocketAddress;

/**
 *
 */
public final class NioTcpServerConfig {
    private NioXnio xnio;
    private Executor executor;
    private IoHandlerFactory<? super TcpChannel> handlerFactory;
    private SocketAddress[] initialAddresses;
    private Integer backlog;
    private Boolean reuseAddresses;
    private Integer receiveBuffer;
    private Boolean keepAlive;
    private Boolean oobInline;
    private Boolean noDelay;

    public NioXnio getXnio() {
        return xnio;
    }

    public void setXnio(final NioXnio xnio) {
        this.xnio = xnio;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    public IoHandlerFactory<? super TcpChannel> getHandlerFactory() {
        return handlerFactory;
    }

    public void setHandlerFactory(final IoHandlerFactory<? super TcpChannel> handlerFactory) {
        this.handlerFactory = handlerFactory;
    }

    public SocketAddress[] getInitialAddresses() {
        return initialAddresses;
    }

    public void setInitialAddresses(final SocketAddress[] initialAddresses) {
        this.initialAddresses = initialAddresses;
    }

    public Integer getBacklog() {
        return backlog;
    }

    public void setBacklog(final Integer backlog) {
        this.backlog = backlog;
    }

    public Boolean getReuseAddresses() {
        return reuseAddresses;
    }

    public void setReuseAddresses(final Boolean reuseAddresses) {
        this.reuseAddresses = reuseAddresses;
    }

    public Integer getReceiveBuffer() {
        return receiveBuffer;
    }

    public void setReceiveBuffer(final Integer receiveBuffer) {
        this.receiveBuffer = receiveBuffer;
    }

    public Boolean getKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(final Boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public Boolean getOobInline() {
        return oobInline;
    }

    public void setOobInline(final Boolean oobInline) {
        this.oobInline = oobInline;
    }

    public Boolean getNoDelay() {
        return noDelay;
    }

    public void setNoDelay(final Boolean noDelay) {
        this.noDelay = noDelay;
    }
}
