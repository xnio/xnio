/**
 *
 */
package org.jboss.xnio.management;

import java.io.Closeable;

/**
 * @author robhadfield
 *
 */
public interface WritableChannelMBean extends Closeable{

    long getBytesWritten();

    long getMessagesWritten();

}
