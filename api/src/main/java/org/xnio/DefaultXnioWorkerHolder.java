/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
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

import java.io.IOError;
import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.wildfly.client.config.ConfigXMLParseException;
import org.xnio._private.Messages;

final class DefaultXnioWorkerHolder {
    static final XnioWorker INSTANCE;

    static {
        INSTANCE = doPrivileged((PrivilegedAction<XnioWorker>) () -> {
            final Xnio xnio = Xnio.getInstance();
            XnioWorker worker = null;
            try {
                worker = XnioXmlParser.parseWorker(xnio);
            } catch (ConfigXMLParseException | IOException e) {
                Messages.msg.trace("Failed to parse worker XML definition", e);
            }
            if (worker == null) {
                final Iterator<XnioWorkerConfigurator> iterator = ServiceLoader.load(XnioWorkerConfigurator.class, DefaultXnioWorkerHolder.class.getClassLoader()).iterator();
                while (worker == null) try {
                    if (! iterator.hasNext()) break;
                    final XnioWorkerConfigurator configurator = iterator.next();
                    if (configurator != null) try {
                        worker = configurator.createWorker();
                    } catch (IOException e) {
                        Messages.msg.trace("Failed to configure the default worker", e);
                    }
                } catch (ServiceConfigurationError e) {
                    Messages.msg.trace("Failed to configure a service", e);
                }
            }
            if (worker == null) try {
                // create with defaults
                worker = xnio.createWorker(OptionMap.create(Options.THREAD_DAEMON, Boolean.TRUE));
            } catch (IOException e) {
                throw new IOError(e);
            }
            return worker;
        });
    }

}
