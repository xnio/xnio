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

package org.jboss.xnio.metadata;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;
import org.jboss.beans.metadata.spi.BeanMetaData;
import org.jboss.beans.metadata.spi.ValueMetaData;
import org.jboss.beans.metadata.spi.builder.BeanMetaDataBuilder;
import org.jboss.xnio.ConfigurableFactory;
import org.jboss.xnio.deployer.SimpleController;

/**
 *
 */
public final class XnioMetaDataHelper {
    private XnioMetaDataHelper() {}

    public static SocketAddress[] getBindAddresses(List<BindAddressMetaData> bindAddressMetaDataList) {
        final int bindCount = bindAddressMetaDataList.size();
        SocketAddress[] addresses = new SocketAddress[bindCount];
        int i = 0;
        for (BindAddressMetaData metaData : bindAddressMetaDataList) {
            addresses[i++] = metaData.getSocketAddress();
        }
        return addresses;
    }

    public static ValueMetaData createInject(BeanMetaDataBuilder builder, NamedBeanMetaData namedBeanMetaData) {
        return builder.createInject(namedBeanMetaData.getName());
    }

    public static void addConfigProperties(BeanMetaDataBuilder builder, AbstractConfigurableMetaData configData) {
        final Integer backlog = configData.getBacklog();
        if (backlog != null) builder.addPropertyMetaData("backlog", backlog);
        final Boolean broadcast = configData.getBroadcast();
        if (broadcast != null) builder.addPropertyMetaData("broadcast", broadcast);
        final Boolean closeAbort = configData.getCloseAbort();
        if (closeAbort != null) builder.addPropertyMetaData("closeAbort", closeAbort);
        final Integer ipTrafficClass = configData.getIpTrafficClass();
        if (ipTrafficClass != null) builder.addPropertyMetaData("ipTrafficClass", ipTrafficClass);
        final Integer ipTos = configData.getIpTos();
        if (ipTos != null) builder.addPropertyMetaData("ipTos", ipTos);
        final Boolean keepAlive = configData.getKeepAlive();
        if (keepAlive != null) builder.addPropertyMetaData("keepAlive", keepAlive);
        final Boolean manageConnections = configData.getManageConnections();
        if (manageConnections != null) builder.addPropertyMetaData("manageConnections", manageConnections);
        final Integer multicastTtl = configData.getMulticastTtl();
        if (multicastTtl != null) builder.addPropertyMetaData("multicastTtl", multicastTtl);
        final Boolean oobInline = configData.getOobInline();
        if (oobInline != null) builder.addPropertyMetaData("oobInline", oobInline);
        final Integer receiveBufferSize = configData.getReceiveBufferSize();
        if (receiveBufferSize != null) builder.addPropertyMetaData("receiveBufferSize", receiveBufferSize);
        final Boolean reuseAddress = configData.getReuseAddress();
        if (reuseAddress != null) builder.addPropertyMetaData("reuseAddress", reuseAddress);
        final Integer sendBufferSize = configData.getSendBufferSize();
        if (sendBufferSize != null) builder.addPropertyMetaData("sendBufferSize", sendBufferSize);
        final Boolean tcpNoDelay = configData.getTcpNoDelay();
        if (tcpNoDelay != null) builder.addPropertyMetaData("tcpNoDelay", tcpNoDelay);
    }

    public static List<BeanMetaData> add(String provider, TcpServerMetaData tcpServerMetaData) {
        final String name = tcpServerMetaData.getName();
        final NamedBeanMetaData handlerFactoryBean = tcpServerMetaData.getHandlerFactoryBean();
        final NamedBeanMetaData executorBean = tcpServerMetaData.getExecutorBean();

        BeanMetaDataBuilder factoryBuilder = BeanMetaDataBuilder.createBuilder(ConfigurableFactory.class.getName());
        if (provider != null) {
            factoryBuilder.setFactory(factoryBuilder.createInject(provider));
        } else {
            // todo: createInject auto-wired based on class
            factoryBuilder.setFactory(factoryBuilder.createInject("XnioProvider"));
        }
        factoryBuilder.setFactoryMethod("createTcpServer");
        if (executorBean != null) factoryBuilder.addConstructorParameter("executor", createInject(factoryBuilder, executorBean));
        factoryBuilder.addConstructorParameter("handlerFactory", factoryBuilder.createInject(handlerFactoryBean.getName()));
        factoryBuilder.addConstructorParameter("bindAddresses", getBindAddresses(tcpServerMetaData.getBindAddresses()));

        BeanMetaDataBuilder builder = BeanMetaDataBuilder.createBuilder(name, SimpleController.class.getName());
        builder.addConstructorParameter(ConfigurableFactory.class.getName(), factoryBuilder.getBeanMetaData());
        addConfigProperties(builder, tcpServerMetaData);

        return Collections.singletonList(builder.getBeanMetaData());
    }
}
