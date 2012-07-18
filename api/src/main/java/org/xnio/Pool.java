
package org.xnio;

/**
 * A generic pooled resource manager.
 *
 * @param <T> the resource type
 *
 * @apiviz.landmark
 */
public interface Pool<T> {

    /**
     * Allocate a resource from the pool.
     *
     * @return the resource
     * @throws PoolDepletedException if no resources are available
     */
    Pooled<T> allocate() throws PoolDepletedException;
}
