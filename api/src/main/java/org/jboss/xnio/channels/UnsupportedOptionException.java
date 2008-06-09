package org.jboss.xnio.channels;

/**
 * An exception that is thrown when an invalid option is specified for a {@link org.jboss.xnio.channels.ConfigurableChannel}.
 */
public final class UnsupportedOptionException extends IllegalArgumentException {
    private static final long serialVersionUID = 250195510855241708L;

    /**
     * Construct a {@code UnsupportedOptionException} instance.
     */
    public UnsupportedOptionException() {
    }

    /**
     * Construct a {@code UnsupportedOptionException} instance with the given message.
     *
     * @param message the message
     */
    public UnsupportedOptionException(final String message) {
        super(message);
    }

    /**
     * Construct a {@code UnsupportedOptionException} instance with the given message and cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public UnsupportedOptionException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Construct a {@code UnsupportedOptionException} instance with the given cause.
     *
     * @param cause the cause
     */
    public UnsupportedOptionException(final Throwable cause) {
        super(cause);
    }
}
