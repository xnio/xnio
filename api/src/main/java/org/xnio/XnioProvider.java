
package org.xnio;

/**
 * An XNIO provider, used by the service loader discovery mechanism.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface XnioProvider {

    /**
     * Get the XNIO instance for this provider.
     *
     * @return the XNIO instance
     */
    Xnio getInstance();

    /**
     * Get the provider name.
     *
     * @return the name
     */
    String getName();
}
