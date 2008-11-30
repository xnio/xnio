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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.ArrayList;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class TestThreadFactory implements ThreadFactory {

    private static final Logger log = Logger.getLogger("TEST");

    private final ThreadFactory delegate = Executors.defaultThreadFactory();

    private final Object lock = new Object();
    private int cnt = 0;
    private final List<Throwable> problems = new ArrayList<Throwable>();

    public Thread newThread(final Runnable r) {
        return delegate.newThread(new Runnable() {
            public void run() {
                synchronized (lock) {
                    cnt ++;
                }
                try {
                    r.run();
                } catch (Throwable t) {
                    synchronized (lock) {
                        problems.add(t);
                    }
                } finally {
                    synchronized (lock) {
                        if (--cnt == 0) {
                            lock.notifyAll();
                        }
                    }
                }
            }
        });
    }

    public void await() throws InterruptedException {
        synchronized (lock) {
            log.info("Awaiting completion...");
            while (cnt > 0) {
                lock.wait();
            }
            if (problems.size() > 0) try {
                final StringBuilder builder = new StringBuilder();
                builder.append("Failed with the following problems:\n");
                for (Throwable problem : problems) {
                    final StringWriter sw = new StringWriter();
                    final PrintWriter pw = new PrintWriter(sw, true);
                    problem.printStackTrace(pw);
                    builder.append(sw.getBuffer());
                }
                throw new RuntimeException(builder.toString());
            } finally {
                problems.clear();
            }
        }
    }

    public void clear() {
        synchronized (lock) {
            problems.clear();
        }
    }

    public void addProblem(final Throwable problem) {
        synchronized (lock) {
            problems.add(problem);
        }
    }
}
