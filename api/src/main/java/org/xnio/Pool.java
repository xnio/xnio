
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
     */
    Pooled<T> allocate();
}
