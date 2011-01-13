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

import java.lang.reflect.UndeclaredThrowableException;

final class SynchronousHolder<T, X extends Exception> {
    private final Class<X> throwType;
    private T held;
    private Throwable problem;
    private boolean set = false;

    SynchronousHolder(final Class<X> type) {
        throwType = type;
    }

    public T get() throws X {
        boolean intr = false;
        try {
            synchronized (this) {
                for (;;) {
                    if (problem != null) {
                        try {
                            throw problem;
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            throw throwType.cast(e);
                        } catch (Error e) {
                            throw e;
                        } catch (Throwable e) {
                            throw new UndeclaredThrowableException(e);
                        }
                    } else if (set) {
                        return held;
                    }
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        intr = true;
                    }
                }
            }
        } finally {
            if (intr) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void set(T value) {
        synchronized (this) {
            held = value;
            set = true;
            notify();
        }
    }

    public void setProblem(X problem) {
        synchronized (this) {
            this.problem = throwType.cast(problem);
            notify();
        }
    }

    public void setProblem(RuntimeException problem) {
        synchronized (this) {
            this.problem = problem;
            notify();
        }
    }
}