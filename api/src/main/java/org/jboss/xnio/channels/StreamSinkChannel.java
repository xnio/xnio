package org.jboss.xnio.channels;

import java.nio.channels.WritableByteChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.ByteBuffer;
import java.io.IOException;

/**
 * A stream sink channel.  This type of channel is a writable desination for bytes.
 */
public interface StreamSinkChannel extends WritableByteChannel, GatheringByteChannel, SuspendableWriteChannel {
    /**
     * Indicate that writing is complete for this channel.  Further attempts to write after shutdown will result in an
     * exception.
     *
     * @throws IOException if an I/O error occurs
     */
    void shutdownWrites() throws IOException;
}
