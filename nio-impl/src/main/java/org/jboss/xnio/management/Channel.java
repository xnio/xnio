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
package org.jboss.xnio.management;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

import javax.management.ObjectName;

/**
 *
 */
abstract class Channel implements ChannelMBean{

    private volatile long bytesRead;
    private volatile long bytesWritten;
    private volatile long messagesRead;
    private volatile long messagesWritten;
    private final ObjectName objectName;
    private final WeakReference<? extends Closeable> mBeanRef;

    @SuppressWarnings("unchecked")
    public Channel(final Closeable mBean) {
        mBeanRef = new WeakReference(mBean);
        objectName = MBeanUtils.getObjectName(mBean.toString());
        MBeanUtils.registerMBean(this, objectName);
    }

    @SuppressWarnings("unchecked")
    public Channel(final Closeable mBean, final Object object) {
        this(mBean, new Object[] { object } );
    }

    @SuppressWarnings("unchecked")
    public Channel(final Closeable mBean, final Object[] objects) {
        mBeanRef = new WeakReference(mBean);
        List<NameValuePair> additionalAttributes = AttributeResolver.getAdditionalAttributes(objects);
        objectName = MBeanUtils.getObjectName(mBean.toString(), additionalAttributes);
        MBeanUtils.registerMBean(this, objectName);
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    public void bytesRead(final long byteCount) {
        if (byteCount > 0) {
            bytesRead+=byteCount;
            messagesRead++;
        }
    }

    public void bytesWritten(final long byteCount) {
        if (byteCount > 0) {
            bytesWritten+=byteCount;
            messagesWritten++;
        }
    }

    public long getBytesRead() {
        return bytesRead;
    }

    public long getBytesWritten() {
        return bytesWritten;
    }

    public long getMessagesRead() {
        return messagesRead;
    }

    public long getMessagesWritten() {
        return messagesWritten;
    }

    public void close() throws IOException{
        Closeable mBean = mBeanRef.get();
        mBean.close();
        MBeanUtils.unregisterMBean(objectName);
    }
}
