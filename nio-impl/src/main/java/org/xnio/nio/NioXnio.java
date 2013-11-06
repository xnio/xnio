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

package org.xnio.nio;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.jboss.logging.Logger;
import org.xnio.IoUtils;
import org.xnio.Version;
import org.xnio.Xnio;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;

/**
 * An NIO-based XNIO provider for a standalone application.
 */
final class NioXnio extends Xnio {

    private static final Logger log = Logger.getLogger("org.xnio.nio");

    interface SelectorCreator {
        Selector open() throws IOException;
    }

    final SelectorCreator tempSelectorCreator;
    final SelectorCreator mainSelectorCreator;

    static final boolean NIO2;

    static {
        log.info("XNIO NIO Implementation Version " + Version.VERSION);
        boolean nio2 = false;
        try {
            // try to find an NIO.2 interface on the system class path
            Class.forName("java.nio.channels.MulticastChannel", false, null);
            log.trace("NIO.2 detected");
            nio2 = true;
        } catch (Throwable t) {
        }
        NIO2 = nio2;
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                final String bugLevel = System.getProperty("sun.nio.ch.bugLevel");
                if (bugLevel == null) System.setProperty("sun.nio.ch.bugLevel", "");
                return null;
            }
        });
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
                        throw new IllegalStateException("No functional selector provider is available");
                    }
                    log.tracef("Starting up with selector provider %s", provider);
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
                            final ConstructorSelectorCreator creator = new ConstructorSelectorCreator("sun.nio.ch.PollSelectorImpl", provider);
                            IoUtils.safeClose(creator.open());
                            objects[1] = creator;
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
        log.tracef("Using %s for main selectors and %s for temp selectors", mainSelectorCreator, tempSelectorCreator);
    }

    public XnioWorker createWorker(final ThreadGroup threadGroup, final OptionMap optionMap, final Runnable terminationTask) throws IOException, IllegalArgumentException {
        final NioXnioWorker worker = new NioXnioWorker(this, threadGroup, optionMap, terminationTask);
        worker.start();
        return worker;
    }

    private final ThreadLocal<Selector> selectorThreadLocal = new ThreadLocal<Selector>() {
        public void remove() {
            // if no selector was created, none will be closed
            IoUtils.safeClose(get());
            super.remove();
        }
    };

    Selector getSelector() throws IOException {
        final ThreadLocal<Selector> threadLocal = selectorThreadLocal;
        Selector selector = threadLocal.get();
        if (selector == null) {
            selector = tempSelectorCreator.open();
            threadLocal.set(selector);
        }
        return selector;
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
                } catch (IOException e2) {
                    throw e2;
                } catch (Error e2) {
                    throw e2;
                } catch (RuntimeException e2) {
                    throw e2;
                } catch (Throwable t) {
                    throw new IllegalStateException("Unexpected exception opening a selector", t);
                }
            }
        }

        public String toString() {
            return String.format("Selector creator %s for provider %s", constructor.getDeclaringClass(), provider.getClass());
        }
    }
}
