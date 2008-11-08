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

package org.jboss.xnio.log;

import org.jboss.xnio.Version;

import java.util.logging.LogRecord;


/**
 * A logger that may be used by XNIO applications.
 */
public final class Logger {
    private static final class Init {
        static {
            getLogger("org.jboss.xnio").info("XNIO Version " + Version.VERSION);
        }
    }

    /**
     * A base class for the XNIO custom log levels for JDK logging.
     */
    static final class Level extends java.util.logging.Level {
        private static final long serialVersionUID = 2595049467798273809L;

        protected Level(final String name, final int value) {
            super(name, value);
        }
    }

    /** The TRACE log level. */
    public static final java.util.logging.Level TRACE = new Level("TRACE", 400);
    /** The DEBUG log level. */
    public static final java.util.logging.Level DEBUG = new Level("DEBUG", 500);
    /** The INFO log level. */
    public static final java.util.logging.Level INFO = new Level("INFO", 800);
    /** The WARN log level. */
    public static final java.util.logging.Level WARN = new Level("WARN", 900);
    /** The ERROR log level. */
    public static final java.util.logging.Level ERROR = new Level("ERROR", 1000);

    @SuppressWarnings ({"NonConstantLogger"})
    private final java.util.logging.Logger logger;
    private final String name;

    private Logger(final String name) {
        // Log the main init message exactly once
        Init.class.getName();
        this.name = name;
        logger = java.util.logging.Logger.getLogger(name);
    }

    /**
     * Get a logger with the given name.
     *
     * @param name the logger name
     * @return the matching logger
     */
    public static Logger getLogger(final String name) {
        return new Logger(name);
    }

    /**
     * Get a logger whose name is the same as the fully qualified name of the given class.
     *
     * @param claxx the class name
     * @return the matching logger
     */
    public static Logger getLogger(final Class claxx) {
        return getLogger(claxx.getName());
    }

    /**
     * Test to see if the logger would log trace messages.
     *
     * @return {@code true} if trace messages may be logged
     */
    public boolean isTrace() {
        return logger.isLoggable(TRACE);
    }

    private void doLog(java.util.logging.Level level, String msg, Throwable ex, Object... params) {
        try {
            final String fmtMsg;
            if (params != null && params.length > 0) {
                fmtMsg = String.format(msg, params);
            } else {
                fmtMsg = msg;
            }
            LogRecord record = new LogRecord(level, fmtMsg);
            record.setLoggerName(name);
            if (ex != null) record.setThrown(ex);
            record.setSourceMethodName("");
            record.setSourceClassName("");
            logger.log(record);
        } catch (Throwable t) {
            // ignore it, I guess...
        }
    }

    /**
     * Log a message at error level.
     *
     * @param msg the message to log
     */
    public void error(String msg) {
        if (logger.isLoggable(ERROR)) {
            doLog(ERROR, msg, null, (Object[]) null);
        }
    }

    /**
     * Log a message at error level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param params the message parameters
     */
    public void error(Throwable ex, String msg, Object... params) {
        if (logger.isLoggable(ERROR)) {
            doLog(ERROR, msg, ex, params);
        }
    }

    /**
     * Log a message at error level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param param1 the first message parameter
     */
    public void error(Throwable ex, String msg, Object param1) {
        if (logger.isLoggable(ERROR)) {
            doLog(ERROR, msg, ex, param1);
        }
    }

    /**
     * Log a message at error level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     */
    public void error(Throwable ex, String msg, Object param1, Object param2) {
        if (logger.isLoggable(ERROR)) {
            doLog(ERROR, msg, ex, param1, param2);
        }
    }

    /**
     * Log a message at error level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     * @param param3 the third message parameter
     */
    public void error(Throwable ex, String msg, Object param1, Object param2, Object param3) {
        if (logger.isLoggable(ERROR)) {
            doLog(ERROR, msg, ex, param1, param2, param3);
        }
    }

    /**
     * Log a message at error level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param params the message parameters
     */
    public void error(String msg, Object... params) {
        if (logger.isLoggable(ERROR)) {
            doLog(ERROR, msg, null, params);
        }
    }

    /**
     * Log a message at error level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param param1 the first message parameter
     */
    public void error(String msg, Object param1) {
        if (logger.isLoggable(ERROR)) {
            doLog(ERROR, msg, null, param1);
        }
    }

    /**
     * Log a message at error level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     */
    public void error(String msg, Object param1, Object param2) {
        if (logger.isLoggable(ERROR)) {
            doLog(ERROR, msg, null, param1, param2);
        }
    }

