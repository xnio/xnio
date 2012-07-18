/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc.


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
