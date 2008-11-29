/**
 *
 */
package org.jboss.xnio.management;

/**
 * @author robhadfield
 *
 */
public interface WritableChannelMBean {

    long getBytesWritten();

    long getMessagesWritten();
}
