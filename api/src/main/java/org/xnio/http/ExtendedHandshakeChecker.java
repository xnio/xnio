package org.xnio.http;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A class that can decide if a HTTP upgrade handshake is valid. If not it should
 * throw an {@link java.io.IOException}
 *
 */
public interface ExtendedHandshakeChecker {

    /**
     * Checks a handshake, and throws an exception if it is invalid
     *
     * @param headers The response headers, keyed by lowercase name
     * @throws java.io.IOException If the handshake is not valid
     */
    void checkHandshakeExtended(final Map<String, List<String>> headers) throws IOException;
}
