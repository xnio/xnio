/**
 *
 */
package org.jboss.xnio.management;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;

import javax.management.ObjectName;

/**
 *
 */
abstract class BasicCounters implements BasicCountersMBean{

    private volatile long bytesRead;
    private volatile long bytesWritten;
    private volatile long messagesRead;
    private volatile long messagesWritten;
    private final ObjectName objectName;
    private final WeakReference<? extends Closeable> mBeanRef;

    @SuppressWarnings("unchecked")
    public BasicCounters(final Closeable mBean, final String objectName) {
        mBeanRef = new WeakReference(mBean);
        this.objectName = MBeanUtils.getObjectName(objectName);
        MBeanUtils.registerMBean(this, this.objectName);
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    public void bytesRead(final long byteCount) {
        bytesRead+=byteCount;
        messagesRead++;
    }

    public void bytesWritten(final long byteCount) {
        bytesWritten+=byteCount;
        messagesWritten++;
    }

    public Long getBytesRead() {
        return bytesRead;
    }

    public Long getBytesWritten() {
        return bytesWritten;
    }

    public Long getMessagesRead() {
        return messagesRead;
    }

    public Long getMessagesWritten() {
        return messagesWritten;
    }

    public void close() throws IOException{
        Closeable mBean = mBeanRef.get();
        mBean.close();
        MBeanUtils.unregisterMBean(this.objectName);
    }
}
