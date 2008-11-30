/**
 *
 */
package org.jboss.xnio.management;

/**
 * @author robhadfield
 *
 */
public interface WritableChannelStats {

    long getBytesWritten();

    long getMessagesWritten();
}
