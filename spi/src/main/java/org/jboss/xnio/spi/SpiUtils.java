package org.jboss.xnio.spi;

import org.jboss.xnio.IoHandler;
import org.jboss.xnio.log.Logger;

/**
 * Helpful utility methods for SPI implementations.
 */
public final class SpiUtils {
    private SpiUtils() {}

    private static final Logger log = Logger.getLogger(SpiUtils.class);

    /**
     * Call the handler open method, logging any exceptions that may occur.  Returns {@code true} if the handler
     * completed without error, which can be used to make a decision to close the channel, or ignored if desired.
     *
     * @param handler the handler
     * @param channel the channel
     * @return {@code true} if the handler completed without error.
     */
    public static <T> boolean handleOpened(IoHandler<? super T> handler, T channel) {
        try {
            handler.handleOpened(channel);
            return true;
        } catch (Throwable t) {
            log.error(t, "Channel handler open notification failed for handler %s, channel %s", handler, channel);
            return false;
        }
    }

    /**
     * Call the handler close method, logging any exceptions that may occur.  Returns {@code true} if the handler
     * completed without error, which can be ignored if desired.
     *
     * @param handler the handler
     * @param channel the channel
     * @return {@code true} if the handler completed without error.
     */
    public static <T> boolean handleClosed(IoHandler<? super T> handler, T channel) {
        try {
            handler.handleClosed(channel);
            return true;
        } catch (Throwable t) {
            log.error(t, "Channel handler close notification failed for handler %s, channel %s", handler, channel);
            return false;
        }
    }

    /**
     * Call the handler readable method, logging any exceptions that may occur.  Returns {@code true} if the handler
     * completed without error, which can be used to make a decision to close the channel, or ignored if desired.
     *
     * @param handler the handler
     * @param channel the channel
     * @return {@code true} if the handler completed without error.
     */
    public static <T> boolean handleReadable(IoHandler<? super T> handler, T channel) {
        try {
            handler.handleReadable(channel);
            return true;
        } catch (Throwable t) {
            log.error(t, "Channel handler readable notification failed for handler %s, channel %s", handler, channel);
            return false;
        }
    }

    /**
     * Call the handler writable method, logging any exceptions that may occur.  Returns {@code true} if the handler
     * completed without error, which can be used to make a decision to close the channel, or ignored if desired.
     *
     * @param handler the handler
     * @param channel the channel
     * @return {@code true} if the handler completed without error.
     */
    public static <T> boolean handleWritable(IoHandler<? super T> handler, T channel) {
        try {
            handler.handleWritable(channel);
            return true;
        } catch (Throwable t) {
            log.error(t, "Channel handler writable notification failed for handler %s, channel %s", handler, channel);
            return false;
        }
    }
}
