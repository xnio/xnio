/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
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
