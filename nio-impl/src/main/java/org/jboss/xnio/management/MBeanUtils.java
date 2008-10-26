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

package org.jboss.xnio.management;

import java.util.ArrayList;
import java.util.List;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.jboss.xnio.log.Logger;
import org.jboss.xnio.management.NameValuePair;

/**
 *
 */
public class MBeanUtils {

    private static final Logger log = Logger.getLogger(MBeanUtils.class);
    private static final String JMXDOMAIN = "org.jboss.xnio";
    private static final String AGENTID = JMXDOMAIN + ".agentid";
    private static final List<NameValuePair> NO_ADDL_PROPS = new ArrayList<NameValuePair>();

    public static ObjectName getObjectName(final Object mBean) {
        return getObjectName(mBean, NO_ADDL_PROPS);
    }

    public static ObjectName getObjectName(final Object mBean, final List<NameValuePair> additionalAttributes) {

        if (mBean == null) {
            throw new IllegalArgumentException("MBean cannot be null");
        }

        StringBuilder nameString = new StringBuilder();
        NameValuePair instanceAttribute = new NameValuePair("Instance", mBean.toString());
        appendProperty(nameString, instanceAttribute);
        if (additionalAttributes != null) {
            for (final NameValuePair nameValuePair : additionalAttributes) {
                appendProperty(nameString, nameValuePair);
            }
        }
        nameString.insert(0,":");
        nameString.insert(0,JMXDOMAIN);

        ObjectName name = null;
        try {
            name = new ObjectName(nameString.toString());
        } catch (final MalformedObjectNameException e) {
            log.error(e,
                    "MalformedObjectNameException for argument '%s'",
                    nameString.toString());
            // rethrow as an unchecked exception
        }
        return name;
    }

    private static void appendProperty(final StringBuilder stringBuilder, final NameValuePair nameValuePair) {
        if (stringBuilder.length() > 0) {
            stringBuilder.append(",");
        }
        stringBuilder.append(nameValuePair.getName());
        stringBuilder.append("=");
        stringBuilder.append(nameValuePair.getValue());

    }

    @SuppressWarnings("unchecked")
    public static void registerMBean(final Object mBean,
            final ObjectName mBeanName) {

        List<MBeanServer> mBeanServers = getMBeanServers();

        if (mBeanServers.isEmpty()) {
            log.warn("No MBean servers to register MBean '%s'", mBeanName);
        }

        for (MBeanServer server : mBeanServers) {
            try {
                server.registerMBean(mBean, mBeanName);
            } catch (final InstanceAlreadyExistsException e) {
                log.warn("MBean name '%s' already registered in MBeanServer '%s'",
                        mBeanName, server);
            } catch (final MBeanRegistrationException e) {
                log.warn(e, "MBean name '%s' was not registered in an MBean server because preregister of '%s' threw an exception",
                        mBeanName, mBean);
                break;
            } catch (final NotCompliantMBeanException e) {
                log.warn(e, "MBean '%s' (name '%s') is not a JMX compliant MBean",
                        mBean, mBeanName);
                break;
            }
        }
    }

    public static void unregisterMBean(final ObjectName mBeanName) {
        for (MBeanServer server : getMBeanServers()) {
            try {
                server.unregisterMBean(mBeanName);
            } catch (final InstanceNotFoundException e) {
                log.debug("MBean name '%s' not found in MBeanServer '%s'",
                        mBeanName, server);
            } catch (final MBeanRegistrationException e) {
                log.warn(e, "MBean name '%s' was not deregistered because prederegister of the MBean threw an exception",
                        mBeanName);
                break;
            }
        }
    }

    /**
     * @return A list of MBeanServers using the folowing rules. If the runtime
     *         property org.jboss.xnio.agentid is not specified, all
     *         MBeanServers will be returned. Otherwise if specified, only the
     *         MBeanServers matching an agentId which matches an entry in the
     *         property will be returned. If any of the MBeanServers are not
     *         found, warning will be written to the log and no programmatic
     *         feedback is given.
     */
    @SuppressWarnings("unchecked")
    private static List<MBeanServer> getMBeanServers() {
        String agentIds = System.getProperty(AGENTID);
        List<MBeanServer> mBeanServers;
        if ((agentIds != null) && (agentIds.length() == 0)) {
            String[] ids = agentIds.split("[,;:]+");
            mBeanServers = new ArrayList<MBeanServer>();
            for (String id : ids) {
                List<MBeanServer> servers = MBeanServerFactory.findMBeanServer(id);
                if (servers == null) {
                    log.warn("Couldn't find MBeanServer '%s'", id);
                } else {
                    mBeanServers.addAll(servers);
                }
            }
        } else {
            mBeanServers = MBeanServerFactory.findMBeanServer(null);
        }
        return mBeanServers;
    }
}
