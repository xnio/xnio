/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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

import static java.security.AccessController.doPrivileged;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.wildfly.common.ref.CleanerReference;

/**
 * An automatic reference is a phantom reference which is automatically freed by a background thread when it is
 * enqueued.  Since this type of garbage collection imposes considerable overhead, it should only be used sparingly,
 * when it is impossible to achieve correctness any other way.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @deprecated See {@link CleanerReference}.
 */
@Deprecated
public abstract class AutomaticReference<T> extends PhantomReference<T> {

    static final Object PERMIT = new Object();

    // todo: we can do better than this...
    private static final Set<AutomaticReference<?>> LIVE_SET = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<AutomaticReference<?>, Boolean>()));

    /**
     * Get the security authorization permit to create automatic references.
     *
     * @return the permit
     * @throws SecurityException if a security manager is enabled and the caller does not have the {@code createAutomaticReference} {@link RuntimePermission}
     */
    public static Object getPermit() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("createAutomaticReference"));
        }
        return PERMIT;
    }

    private static ReferenceQueue<Object> checkPermit(Object permit) {
        if (permit == PERMIT) {
            // only initialize the cleaner on the first valid permit
            return Cleaner.QUEUE;
        } else {
            throw new SecurityException("Unauthorized subclass of " + AutomaticReference.class);
        }
    }

    /**
     * Always returns {@code null}.
     *
     * @return {@code null}
     */
    public final T get() {
        return null;
    }

    /**
     * Not supported.
     *
     * @throws UnsupportedOperationException always
     */
    public final void clear() {
        throw new UnsupportedOperationException();
    }

    /**
     * Determine whether this reference has been enqueued by the garbage collector.
     *
     * @return {@code true} if the reference has been enqueued, {@code false} otherwise
     */
    public final boolean isEnqueued() {
        return super.isEnqueued();
    }

    /**
     * Not supported.
     *
     * @return nothing
     * @throws UnsupportedOperationException always
     */
    public final boolean enqueue() {
        throw new UnsupportedOperationException();
    }

    /**
     * Construct a new instance.  In order to maximize performance, the only security check performed by
     * this constructor is to verify the {@code permit} which was passed in.
     *
     * @param referent the object to monitor
     * @param permit the permit object originally acquired from {@link #getPermit()}
     */
    protected AutomaticReference(final T referent, final Object permit) {
        super(referent, checkPermit(permit));
        LIVE_SET.add(this);
    }

    /**
     * Free this reference.  This method will be called from a dedicated thread or threads so this method
     * should execute as quickly as possible.
     */
    protected abstract void free();

    static class Cleaner implements Runnable {
        private static final ReferenceQueue<Object> QUEUE;

        static {
            QUEUE = new ReferenceQueue<>();
            doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    final Thread thr = new Thread(new Cleaner(), "XNIO cleaner thread");
                    thr.setDaemon(true);
                    thr.setContextClassLoader(null);
                    thr.start();
                    return null;
                }
            });
        }

        public void run() {
            AutomaticReference<?> ref;
            for (;;) try {
                ref = (AutomaticReference<?>) QUEUE.remove();
                try {
                    ref.free();
                } finally {
                    LIVE_SET.remove(ref);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
