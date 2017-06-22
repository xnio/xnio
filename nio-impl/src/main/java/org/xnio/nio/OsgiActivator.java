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

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.xnio.Xnio;
import org.xnio.XnioProvider;

import java.util.Hashtable;

/**
 * OSGi Activator.
 *
 * @author <a href="mailto:gnodet@redhat.com">Guillaume Nodet</a>
 */
public class OsgiActivator implements BundleActivator {

    private ServiceRegistration<Xnio> registrationS;
    private ServiceRegistration<XnioProvider> registrationP;

    @Override
    public void start(BundleContext context) throws Exception {
        XnioProvider provider = new NioXnioProvider();
        Xnio xnio = provider.getInstance();
        String name = xnio.getName();
        Hashtable<String, String> props = new Hashtable<>();
        props.put("name", name);
        registrationS = context.registerService(Xnio.class, xnio, props);
        registrationP = context.registerService(XnioProvider.class, provider, props);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        registrationS.unregister();
        registrationP.unregister();
    }
}

