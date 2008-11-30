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

package org.jboss.xnio.nio.test;

import org.jboss.xnio.IoHandler;
import org.jboss.xnio.test.support.TestThreadFactory;
import java.nio.channels.Channel;

/**
 *
 */
public final class CatchingHandler<T extends Channel> implements IoHandler<T> {

    private final IoHandler<? super T> delegate;
    private final TestThreadFactory testThreadFactory;

    public CatchingHandler(final IoHandler<? super T> delegate, final TestThreadFactory factory) {
        this.delegate = delegate;
        testThreadFactory = factory;
    }

    public void handleOpened(final T channel) {
        try {
            delegate.handleOpened(channel);
        } catch (RuntimeException t) {
            testThreadFactory.addProblem(t);
            throw t;
        } catch (Error t) {
            testThreadFactory.addProblem(t);
            throw t;
        }
    }

    public void handleClosed(final T channel) {
        try {
            delegate.handleClosed(channel);
        } catch (RuntimeException t) {
            testThreadFactory.addProblem(t);
            throw t;
        } catch (Error t) {
            testThreadFactory.addProblem(t);
            throw t;
        }
    }

    public void handleReadable(final T channel) {
        try {
            delegate.handleReadable(channel);
        } catch (RuntimeException t) {
            testThreadFactory.addProblem(t);
            throw t;
        } catch (Error t) {
            testThreadFactory.addProblem(t);
            throw t;
        }
    }

    public void handleWritable(final T channel) {
        try {
            delegate.handleWritable(channel);
        } catch (RuntimeException t) {
            testThreadFactory.addProblem(t);
            throw t;
        } catch (Error t) {
            testThreadFactory.addProblem(t);
            throw t;
        }
    }
}
