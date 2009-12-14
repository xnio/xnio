/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

import java.util.logging.LogRecord;
import java.util.logging.Level;

class XnioLogRecord extends LogRecord {

    private static final long serialVersionUID = 542119905844866161L;
    private boolean resolved;
    private static final String LOGGER_CLASS_NAME = Logger.class.getName();

    XnioLogRecord(final Level level, final String msg) {
        super(level, msg);
    }

    public String getSourceClassName() {
        if (! resolved) {
            resolve();
        }
        return super.getSourceClassName();
    }

    public void setSourceClassName(final String sourceClassName) {
        resolved = true;
        super.setSourceClassName(sourceClassName);
    }

    public String getSourceMethodName() {
        if (! resolved) {
            resolve();
        }
        return super.getSourceMethodName();
    }

    public void setSourceMethodName(final String sourceMethodName) {
        resolved = true;
        super.setSourceMethodName(sourceMethodName);
    }

    private void resolve() {
        resolved = true;
        final StackTraceElement[] stack = new Throwable().getStackTrace();
        boolean found = false;
        for (StackTraceElement element : stack) {
            final String className = element.getClassName();
            if (found) {
                if (! LOGGER_CLASS_NAME.equals(className)) {
                    setSourceClassName(className);
                    setSourceMethodName(element.getMethodName());
                    return;
                }
            } else {
                found = LOGGER_CLASS_NAME.equals(className);
            }
        }
        setSourceClassName("<unknown>");
        setSourceMethodName("<unknown>");
    }

    protected Object writeReplace() {
        final LogRecord replacement = new LogRecord(getLevel(), getMessage());
        replacement.setResourceBundle(getResourceBundle());
        replacement.setLoggerName(getLoggerName());
        replacement.setMillis(getMillis());
        replacement.setParameters(getParameters());
        replacement.setResourceBundleName(getResourceBundleName());
        replacement.setSequenceNumber(getSequenceNumber());
        replacement.setSourceClassName(getSourceClassName());
        replacement.setSourceMethodName(getSourceMethodName());
        replacement.setThreadID(getThreadID());
        replacement.setThrown(getThrown());
        return replacement;
    }
}
