package org.jboss.xnio.log;

import org.jboss.xnio.Version;

import java.util.logging.LogRecord;


/**
 *
 */
public final class Logger {
    private static final class Init {
        static {
            getLogger("org.jboss.xnio").info("XNIO Version " + Version.VERSION);
        }
    }

    public static final class Level extends java.util.logging.Level {
        private static final long serialVersionUID = 2595049467798273809L;

        protected Level(final String name, final int value) {
            super(name, value);
        }
    }

    public static final Level TRACE = new Level("TRACE", 400);
    public static final Level DEBUG = new Level("DEBUG", 500);
    public static final Level INFO = new Level("INFO", 800);
    public static final Level WARN = new Level("WARN", 900);
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

    public static Logger getLogger(final String name) {
        return new Logger(name);
    }

    public static Logger getLogger(final Class claxx) {
        return getLogger(claxx.getName());
    }

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

    public void error(String msg) {
        doLog(ERROR, msg, null, null);
    }

    public void error(Throwable ex, String msg, Object... params) {
        doLog(ERROR, msg, ex, params);
    }

    public void error(String msg, Object... params) {
        doLog(ERROR, msg, null, params);
    }

    public void warn(String msg) {
        doLog(WARN, msg, null, null);
    }

    public void warn(Throwable ex, String msg, Object... params) {
        doLog(WARN, msg, ex, params);
    }

    public void warn(String msg, Object... params) {
        doLog(WARN, msg, null, params);
    }

    public void info(String msg) {
        doLog(INFO, msg, null, null);
    }

    public void info(Throwable ex, String msg, Object... params) {
        doLog(INFO, msg, ex, params);
    }

    public void info(String msg, Object... params) {
        doLog(INFO, msg, null, params);
    }

    public void debug(String msg) {
        doLog(DEBUG, msg, null, null);
    }

    public void debug(Throwable ex, String msg, Object... params) {
        doLog(DEBUG, msg, ex, params);
    }

    public void debug(String msg, Object... params) {
        doLog(DEBUG, msg, null, params);
    }

    public void trace(String msg) {
        doLog(TRACE, msg, null, null);
    }

    public void trace(Throwable ex, String msg, Object... params) {
        doLog(TRACE, msg, ex, params);
    }

    public void trace(String msg, Object... params) {
        doLog(TRACE, msg, null, params);
    }
}
