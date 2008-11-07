/**
 *
 */
package org.jboss.xnio.management;

import java.io.Closeable;



/**
 * @author robhadfield
 *
 */
public abstract class WritableReadableChannel extends Channel{

    public WritableReadableChannel(final Closeable mBean) {
        super(mBean);
    }

    public WritableReadableChannel(final Closeable mBean, final Object object) {
        this(mBean, new Object[] { object } );
    }

    public WritableReadableChannel(final Closeable mBean, final Object[] objects) {
        super(mBean, objects);
    }
}
