/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.xnio.management;

/**
 *
 */
public class BoundServer extends Server implements BoundServerMBean{

    public BoundServer(final BoundServerMBean bean) {
        super(bean);
    }

    public int getTrafficClass() {
        BoundServerMBean bean = (BoundServerMBean) getServerMBean();
        if (bean != null) {
            return bean.getTrafficClass();
        }
        return -1;
    }

    public boolean isBroadcast() {
        BoundServerMBean bean = (BoundServerMBean) getServerMBean();
        if (bean != null) {
            return bean.isBroadcast();
        }
        return false;
    }

    public void setBroadcast(final boolean broadcast) {
        BoundServerMBean bean = (BoundServerMBean) getServerMBean();
        if (bean != null) {
            bean.setBroadcast(broadcast);
        }
    }

    public void setTrafficClass(final int trafficClass) {
        BoundServerMBean bean = (BoundServerMBean) getServerMBean();
        if (bean != null) {
            bean.setTrafficClass(trafficClass);
        }
    }
}
