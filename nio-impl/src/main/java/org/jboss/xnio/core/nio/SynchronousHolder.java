package org.jboss.xnio.core.nio;

/**
 *
 */
public final class SynchronousHolder<T, E extends Throwable> {
    private T held;
    private E problem;
    private boolean set = false;

    public T get() throws E {
        boolean intr = false;
        try {
            synchronized (this) {
                for (;;) {
                    if (problem != null) {
                        throw problem;
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

    public void setProblem(E problem) {
        synchronized (this) {
            this.problem = problem;
            notify();
        }
    }
}