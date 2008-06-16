/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.xnio.spi;

import org.jboss.xnio.IoHandler;
import org.jboss.xnio.log.Logger;

/**
 * Helpful utility methods for SPI implementations.
 */
public final class SpiUtils {
    private SpiUtils() {}

    private static final Logger handlerErrorLog = Logger.getLogger("org.jboss.xnio.handler-errors");

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
            handlerErrorLog.error(t, "Channel handler open notification failed for handler %s, channel %s", handler, channel);
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
            handlerErrorLog.error(t, "Channel handler close notification failed for handler %s, channel %s", handler, channel);
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
            handlerErrorLog.error(t, "Channel handler readable notification failed for handler %s, channel %s", handler, channel);
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
            handlerErrorLog.error(t, "Channel handler writable notification failed for handler %s, channel %s", handler, channel);
            return false;
        }
    }
}