    /**
     * Log a message at error level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     * @param param3 the third message parameter
     */
    public void error(String msg, Object param1, Object param2, Object param3) {
        if (logger.isLoggable(ERROR)) {
            doLog(ERROR, msg, null, param1, param2, param3);
        }
    }

    /**
     * Log a message at warn level.
     *
     * @param msg the message to log
     */
    public void warn(String msg) {
        if (logger.isLoggable(WARN)) {
            doLog(WARN, msg, null, (Object[]) null);
        }
    }

    /**
     * Log a message at warn level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param params the message parameters
     */
    public void warn(Throwable ex, String msg, Object... params) {
        if (logger.isLoggable(WARN)) {
            doLog(WARN, msg, ex, params);
        }
    }

    /**
     * Log a message at warn level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param param1 the first message parameter
     */
    public void warn(Throwable ex, String msg, Object param1) {
        if (logger.isLoggable(WARN)) {
            doLog(WARN, msg, ex, param1);
        }
    }

    /**
     * Log a message at warn level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     */
    public void warn(Throwable ex, String msg, Object param1, Object param2) {
        if (logger.isLoggable(WARN)) {
            doLog(WARN, msg, ex, param1, param2);
        }
    }

    /**
     * Log a message at warn level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     * @param param3 the third message parameter
     */
    public void warn(Throwable ex, String msg, Object param1, Object param2, Object param3) {
        if (logger.isLoggable(WARN)) {
            doLog(WARN, msg, ex, param1, param2, param3);
        }
    }

    /**
     * Log a message at warn level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param params the message parameters
     */
    public void warn(String msg, Object... params) {
        if (logger.isLoggable(WARN)) {
            doLog(WARN, msg, null, params);
        }
    }

    /**
     * Log a message at warn level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param param1 the first message parameter
     */
    public void warn(String msg, Object param1) {
        if (logger.isLoggable(WARN)) {
            doLog(WARN, msg, null, param1);
        }
    }

    /**
     * Log a message at warn level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     */
    public void warn(String msg, Object param1, Object param2) {
        if (logger.isLoggable(WARN)) {
            doLog(WARN, msg, null, param1, param2);
        }
    }

    /**
     * Log a message at warn level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     * @param param3 the third message parameter
     */
    public void warn(String msg, Object param1, Object param2, Object param3) {
        if (logger.isLoggable(WARN)) {
            doLog(WARN, msg, null, param1, param2, param3);
        }
    }

    /**
     * Log a message at info level.
     *
     * @param msg the message to log
     */
    public void info(String msg) {
        if (logger.isLoggable(INFO)) {
            doLog(INFO, msg, null, (Object[]) null);
        }
    }

    /**
     * Log a message at info level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param params the message parameters
     */
    public void info(Throwable ex, String msg, Object... params) {
        if (logger.isLoggable(INFO)) {
            doLog(INFO, msg, ex, params);
        }
    }

    /**
     * Log a message at info level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param param1 the first message parameter
     */
    public void info(Throwable ex, String msg, Object param1) {
        if (logger.isLoggable(INFO)) {
            doLog(INFO, msg, ex, param1);
        }
    }

    /**
     * Log a message at info level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     */
    public void info(Throwable ex, String msg, Object param1, Object param2) {
        if (logger.isLoggable(INFO)) {
            doLog(INFO, msg, ex, param1, param2);
        }
    }

    /**
     * Log a message at info level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     * @param param3 the third message parameter
     */
    public void info(Throwable ex, String msg, Object param1, Object param2, Object param3) {
        if (logger.isLoggable(INFO)) {
            doLog(INFO, msg, ex, param1, param2, param3);
        }
    }

    /**
     * Log a message at info level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param params the message parameters
     */
    public void info(String msg, Object... params) {
        if (logger.isLoggable(INFO)) {
            doLog(INFO, msg, null, params);
        }
    }

    /**
     * Log a message at info level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param param1 the first message parameter
     */
    public void info(String msg, Object param1) {
        if (logger.isLoggable(INFO)) {
            doLog(INFO, msg, null, param1);
        }
    }

    /**
     * Log a message at info level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     */
    public void info(String msg, Object param1, Object param2) {
        if (logger.isLoggable(INFO)) {
            doLog(INFO, msg, null, param1, param2);
        }
    }

    /**
     * Log a message at info level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     * @param param3 the third message parameter
     */
    public void info(String msg, Object param1, Object param2, Object param3) {
        if (logger.isLoggable(INFO)) {
            doLog(INFO, msg, null, param1, param2, param3);
        }
    }

