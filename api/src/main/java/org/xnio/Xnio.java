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

package org.xnio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;
import java.util.EnumMap;
import java.util.ServiceLoader;

import org.jboss.logging.Logger;
import org.xnio.ssl.JsseXnioSsl;
import org.xnio.ssl.XnioSsl;

/**
 * The XNIO provider class.
 *
 * @apiviz.landmark
 */
@SuppressWarnings("unused")
public abstract class Xnio {

    static final InetSocketAddress ANY_INET_ADDRESS = new InetSocketAddress(0);
    static final LocalSocketAddress ANY_LOCAL_ADDRESS = new LocalSocketAddress("");

    private static final EnumMap<FileAccess, OptionMap> FILE_ACCESS_OPTION_MAPS;

    private static final RuntimePermission ALLOW_BLOCKING_SETTING = new RuntimePermission("changeThreadBlockingSetting");

    static {
        Logger.getLogger("org.xnio").info("XNIO Version " + Version.VERSION);
        final EnumMap<FileAccess, OptionMap> map = new EnumMap<FileAccess, OptionMap>(FileAccess.class);
        for (FileAccess access : FileAccess.values()) {
            map.put(access, OptionMap.create(Options.FILE_ACCESS, access));
        }
        FILE_ACCESS_OPTION_MAPS = map;
    }

    /**
     * The name of this provider instance.
     */
    private final String name;

    /**
     * Construct an XNIO provider instance.  Used by implementors only.  To get an XNIO instance,
     * use one of the {@link org.xnio.Xnio#getInstance()} methods.
     *
     * @param name the provider name
     */
    protected Xnio(String name) {
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        this.name = name;
    }

    private static final ThreadLocal<Boolean> BLOCKING = new ThreadLocal<Boolean>() {
        protected Boolean initialValue() {
            return Boolean.TRUE;
        }
    };

