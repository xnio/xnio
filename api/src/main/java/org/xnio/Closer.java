
package org.xnio;

import java.io.Closeable;

/**
 * A {@code Runnable} that closes some resource.
 *
 * @apiviz.exclude
 */
public final class Closer implements Runnable {
    private final Closeable resource;

    public Closer(final Closeable resource) {
        this.resource = resource;
    }

    public void run() {
        IoUtils.safeClose(resource);
    }
}
