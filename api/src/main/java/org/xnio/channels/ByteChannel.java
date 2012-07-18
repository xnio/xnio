
package org.xnio.channels;

import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

/**
 * An extension of a simple NIO {@link java.nio.channels.ByteChannel} which includes scatter/gather operations.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ByteChannel extends java.nio.channels.ByteChannel, GatheringByteChannel, ScatteringByteChannel {
}
