package org.xnio.http;

import java.io.IOException;

/**
 * Exception that is thrown when a valid HTTP response is
 * received that is not an upgrade or redirect response.
 *
 * @author Stuart Douglas
 */
public class UpgradeFailedException extends IOException {

    public UpgradeFailedException() {
    }

    public UpgradeFailedException(String message) {
        super(message);
    }

    public UpgradeFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public UpgradeFailedException(Throwable cause) {
        super(cause);
    }
}
