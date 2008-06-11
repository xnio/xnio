package org.jboss.xnio.test.support;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.Handler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 *
 */
public final class LoggingHelper {
    private static final class Once {
        static {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    final Logger rootLogger = Logger.getLogger("");
                    rootLogger.setLevel(Level.ALL);
                    final Handler[] handlers = rootLogger.getHandlers();
                    for (Handler handler : handlers) {
                        handler.setLevel(Level.ALL);
                        handler.setFormatter(new Formatter() {
                            public String format(final LogRecord record) {
                                StringBuilder builder = new StringBuilder();
                                builder.append(record.getLevel().toString());
                                builder.append(" [").append(record.getLoggerName()).append("] ");
                                builder.append(String.format(record.getMessage(), record.getParameters()));
                                Throwable t = record.getThrown();
                                while (t != null) {
                                    builder.append("\n    Caused by: ");
                                    builder.append(t.getClass().getName());
                                    builder.append(": ");
                                    builder.append(t.getMessage());
                                    for (StackTraceElement e : t.getStackTrace()) {
                                        builder.append("\n        at ");
                                        builder.append(e.getClassName()).append('.').append(e.getMethodName());
                                        builder.append("(").append(e.getFileName()).append(':').append(e.getLineNumber()).append(')');
                                    }
                                    t = t.getCause();
                                }
                                builder.append('\n');
                                return builder.toString();
                            }
                        });
                    }
                    return null;
                }
            });
        }
    }

    private LoggingHelper() {
    }

    public static void init() {
        new Once();
    }
}
