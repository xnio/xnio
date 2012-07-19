/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
