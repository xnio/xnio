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
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

import javax.management.MBeanServer;

/**
 * A configuration for an XNIO provider.
 *
 * @since 1.2
 */
public final class XnioConfiguration {

    private List<MBeanServer> mBeanServers;
    private ThreadFactory threadFactory;
    private Executor executor;
    private String name;
    private OptionMap optionMap;

    /**
     * Construct an uninitialized instance.
     */
    public XnioConfiguration() {
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
     * Get the thread factory to use for any created threads.
     *
     * @return the thread factory
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /**
     * Set the thread factory to use for any created threads.
     *
     * @param threadFactory the thread factory
     */
    public void setThreadFactory(final ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
    }

    /**
     * Get the default listener executor for the XNIO provider instance.
     *
     * @return the default listener executor
     */
    public Executor getExecutor() {
        return executor;
    }

    /**
     * Set the default listener executor for the XNIO provider instance.
     *
     * @param executor the default listener executor
     */
    public void setExecutor(final Executor executor) {
        this.executor = executor;
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

    /**
     * Get the option map associated with this configuration.
     *
     * @return the option map
     */
    public OptionMap getOptionMap() {
        return optionMap;
    }

    /**
     * Set the option map associated with this configuration.
     *
     * @param optionMap the option map
     */
    public void setOptionMap(final OptionMap optionMap) {
        this.optionMap = optionMap;
    }
}
