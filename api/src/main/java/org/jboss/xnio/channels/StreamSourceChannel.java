package org.jboss.xnio.channels;

import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.ByteBuffer;
import java.io.IOException;

/**
 * A stream source channel.  This type of channel is a readable source for bytes.
 */
public interface StreamSourceChannel extends ReadableByteChannel, ScatteringByteChannel, SuspendableReadChannel, Configurable {
    /**
     * Places this readable channel at "end of stream".  Further reads will result in EOF.
     *
     * @throws IOException if an I/O error occurs
     */
    void shutdownReads() throws IOException;
}
