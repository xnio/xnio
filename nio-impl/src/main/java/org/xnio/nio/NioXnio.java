/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2014 Red Hat, Inc. and/or its affiliates.
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


package org.xnio.nio;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.xnio.FileSystemWatcher;
import org.xnio.IoUtils;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;
import org.xnio.management.XnioProviderMXBean;
import org.xnio.management.XnioServerMXBean;
import org.xnio.management.XnioWorkerMXBean;

import static org.xnio.nio.Log.log;

/**
 * An NIO-based XNIO provider for a standalone application.
 */
final class NioXnio extends Xnio {

    static final boolean IS_HP_UX;
    static final boolean HAS_BUGGY_EVENT_PORT;

    interface SelectorCreator {
        Selector open() throws IOException;
    }

    final SelectorCreator tempSelectorCreator;
    final SelectorCreator mainSelectorCreator;

    static {
        log.greeting(Version.getVersionString());
        IS_HP_UX = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
            public Boolean run() {
                final String bugLevel = System.getProperty("sun.nio.ch.bugLevel");
                if (bugLevel == null) System.setProperty("sun.nio.ch.bugLevel", "");
                return Boolean.valueOf(System.getProperty("os.name", "unknown").equalsIgnoreCase("hp-ux"));
            }
        }).booleanValue();
        // if a JDK is released with a fix, we can try to detect it and set this to "false" for those JDKs.
        HAS_BUGGY_EVENT_PORT = true;
    }

    /**
     * Construct a new NIO-based XNIO provider instance.  Should only be invoked by the service loader.
     */
    NioXnio() {
        super("nio");
        final Object[] objects = AccessController.doPrivileged(
            new PrivilegedAction<Object[]>() {
                public Object[] run() {
                    final SelectorProvider defaultProvider = SelectorProvider.provider();
                    final String chosenProvider = System.getProperty("xnio.nio.selector.provider");
                    SelectorProvider provider = null;
                    if (chosenProvider != null) {
                        try {
                            provider = Class.forName(chosenProvider, true, NioXnio.class.getClassLoader()).asSubclass(SelectorProvider.class).getConstructor().newInstance();
                            provider.openSelector().close();
                        } catch (Throwable e) {
                            // not available
                            provider = null;
                        }
                    }
                    if (provider == null) {
                        try {
                            // Mac OS X and BSD
                            provider = Class.forName("sun.nio.ch.KQueueSelectorProvider", true, NioXnio.class.getClassLoader()).asSubclass(SelectorProvider.class).getConstructor().newInstance();
                            provider.openSelector().close();
                        } catch (Throwable e) {
                            // not available
                            provider = null;
                        }
                    }
                    if (provider == null) {
                        try {
                            // Linux
                            provider = Class.forName("sun.nio.ch.EPollSelectorProvider", true, NioXnio.class.getClassLoader()).asSubclass(SelectorProvider.class).getConstructor().newInstance();
                            provider.openSelector().close();
                        } catch (Throwable e) {
                            // not available
                            provider = null;
                        }
                    }
                    if (provider == null && ! HAS_BUGGY_EVENT_PORT) {
                        try {
                            // Solaris (Java 8+)
                            provider = Class.forName("sun.nio.ch.EventPortSelectorProvider", true, NioXnio.class.getClassLoader()).asSubclass(SelectorProvider.class).getConstructor().newInstance();
                            provider.openSelector().close();
                        } catch (Throwable e) {
                            // not available
                            provider = null;
                        }
                    }
                    if (provider == null) {
                        try {
                            // Solaris
                            provider = Class.forName("sun.nio.ch.DevPollSelectorProvider", true, NioXnio.class.getClassLoader()).asSubclass(SelectorProvider.class).getConstructor().newInstance();
                            provider.openSelector().close();
                        } catch (Throwable e) {
                            // not available
                            provider = null;
                        }
                    }
                    if (provider == null) {
                        try {
                            // Solaris (Java 8+)
                            provider = Class.forName("sun.nio.ch.EventPortSelectorProvider", true, NioXnio.class.getClassLoader()).asSubclass(SelectorProvider.class).getConstructor().newInstance();
                            provider.openSelector().close();
                        } catch (Throwable e) {
                            // not available
                            provider = null;
                        }
                    }
                    if (provider == null) {
                        try {
                            // AIX
                            provider = Class.forName("sun.nio.ch.PollsetSelectorProvider", true, NioXnio.class.getClassLoader()).asSubclass(SelectorProvider.class).getConstructor().newInstance();
                            provider.openSelector().close();
                        } catch (Throwable e) {
                            // not available
                            provider = null;
                        }
                    }
                    if (provider == null) {
                        try {
                            defaultProvider.openSelector().close();
                            provider = defaultProvider;
                        } catch (Throwable e) {
                            // not available
                        }
                    }
                    if (provider == null) {
                        try {
                            // Nothing else works, not even the default
                            provider = Class.forName("sun.nio.ch.PollSelectorProvider", true, NioXnio.class.getClassLoader()).asSubclass(SelectorProvider.class).getConstructor().newInstance();
                            provider.openSelector().close();
                        } catch (Throwable e) {
                            // not available
                            provider = null;
                        }
                    }
                    if (provider == null) {
                        throw log.noSelectorProvider();
                    }
                    log.selectorProvider(provider);
                    final boolean defaultIsPoll = "sun.nio.ch.PollSelectorProvider".equals(provider.getClass().getName());
                    final String chosenMainSelector = System.getProperty("xnio.nio.selector.main");
                    final String chosenTempSelector = System.getProperty("xnio.nio.selector.temp");
                    final SelectorCreator defaultSelectorCreator = new DefaultSelectorCreator(provider);
                    final Object[] objects = new Object[3];
                    objects[0] = provider;
                    if (chosenTempSelector != null) try {
                        final ConstructorSelectorCreator creator = new ConstructorSelectorCreator(chosenTempSelector, provider);
                        IoUtils.safeClose(creator.open());
                        objects[1] = creator;
                    } catch (Exception e) {
                        // not available
                    }
                    if (chosenMainSelector != null) try {
                        final ConstructorSelectorCreator creator = new ConstructorSelectorCreator(chosenMainSelector, provider);
                        IoUtils.safeClose(creator.open());
                        objects[2] = creator;
                    } catch (Exception e) {
                        // not available
                    }
                    if (! defaultIsPoll) {
                        // default is fine for main selectors; we should try to get poll for temp though
                        if (objects[1] == null) try {
                            SelectorProvider pollSelectorProvider = Class.forName("sun.nio.ch.PollSelectorProvider", true, NioXnio.class.getClassLoader()).asSubclass(SelectorProvider.class).getConstructor().newInstance();
                            pollSelectorProvider.openSelector().close();
                            objects[1] = new DefaultSelectorCreator(provider);
                        } catch (Exception e) {
                            // not available
                        }
                    }
                    if (objects[1] == null) {
                        objects[1] = defaultSelectorCreator;
                    }
                    if (objects[2] == null) {
                        objects[2] = defaultSelectorCreator;
                    }
                    return objects;
                }
            }
        );
        tempSelectorCreator = (SelectorCreator) objects[1];
        mainSelectorCreator = (SelectorCreator) objects[2];
        log.selectors(mainSelectorCreator, tempSelectorCreator);
        register(new XnioProviderMXBean() {
            public String getName() {
                return "nio";
            }

            public String getVersion() {
                return Version.getVersionString();
            }
        });
    }

    protected XnioWorker build(final XnioWorker.Builder builder) {
        final NioXnioWorker worker = new NioXnioWorker(builder);
        worker.start();
        return worker;
    }

    @Override
    public FileSystemWatcher createFileSystemWatcher(String name, OptionMap options) {
        try {
            boolean daemonThread = options.get(Options.THREAD_DAEMON, true);
            return new WatchServiceFileSystemWatcher(name, daemonThread);
        } catch (LinkageError e) {
            //ignore
        }
        return super.createFileSystemWatcher(name, options);
    }

    private final ThreadLocal<FinalizableSelectorHolder> selectorThreadLocal = new ThreadLocal<FinalizableSelectorHolder>() {
        public void remove() {
            // if no selector was created, none will be closed
            FinalizableSelectorHolder holder = get();
            if(holder != null) {
                IoUtils.safeClose(holder.selector);
            }
            super.remove();
        }
    };

    Selector getSelector() throws IOException {
        final ThreadLocal<FinalizableSelectorHolder> threadLocal = selectorThreadLocal;
        FinalizableSelectorHolder holder = threadLocal.get();
        if (holder == null) {
            holder = new FinalizableSelectorHolder(tempSelectorCreator.open());
            threadLocal.set(holder);
        }
        return holder.selector;
    }

    private static class DefaultSelectorCreator implements SelectorCreator {
        private final SelectorProvider provider;

        private DefaultSelectorCreator(final SelectorProvider provider) {
            this.provider = provider;
        }

        public Selector open() throws IOException {
            return provider.openSelector();
        }

        public String toString() {
            return "Default system selector creator for provider " + provider.getClass();
        }
    }

    private static class ConstructorSelectorCreator implements SelectorCreator {

        private final Constructor<? extends Selector> constructor;
        private final SelectorProvider provider;

        public ConstructorSelectorCreator(final String name, final SelectorProvider provider) throws ClassNotFoundException, NoSuchMethodException {
            this.provider = provider;
            final Class<? extends Selector> selectorImplClass = Class.forName(name, true, null).asSubclass(Selector.class);
            final Constructor<? extends Selector> constructor = selectorImplClass.getDeclaredConstructor(SelectorProvider.class);
            constructor.setAccessible(true);
            this.constructor = constructor;
        }

        public Selector open() throws IOException {
            try {
                return constructor.newInstance(provider);
            } catch (InstantiationException e) {
                return Selector.open();
            } catch (IllegalAccessException e) {
                return Selector.open();
            } catch (InvocationTargetException e) {
                try {
                    throw e.getTargetException();
                } catch (IOException | Error | RuntimeException e2) {
                    throw e2;
                } catch (Throwable t) {
                    throw log.unexpectedSelectorOpenProblem(t);
                }
            }
        }

        public String toString() {
            return String.format("Selector creator %s for provider %s", constructor.getDeclaringClass(), provider.getClass());
        }
    }

    protected static Closeable register(XnioWorkerMXBean workerMXBean) {
        return Xnio.register(workerMXBean);
    }

    protected static Closeable register(XnioServerMXBean serverMXBean) {
        return Xnio.register(serverMXBean);
    }

    private static final class FinalizableSelectorHolder {
        final Selector selector;


        private FinalizableSelectorHolder(Selector selector) {
            this.selector = selector;
        }

        @Override
        protected void finalize() throws Throwable {
            IoUtils.safeClose(selector);
        }
    }
}
