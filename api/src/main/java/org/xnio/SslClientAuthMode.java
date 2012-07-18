
package org.xnio;

/**
 * The desired SSL client authentication mode for SSL channels in server mode.
 */
public enum SslClientAuthMode {

    /**
     * SSL client authentication is not requested.
     */
    NOT_REQUESTED,
    /**
     * SSL client authentication is requested but not required.
     */
    REQUESTED,
    /**
     * SSL client authentication is required.
     */
    REQUIRED,
}
