
package org.xnio;

/**
 * An operation which may be cancelled.
 */
public interface Cancellable {

    /**
     * Cancel an operation.  The actual cancel may be synchronous or asynchronous.
     *
     * @return this instance
     */
    Cancellable cancel();
}
