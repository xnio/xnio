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

package org.jboss.xnio.test.support;

import java.security.Permission;
import java.io.FileDescriptor;
import java.net.InetAddress;

/**
 *
 */
public final class LoggingSecurityManager extends SecurityManager {

    public LoggingSecurityManager() {
    }

    private static <T extends Throwable> T logged(T se) {
        se.printStackTrace(System.err);
        return se;
    }

    public void checkPermission(final Permission perm) {
        try {
            super.checkPermission(perm);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkPermission(final Permission perm, final Object context) {
        try {
            super.checkPermission(perm, context);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkCreateClassLoader() {
        try {
            super.checkCreateClassLoader();
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkAccess(final Thread t) {
        try {
            super.checkAccess(t);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkAccess(final ThreadGroup g) {
        try {
            super.checkAccess(g);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkExit(final int status) {
        try {
            super.checkExit(status);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkExec(final String cmd) {
        try {
            super.checkExec(cmd);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkLink(final String lib) {
        try {
            super.checkLink(lib);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkRead(final FileDescriptor fd) {
        try {
            super.checkRead(fd);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkRead(final String file) {
        try {
            super.checkRead(file);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkRead(final String file, final Object context) {
        try {
            super.checkRead(file, context);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkWrite(final FileDescriptor fd) {
        try {
            super.checkWrite(fd);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkWrite(final String file) {
        try {
            super.checkWrite(file);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkDelete(final String file) {
        try {
            super.checkDelete(file);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkConnect(final String host, final int port) {
        try {
            super.checkConnect(host, port);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkConnect(final String host, final int port, final Object context) {
        try {
            super.checkConnect(host, port, context);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkListen(final int port) {
        try {
            super.checkListen(port);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkAccept(final String host, final int port) {
        try {
            super.checkAccept(host, port);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkMulticast(final InetAddress maddr) {
        try {
            super.checkMulticast(maddr);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    @Deprecated
    public void checkMulticast(final InetAddress maddr, final byte ttl) {
        try {
            super.checkMulticast(maddr, ttl);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkPropertiesAccess() {
        try {
            super.checkPropertiesAccess();
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkPropertyAccess(final String key) {
        try {
            super.checkPropertyAccess(key);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public boolean checkTopLevelWindow(final Object window) {
        try {
            return super.checkTopLevelWindow(window);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkPrintJobAccess() {
        try {
            super.checkPrintJobAccess();
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkSystemClipboardAccess() {
        try {
            super.checkSystemClipboardAccess();
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkAwtEventQueueAccess() {
        try {
            super.checkAwtEventQueueAccess();
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkPackageAccess(final String pkg) {
        try {
            super.checkPackageAccess(pkg);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkPackageDefinition(final String pkg) {
        try {
            super.checkPackageDefinition(pkg);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkSetFactory() {
        try {
            super.checkSetFactory();
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkMemberAccess(final Class<?> clazz, final int which) {
        try {
            super.checkMemberAccess(clazz, which);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }

    public void checkSecurityAccess(final String target) {
        try {
            super.checkSecurityAccess(target);
        } catch (SecurityException se) {
            throw logged(se);
        }
    }
}
