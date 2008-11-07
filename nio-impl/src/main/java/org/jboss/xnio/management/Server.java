/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.xnio.management;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.SocketAddress;

import javax.management.ObjectName;

/**
 *
 */
public class Server implements ServerMBean {

    private final ObjectName objectName;
    private final WeakReference<? extends ServerMBean> mBeanRef;

    public Server(final ServerMBean mBean) {
        mBeanRef = new WeakReference<ServerMBean>(mBean);
        objectName = MBeanUtils.getObjectName(mBean.toString());
        MBeanUtils.registerMBean(this, objectName);
    }

    public SocketAddress[] getBindAddresses() {
        ServerMBean bean = mBeanRef.get();
        if (bean != null) {
            return bean.getBindAddresses();
        }
        return null;
    }

    public int getReceiveBufferSize() {
        ServerMBean bean = mBeanRef.get();
        if (bean != null) {
            return bean.getReceiveBufferSize();
        }
        return -1;
    }

    public boolean isReuseAddress() {
        ServerMBean bean = mBeanRef.get();
        if (bean != null) {
            return bean.isReuseAddress();
        }
        return false;
    }

    public void setBindAddresses(final SocketAddress[] bindAddresses) {
        ServerMBean bean = mBeanRef.get();
        if (bean != null) {
            bean.setBindAddresses(bindAddresses);
        }
    }

    public void setReceiveBufferSize(final int receiveBufferSize) {
        ServerMBean bean = mBeanRef.get();
        if (bean != null) {
            bean.setReceiveBufferSize(receiveBufferSize);
        }
    }

    public void setReuseAddress(final boolean reuseAddress) {
        ServerMBean bean = mBeanRef.get();
        if (bean != null) {
            bean.setReuseAddress(reuseAddress);
        }
    }

    public void start() throws IOException {
        ServerMBean bean = mBeanRef.get();
        if (bean != null) {
            bean.start();
        }
    }

    public void stop() throws IOException {
        ServerMBean bean = mBeanRef.get();
        if (bean != null) {
            bean.stop();
        }
    }

    protected ServerMBean getServerMBean() {
        return mBeanRef.get();
    }

    //TODO cannot deregister server
//    public void close() throws IOException{
//        Closeable mBean = mBeanRef.get();
//        mBean.close();
//        MBeanUtils.unregisterMBean(objectName);
//    }
}
