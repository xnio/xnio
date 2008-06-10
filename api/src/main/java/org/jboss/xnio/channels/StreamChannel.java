package org.jboss.xnio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A stream channel.  This type of channel represents a stream of bytes flowing in two directions.
 */
public interface StreamChannel extends SuspendableChannel, StreamSinkChannel, StreamSourceChannel, Configurable {
}
