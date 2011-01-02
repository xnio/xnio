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

package org.xnio.test.support;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.io.IOException;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.Handler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;

/**
 *
 */
public final class LoggingHelper {
    private static final long startTime = System.currentTimeMillis();

    private static final class Once {
        static {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    final Logger rootLogger = Logger.getLogger("");
                    rootLogger.setLevel(Level.ALL);
                    final Handler[] handlers = rootLogger.getHandlers();
                    for (Handler handler : handlers) {
                        rootLogger.removeHandler(handler);
                    }
                    final Formatter formatter = new Formatter() {
                        public String format(final LogRecord record) {
                            StringBuilder builder = new StringBuilder();
                            long offs = record.getMillis() - startTime;
                            final String sign = offs < 0 ? "-" : "+";
                            offs = Math.abs(offs);
                            int ms = (int) (offs % 1000L);
                            long s = offs / 1000L;
                            builder.append(String.format("%s%04d.%03d ", sign, Long.valueOf(s), Long.valueOf(ms)));
                            builder.append(String.format("tid:%d ", Integer.valueOf(record.getThreadID())));
                            builder.append(record.getLevel().toString());
                            builder.append(" [").append(record.getLoggerName()).append("] ");
                            builder.append(String.format(record.getMessage(), record.getParameters()));
                            Throwable t = record.getThrown();
                            while (t != null) {
                                builder.append("\n    Caused by: ");
                                builder.append(t.toString());
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
                    };
                    final Handler handler = new ConsoleHandler() {
                        @Override
                        public void publish(final LogRecord record) {
                            super.publish(record);
                            flush();
                        }
                    };
                    handler.setFormatter(formatter);
                    handler.setLevel(Level.ALL);
                    rootLogger.addHandler(handler);
                    if (false) try {
                        final FileHandler fileHandler = new FileHandler("test-output.log", true) {
                            @Override
                            public void publish(final LogRecord record) {
                                super.publish(record);
                                flush();
                            }
                        };
                        fileHandler.setFormatter(formatter);
                        fileHandler.setLevel(Level.ALL);
                        rootLogger.addHandler(fileHandler);
                    } catch (IOException e) {
                        // oh well
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