    /**
     * Log a message at debug level.
     *
     * @param msg the message to log
     */
    public void debug(String msg) {
        if (logger.isLoggable(DEBUG)) {
            doLog(DEBUG, msg, null, (Object[]) null);
        }
    }

    /**
     * Log a message at debug level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param params the message parameters
     */
    public void debug(Throwable ex, String msg, Object... params) {
        if (logger.isLoggable(DEBUG)) {
            doLog(DEBUG, msg, ex, params);
        }
    }

    /**
     * Log a message at debug level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param param1 the first message parameter
     */
    public void debug(Throwable ex, String msg, Object param1) {
        if (logger.isLoggable(DEBUG)) {
            doLog(DEBUG, msg, ex, param1);
        }
    }

    /**
     * Log a message at debug level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     */
    public void debug(Throwable ex, String msg, Object param1, Object param2) {
        if (logger.isLoggable(DEBUG)) {
            doLog(DEBUG, msg, ex, param1, param2);
        }
    }

    /**
     * Log a message at debug level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     * @param param3 the third message parameter
     */
    public void debug(Throwable ex, String msg, Object param1, Object param2, Object param3) {
        if (logger.isLoggable(DEBUG)) {
            doLog(DEBUG, msg, ex, param1, param2, param3);
        }
    }

    /**
     * Log a message at debug level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param params the message parameters
     */
    public void debug(String msg, Object... params) {
        if (logger.isLoggable(DEBUG)) {
            doLog(DEBUG, msg, null, params);
        }
    }

    /**
     * Log a message at debug level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param param1 the first message parameter
     */
    public void debug(String msg, Object param1) {
        if (logger.isLoggable(DEBUG)) {
            doLog(DEBUG, msg, null, param1);
        }
    }

    /**
     * Log a message at debug level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     */
    public void debug(String msg, Object param1, Object param2) {
        if (logger.isLoggable(DEBUG)) {
            doLog(DEBUG, msg, null, param1, param2);
        }
    }

    /**
     * Log a message at debug level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     * @param param3 the third message parameter
     */
    public void debug(String msg, Object param1, Object param2, Object param3) {
        if (logger.isLoggable(DEBUG)) {
            doLog(DEBUG, msg, null, param1, param2, param3);
        }
    }

    /**
     * Log a message at trace level.
     *
     * @param msg the message to log
     */
    public void trace(String msg) {
        if (logger.isLoggable(TRACE)) {
            doLog(TRACE, msg, null, (Object[]) null);
        }
    }

    /**
     * Log a message at trace level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param params the message parameters
     */
    public void trace(Throwable ex, String msg, Object... params) {
        if (logger.isLoggable(TRACE)) {
            doLog(TRACE, msg, ex, params);
        }
    }

    /**
     * Log a message at trace level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param param1 the first message parameter
     */
    public void trace(Throwable ex, String msg, Object param1) {
        if (logger.isLoggable(TRACE)) {
            doLog(TRACE, msg, ex, param1);
        }
    }

    /**
     * Log a message at trace level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     */
    public void trace(Throwable ex, String msg, Object param1, Object param2) {
        if (logger.isLoggable(TRACE)) {
            doLog(TRACE, msg, ex, param1, param2);
        }
    }

    /**
     * Log a message at trace level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     * @param param3 the third message parameter
     */
    public void trace(Throwable ex, String msg, Object param1, Object param2, Object param3) {
        if (logger.isLoggable(TRACE)) {
            doLog(TRACE, msg, ex, param1, param2, param3);
        }
    }

    /**
     * Log a message at trace level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param params the message parameters
     */
    public void trace(String msg, Object... params) {
        if (logger.isLoggable(TRACE)) {
            doLog(TRACE, msg, null, params);
        }
    }

    /**
     * Log a message at trace level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param param1 the first message parameter
     */
    public void trace(String msg, Object param1) {
        if (logger.isLoggable(TRACE)) {
            doLog(TRACE, msg, null, param1);
        }
    }

    /**
     * Log a message at trace level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     */
    public void trace(String msg, Object param1, Object param2) {
        if (logger.isLoggable(TRACE)) {
            doLog(TRACE, msg, null, param1, param2);
        }
    }

    /**
     * Log a message at trace level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param param1 the first message parameter
     * @param param2 the second message parameter
     * @param param3 the third message parameter
     */
    public void trace(String msg, Object param1, Object param2, Object param3) {
        if (logger.isLoggable(TRACE)) {
            doLog(TRACE, msg, null, param1, param2, param3);
        }
    }
}
