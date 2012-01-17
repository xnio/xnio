/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.xnio.mock;

import java.io.IOException;

import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioProvider;
import org.xnio.XnioWorker;

/**
 * {@link Xnio} mock.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class XnioMock extends Xnio {

    private static final XnioMock INSTANCE = new XnioMock("xnio-mock");

    protected XnioMock(String name) {
        super(name);
    }

    @Override
    public XnioWorker createWorker(ThreadGroup threadGroup, OptionMap optionMap, Runnable terminationTask) throws IOException,
            IllegalArgumentException {
        return new XnioWorkerMock(this, threadGroup, optionMap, terminationTask);
    }

    public static class Provider implements XnioProvider {
        @Override
        public Xnio getInstance() {
            return INSTANCE;
        }

        @Override
        public String getName() {
            return INSTANCE.getName();
        }
    }

}