    /**
     * Allow (or disallow) blocking I/O on the current thread.  Requires the {@code changeThreadBlockingSetting}
     * {@link RuntimePermission}.
     *
     * @param newSetting {@code true} to allow blocking I/O, {@code false} to disallow it
     * @return the previous setting
     * @throws SecurityException if a security manager is present and disallows changing the {@code changeThreadBlockingSetting} {@code RuntimePermission}
     */
    public static boolean allowBlocking(boolean newSetting) throws SecurityException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ALLOW_BLOCKING_SETTING);
        }
        final ThreadLocal<Boolean> threadLocal = BLOCKING;
        try {
            return threadLocal.get().booleanValue();
        } finally {
            threadLocal.set(Boolean.valueOf(newSetting));
        }
    }

    /**
     * Determine whether blocking I/O is allowed from the current thread.
     *
     * @return {@code true} if blocking I/O is allowed, {@code false} otherwise
     */
    public static boolean isBlockingAllowed() {
        return BLOCKING.get().booleanValue();
    }

    /**
     * Perform a check for whether blocking is allowed on the current thread.
     *
     * @throws IllegalStateException if blocking is not allowed on the current thread
     */
    public static void checkBlockingAllowed() throws IllegalStateException {
        if (! BLOCKING.get().booleanValue()) {
            throw new IllegalStateException("Blocking I/O is not allowed on the current thread");
        }
    }

    /**
     * Get an XNIO provider instance.  If multiple providers are
     * available, use the first one encountered.
     *
     * @param classLoader the class loader to search in
     * @return the XNIO provider instance
     *
     * @since 3.0
     */
    public static Xnio getInstance(ClassLoader classLoader) {
        return doGetInstance(null, ServiceLoader.load(XnioProvider.class, classLoader));
    }

    /**
     * Get an XNIO provider instance from XNIO's class loader.  If multiple providers are
     * available, use the first one encountered.
     *
     * @return the XNIO provider instance
     *
     * @since 3.0
     */
    public static Xnio getInstance() {
        return doGetInstance(null, ServiceLoader.load(XnioProvider.class, Xnio.class.getClassLoader()));
    }

    /**
     * Get a specific XNIO provider instance.
     *
     * @param provider the provider name, or {@code null} for the first available
     * @param classLoader the class loader to search in
     * @return the XNIO provider instance
     *
     * @since 3.0
     */
    public static Xnio getInstance(String provider, ClassLoader classLoader) {
        return doGetInstance(provider, ServiceLoader.load(XnioProvider.class, classLoader));
    }

    /**
     * Get a specific XNIO provider instance from XNIO's class loader.
     *
     * @param provider the provider name, or {@code null} for the first available
     * @return the XNIO provider instance
     *
     * @since 3.0
     */
    public static Xnio getInstance(String provider) {
        return doGetInstance(provider, ServiceLoader.load(XnioProvider.class, Xnio.class.getClassLoader()));
    }

    private static Xnio doGetInstance(final String provider, final ServiceLoader<XnioProvider> serviceLoader) {
        for (XnioProvider xnioProvider : serviceLoader) {
            if (provider == null || provider.equals(xnioProvider.getName())) {
                return xnioProvider.getInstance();
            }
        }
        throw new IllegalArgumentException("No matching XNIO provider found");
    }

    //==================================================
    //
    // SSL methods
    //
    //==================================================

    /**
     * Get an SSL provider for this XNIO provider.
     *
     * @param optionMap the option map to use for configuring SSL
     * @return the SSL provider
     * @throws GeneralSecurityException if an exception occurred configuring the SSL provider
     */
    public XnioSsl getSslProvider(final OptionMap optionMap) throws GeneralSecurityException {
        return new JsseXnioSsl(this, optionMap);
    }

    //==================================================
    //
    // File system methods
    //
    //==================================================

    /**
     * Open a file on the filesystem.
     *
     * @param file the file to open
     * @param options the file-open options
     * @return the file channel
     * @throws IOException if an I/O error occurs
     */
    public FileChannel openFile(File file, OptionMap options) throws IOException {
        switch (options.get(Options.FILE_ACCESS, FileAccess.READ_WRITE)) {
            case READ_ONLY: return new XnioFileChannel(new RandomAccessFile(file, "r").getChannel());
            case READ_WRITE: return new XnioFileChannel(new RandomAccessFile(file, "rw").getChannel());
            default: throw new IllegalStateException();
        }
    }

    /**
     * Open a file on the filesystem.
     *
     * @param fileName the file name of the file to open
     * @param options the file-open options
     * @return the file channel
     * @throws IOException if an I/O error occurs
     */
    public FileChannel openFile(String fileName, OptionMap options) throws IOException {
        return openFile(new File(fileName), options);
    }

    /**
     * Open a file on the filesystem.
     *
     * @param file the file to open
     * @param access the file access level to use
     * @return the file channel
     * @throws IOException if an I/O error occurs
     */
    public FileChannel openFile(File file, FileAccess access) throws IOException {
        if (access == null) {
            throw new IllegalArgumentException("access is null");
        }
        return openFile(file, FILE_ACCESS_OPTION_MAPS.get(access));
    }

    /**
     * Open a file on the filesystem.
     *
     * @param fileName the file name of the file to open
     * @param access the file access level to use
     * @return the file channel
     * @throws IOException if an I/O error occurs
     */
    public FileChannel openFile(String fileName, FileAccess access) throws IOException {
        if (access == null) {
            throw new IllegalArgumentException("access is null");
        }
        return openFile(new File(fileName), FILE_ACCESS_OPTION_MAPS.get(access));
    }

    //==================================================
    //
    // Worker methods
    //
    //==================================================

    /**
     * Construct a new XNIO worker.
     *
     * @param optionMap the options to use to configure the worker
     * @return the new worker
     * @throws IOException if the worker failed to be opened
     * @throws IllegalArgumentException if an option value is invalid for this worker
     */
    public XnioWorker createWorker(OptionMap optionMap) throws IOException, IllegalArgumentException {
        return createWorker(null, optionMap);
    }

    /**
     * Construct a new XNIO worker.
     *
     * @param threadGroup the thread group for worker threads
     * @param optionMap the options to use to configure the worker
     * @return the new worker
     * @throws IOException if the worker failed to be opened
     * @throws IllegalArgumentException if an option value is invalid for this worker
     */
    public abstract XnioWorker createWorker(ThreadGroup threadGroup, OptionMap optionMap) throws IOException, IllegalArgumentException;

    //==================================================
    //
    // General methods
    //
    //==================================================

    /**
     * Get the name of this XNIO provider.
     *
     * @return the name
     */
    public final String getName() {
        return name;
    }

    /**
     * Get a string representation of this XNIO provider.
     *
     * @return the string representation
     */
    public final String toString() {
        return String.format("XNIO provider \"%s\" <%s@%s>", getName(), getClass().getName(), Integer.toHexString(hashCode()));
    }

    /**
     * Get an XNIO property.  The property name must start with {@code "xnio."}.
     *
     * @param name the property name
     * @return the property value, or {@code null} if it wasn't found
     * @since 1.2
     */
    protected String getProperty(final String name) {
        if (! name.startsWith("xnio.")) {
            throw new SecurityException("Not allowed to read non-XNIO properties");
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            return AccessController.doPrivileged(new GetPropertyAction(name, null));
        } else {
            return System.getProperty(name);
        }
    }

    /**
     * Get an XNIO property.  The property name must start with {@code "xnio."}.
     *
     * @param name the property name
     * @param defaultValue the default value
     * @return the property value, or {@code defaultValue} if it wasn't found
     * @since 1.2
     */
    protected String getProperty(final String name, final String defaultValue) {
        if (! name.startsWith("xnio.")) {
            throw new SecurityException("Not allowed to read non-XNIO properties");
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            return AccessController.doPrivileged(new GetPropertyAction(name, defaultValue));
        } else {
            return System.getProperty(name, defaultValue);
        }
    }

    private static final class GetPropertyAction implements PrivilegedAction<String> {
        private final String propertyName;
        private final String defaultValue;

        private GetPropertyAction(final String propertyName, final String defaultValue) {
            this.propertyName = propertyName;
            this.defaultValue = defaultValue;
        }

        public String run() {
            return System.getProperty(propertyName, defaultValue);
        }
    }
}
