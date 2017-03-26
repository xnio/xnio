/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xnio;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.xnio.management.XnioProviderMXBean;
import org.xnio.management.XnioServerMXBean;
import org.xnio.management.XnioWorkerMXBean;
import org.xnio.ssl.JsseSslUtils;
import org.xnio.ssl.JsseXnioSsl;
import org.xnio.ssl.XnioSsl;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import static java.security.AccessController.doPrivileged;
import static org.xnio._private.Messages.msg;

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

    static final class MBeanHolder {

        private static final MBeanServer MBEAN_SERVER;

        static {
            MBEAN_SERVER = doPrivileged(new PrivilegedAction<MBeanServer>() {
                public MBeanServer run() {
                    return ManagementFactory.getPlatformMBeanServer();
                }
            });
        }
    }

    /**
     * A flag indicating the presence of NIO.2 (JDK 7).  Always {@code true} as of XNIO version 3.5.0, which requires
     * Java 8.
     */
    public static final boolean NIO2 = true;

    static {
        msg.greeting(Version.VERSION);
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
            throw msg.nullParameter("name");
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
            throw msg.blockingNotAllowed();
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
    public static Xnio getInstance(final ClassLoader classLoader) {
        return doGetInstance(null, doPrivileged(new PrivilegedAction<ServiceLoader<XnioProvider>>() {
            public ServiceLoader<XnioProvider> run() {
                return ServiceLoader.load(XnioProvider.class, classLoader);
            }
        }));
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
        return doGetInstance(null, doPrivileged(new PrivilegedAction<ServiceLoader<XnioProvider>>() {
            public ServiceLoader<XnioProvider> run() {
                return ServiceLoader.load(XnioProvider.class, Xnio.class.getClassLoader());
            }
        }));
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
    public static Xnio getInstance(String provider, final ClassLoader classLoader) {
        return doGetInstance(provider, doPrivileged(new PrivilegedAction<ServiceLoader<XnioProvider>>() {
            public ServiceLoader<XnioProvider> run() {
                return ServiceLoader.load(XnioProvider.class, classLoader);
            }
        }));
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
        return doGetInstance(provider, doPrivileged(new PrivilegedAction<ServiceLoader<XnioProvider>>() {
            public ServiceLoader<XnioProvider> run() {
                return ServiceLoader.load(XnioProvider.class, Xnio.class.getClassLoader());
            }
        }));
    }

    private static synchronized Xnio doGetInstance(final String provider, final ServiceLoader<XnioProvider> serviceLoader) {
        final Iterator<XnioProvider> iterator = serviceLoader.iterator();
        for (;;) {
            try {
                if (! iterator.hasNext()) break;
                final XnioProvider xnioProvider = iterator.next();
                try {
                    if (provider == null || provider.equals(xnioProvider.getName())) {
                        return xnioProvider.getInstance();
                    }
                } catch (Throwable t) {
                    msg.debugf(t, "Not loading provider %s", xnioProvider.getName());
                }
            } catch (Throwable t) {
                msg.debugf(t, "Skipping non-loadable provider");
            }
        }
        try {
            Xnio xnio = OsgiSupport.doGetOsgiService();
            if (xnio != null) {
                return xnio;
            }
        } catch (NoClassDefFoundError t) {
            // Ignore
        } catch (Throwable t) {
            msg.debugf(t, "Not using OSGi service");
        }
        throw msg.noProviderFound();
    }

    static class OsgiSupport {

        static Xnio doGetOsgiService() {
            Bundle bundle = FrameworkUtil.getBundle(Xnio.class);
            BundleContext context = bundle.getBundleContext();
            if (context == null) {
                throw new IllegalStateException("Bundle not started");
            }
            ServiceReference<Xnio> sr = context.getServiceReference(Xnio.class);
            if (sr == null) {
                return null;
            }
            return context.getService(sr);
        }

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

    /**
     * Get an SSL provider for this XNIO provider.
     *
     * @param optionMap the option map to use for configuring SSL
     * @param keyManagers the key managers to use, or {@code null} to configure from the option map
     * @param trustManagers the trust managers to use, or {@code null} to configure from the option map
     * @return the SSL provider
     * @throws GeneralSecurityException if an exception occurred configuring the SSL provider
     */
    public XnioSsl getSslProvider(final KeyManager[] keyManagers, final TrustManager[] trustManagers, final OptionMap optionMap) throws GeneralSecurityException {
        return new JsseXnioSsl(this, optionMap, JsseSslUtils.createSSLContext(keyManagers, trustManagers, null, optionMap));
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
        if (file == null) {
            throw msg.nullParameter("file");
        }
        if (options == null) {
            throw msg.nullParameter("options");
        }
        try {
            final FileAccess fileAccess = options.get(Options.FILE_ACCESS, FileAccess.READ_WRITE);
            final boolean append = options.get(Options.FILE_APPEND, false);
            final boolean create = options.get(Options.FILE_CREATE, fileAccess != FileAccess.READ_ONLY);
            final EnumSet<StandardOpenOption> openOptions = EnumSet.noneOf(StandardOpenOption.class);
            if (create) {
                openOptions.add(StandardOpenOption.CREATE);
            }
            if (fileAccess.isRead()) {
                openOptions.add(StandardOpenOption.READ);
            }
            if (fileAccess.isWrite()) {
                openOptions.add(StandardOpenOption.WRITE);
            }
            if (append) {
                openOptions.add(StandardOpenOption.APPEND);
            }
            final Path path = file.toPath();
            return new XnioFileChannel(path.getFileSystem().provider().newFileChannel(path, openOptions));
        } catch (NoSuchFileException e) {
            throw new FileNotFoundException(e.getMessage());
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
        if (fileName == null) {
            throw msg.nullParameter("fileName");
        }
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
            throw msg.nullParameter("access");
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
            throw msg.nullParameter("access");
        }
        if (fileName == null) {
            throw msg.nullParameter("fileName");
        }
        return openFile(new File(fileName), FILE_ACCESS_OPTION_MAPS.get(access));
    }

    /**
     * Unwrap an XNIO-wrapped file channel.  For use by providers.
     *
     * @param src the possibly wrapped file channel
     * @return the unwrapped file channel
     */
    protected FileChannel unwrapFileChannel(FileChannel src) {
        if (src instanceof XnioFileChannel) {
            return ((XnioFileChannel)src).getDelegate();
        } else {
            return src;
        }
    }

    //==================================================
    //
    // Worker methods
    //
    //==================================================

    /**
     * Create a new worker builder.
     *
     * @return the worker builder (not {@code null})
     */
    public XnioWorker.Builder createWorkerBuilder() {
        return new XnioWorker.Builder(this);
    }

    /**
     * Construct an XNIO worker from a builder.
     *
     * @param builder the builder (must not be {@code null})
     * @return the constructed worker
     */
    protected abstract XnioWorker build(XnioWorker.Builder builder);

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
    public XnioWorker createWorker(ThreadGroup threadGroup, OptionMap optionMap) throws IOException, IllegalArgumentException {
        return createWorker(threadGroup, optionMap, null);
    }

    /**
     * Construct a new XNIO worker.
     *
     * @param threadGroup the thread group for worker threads
     * @param optionMap the options to use to configure the worker
     * @param terminationTask the task to run after the worker has shut down
     * @return the new worker
     * @throws IOException if the worker failed to be opened
     * @throws IllegalArgumentException if an option value is invalid for this worker
     */
    public XnioWorker createWorker(ThreadGroup threadGroup, OptionMap optionMap, Runnable terminationTask) throws IOException, IllegalArgumentException {
        final XnioWorker.Builder workerBuilder = createWorkerBuilder();
        workerBuilder.populateFromOptions(optionMap);
        workerBuilder.setThreadGroup(threadGroup);
        workerBuilder.setTerminationTask(terminationTask);
        return workerBuilder.build();
    }

    /**
     * Creates a file system watcher, that can be used to monitor file system changes.
     *
     * @param name The watcher name
     * @param options The options to use to create the watcher
     * @return The file system watcher
     */
    public FileSystemWatcher createFileSystemWatcher(final String name, final OptionMap options) {
        int pollInterval = options.get(Options.WATCHER_POLL_INTERVAL, 5000);
        boolean daemonThread = options.get(Options.THREAD_DAEMON, true);
        return new PollingFileSystemWatcher(name, pollInterval, daemonThread);
    }

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
    protected static String getProperty(final String name) {
        if (! name.startsWith("xnio.")) {
            throw msg.propReadForbidden();
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            return AccessController.doPrivileged(new ReadPropertyAction(name, null));
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
    protected static String getProperty(final String name, final String defaultValue) {
        if (! name.startsWith("xnio.")) {
            throw msg.propReadForbidden();
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            return AccessController.doPrivileged(new ReadPropertyAction(name, defaultValue));
        } else {
            return System.getProperty(name, defaultValue);
        }
    }

    /**
     * Register an MBean.  If the MBean cannot be registered, this method will simply return.
     *
     * @param providerMXBean the provider MBean to register
     * @return a handle which may be used to remove the registration
     */
    protected static Closeable register(XnioProviderMXBean providerMXBean) {
        try {
            final ObjectName objectName = new ObjectName("org.xnio", ObjectProperties.properties(ObjectProperties.property("type", "Xnio"), ObjectProperties.property("provider", ObjectName.quote(providerMXBean.getName()))));
            MBeanHolder.MBEAN_SERVER.registerMBean(providerMXBean, objectName);
            return new MBeanCloseable(objectName);
        } catch (Throwable ignored) {
            return IoUtils.nullCloseable();
        }
    }

    /**
     * Register an MBean.  If the MBean cannot be registered, this method will simply return.
     *
     * @param workerMXBean the worker MBean to register
     * @return a handle which may be used to remove the registration
     */
    protected static Closeable register(XnioWorkerMXBean workerMXBean) {
        try {
            final ObjectName objectName = new ObjectName("org.xnio", ObjectProperties.properties(ObjectProperties.property("type", "Xnio"), ObjectProperties.property("provider", ObjectName.quote(workerMXBean.getProviderName())), ObjectProperties.property("worker", ObjectName.quote(workerMXBean.getName()))));
            MBeanHolder.MBEAN_SERVER.registerMBean(workerMXBean, objectName);
            return new MBeanCloseable(objectName);
        } catch (Throwable ignored) {
            return IoUtils.nullCloseable();
        }
    }

    /**
     * Register an MBean.  If the MBean cannot be registered, this method will simply return.
     *
     * @param serverMXBean the server MBean to register
     * @return a handle which may be used to remove the registration
     */
    protected static Closeable register(XnioServerMXBean serverMXBean) {
        try {
            final ObjectName objectName = new ObjectName("org.xnio", ObjectProperties.properties(ObjectProperties.property("type", "Xnio"), ObjectProperties.property("provider", ObjectName.quote(serverMXBean.getProviderName())), ObjectProperties.property("worker", ObjectName.quote(serverMXBean.getWorkerName())), ObjectProperties.property("address", ObjectName.quote(serverMXBean.getBindAddress()))));
            MBeanHolder.MBEAN_SERVER.registerMBean(serverMXBean, objectName);
            return new MBeanCloseable(objectName);
        } catch (Throwable ignored) {
            return IoUtils.nullCloseable();
        }
    }

    static class MBeanCloseable extends AtomicBoolean implements Closeable {

        private final ObjectName objectName;

        MBeanCloseable(final ObjectName objectName) {
            this.objectName = objectName;
        }

        public void close() {
            if (! getAndSet(true)) try {
                MBeanHolder.MBEAN_SERVER.unregisterMBean(objectName);
            } catch (Throwable ignored) {
            }
        }
    }
}
