package org.jboss.xnio;

import java.io.Closeable;
import java.io.IOException;
import java.io.Flushable;

/**
 * A streaming object sink.
 *
 * @param <T> the type of object that this {@code ObjectSink} accepts
 */
public interface ObjectSink<T> extends Flushable, Closeable {
    /**
     * Accept an object.
     *
     * @param instance the object to accept
     * @throws IOException if an I/O error occurs
     */
    void accept(T instance) throws IOException;
}
