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
    public static final class Level extends java.util.logging.Level {
        private static final long serialVersionUID = 2595049467798273809L;

        protected Level(final String name, final int value) {
            super(name, value);
        }
    }

    /** The TRACE log level. */
    public static final Level TRACE = new Level("TRACE", 400);
    /** The DEBUG log level. */
    public static final Level DEBUG = new Level("DEBUG", 500);
    /** The INFO log level. */
    public static final Level INFO = new Level("INFO", 800);
    /** The WARN log level. */
    public static final Level WARN = new Level("WARN", 900);
    /** The ERROR log level. */
    public static final Level ERROR = new Level("ERROR", 1000);

    @SuppressWarnings ({"NonConstantLogger"})
    private final java.util.logging.Logger logger;
    private final String name;

    private Logger(final String name) {
        // Log the main init message exactly once
        final Class<Init> x = Init.class;
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

    private void doLog(Level level, String msg, Throwable ex, Object[] params) {
        if (logger.isLoggable(level)) {
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
        }
    }

    /**
     * Log a message at error level.
     *
     * @param msg the message to log
     */
    public void error(String msg) {
        doLog(ERROR, msg, null, null);
    }

    /**
     * Log a message at error level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param params the message parameters
     */
    public void error(Throwable ex, String msg, Object... params) {
        doLog(ERROR, msg, ex, params);
    }

    /**
     * Log a message at error level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param params the message parameters
     */
    public void error(String msg, Object... params) {
        doLog(ERROR, msg, null, params);
    }

    /**
     * Log a message at warn level.
     *
     * @param msg the message to log
     */
    public void warn(String msg) {
        doLog(WARN, msg, null, null);
    }

    /**
     * Log a message at warn level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param params the message parameters
     */
    public void warn(Throwable ex, String msg, Object... params) {
        doLog(WARN, msg, ex, params);
    }

    /**
     * Log a message at warn level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param params the message parameters
     */
    public void warn(String msg, Object... params) {
        doLog(WARN, msg, null, params);
    }

    /**
     * Log a message at info level.
     *
     * @param msg the message to log
     */
    public void info(String msg) {
        doLog(INFO, msg, null, null);
    }

    /**
     * Log a message at info level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param params the message parameters
     */
    public void info(Throwable ex, String msg, Object... params) {
        doLog(INFO, msg, ex, params);
    }

    /**
     * Log a message at info level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param params the message parameters
     */
    public void info(String msg, Object... params) {
        doLog(INFO, msg, null, params);
    }

    /**
     * Log a message at debug level.
     *
     * @param msg the message to log
     */
    public void debug(String msg) {
        doLog(DEBUG, msg, null, null);
    }

    /**
     * Log a message at debug level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param params the message parameters
     */
    public void debug(Throwable ex, String msg, Object... params) {
        doLog(DEBUG, msg, ex, params);
    }

    /**
     * Log a message at debug level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param params the message parameters
     */
    public void debug(String msg, Object... params) {
        doLog(DEBUG, msg, null, params);
    }

    /**
     * Log a message at trace level.
     *
     * @param msg the message to log
     */
    public void trace(String msg) {
        doLog(TRACE, msg, null, null);
    }

    /**
     * Log a message at trace level with an exception.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param ex the exception to log
     * @param msg the message to log
     * @param params the message parameters
     */
    public void trace(Throwable ex, String msg, Object... params) {
        doLog(TRACE, msg, ex, params);
    }

    /**
     * Log a message at trace level.  The message will be formatted using {@link String#format(String, Object[])}.
     *
     * @param msg the message to log
     * @param params the message parameters
     */
    public void trace(String msg, Object... params) {
        doLog(TRACE, msg, null, params);
    }
}
