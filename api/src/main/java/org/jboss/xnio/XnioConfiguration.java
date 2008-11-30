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

package org.jboss.xnio;

import java.util.List;

import javax.management.MBeanServer;

/**
 * An abstract configuration for an XNIO provider.  This class provides configuration items that are always common
 * to all providers.
 */
public abstract class XnioConfiguration {

    private List<MBeanServer> mBeanServers;
    private String name;

    /**
     * Construct an uninitialized instance.
     */
    protected XnioConfiguration() {
    }

    /**
     * Get the {@code MBeanServer}s that the provider should register with.  A {@code null} value indicates that
     * the provider should attempt autodetection, based on the value of the {@code xnio.agentid} system property.
     *
     * @return the list of MBean servers, or {@code null} to indicate autodetection
     */
    public List<MBeanServer> getMBeanServers() {
        return mBeanServers;
    }

    /**
     * Set the {@code MBeanServer}s that the provider should register with.  A {@code null} value indicates that
     * the provider should attempt autodetection, based on the value of the {@code xnio.agentid} system property.
     *
     * @param mBeanServers the list of MBean servers, or {@code null} to indicate autodetection
     */
    public void setMBeanServers(final List<MBeanServer> mBeanServers) {
        this.mBeanServers = mBeanServers;
    }

    /**
     * Get the common name of the XNIO provider instance.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Set the common name of the XNIO provider instance.
     *
     * @param name the name
     */
    public void setName(final String name) {
        this.name = name;
    }
}
