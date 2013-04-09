package org.xnio.http;

import java.io.IOException;
import java.util.Map;

/**
 * A class that can decide if a HTTP upgrade handshake is valid. If not it should
 * throw an {@link java.io.IOException}
 */
public interface HandshakeChecker {

    /**
     * Checks a handshake, and throws an exception if it is invalid
     *
     * @param headers The response headers, keyed by lowercase name
     * @throws java.io.IOException If the handshake is not valid
     */
    void checkHandshake(final Map<String, String> headers) throws IOException;
}
