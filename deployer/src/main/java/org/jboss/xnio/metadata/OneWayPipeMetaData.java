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

import org.jboss.beans.metadata.spi.BeanMetaData;
import org.jboss.beans.metadata.spi.builder.BeanMetaDataBuilder;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlAttribute;

/**
 *
 */
@XmlType(name = "one-way-pipe", namespace = "urn:jboss:io:1.0")
public final class OneWayPipeMetaData implements IoMetaData {

    private NamedBeanMetaData executorBean;
    private PipeEndMetaData sourceEnd;
    private PipeEndMetaData sinkEnd;
    private String name;

    public NamedBeanMetaData getExecutorBean() {
        return executorBean;
    }

    @XmlElement(name = "executor-bean", namespace = "urn:jboss:io:1.0")
    public void setExecutorBean(final NamedBeanMetaData executorBean) {
        this.executorBean = executorBean;
    }

    public PipeEndMetaData getSourceEnd() {
        return sourceEnd;
    }

    @XmlElement(name = "source-end", namespace = "urn:jboss:io:1.0")
    public void setSourceEnd(final PipeEndMetaData sourceEnd) {
        this.sourceEnd = sourceEnd;
    }

    public PipeEndMetaData getSinkEnd() {
        return sinkEnd;
    }

    @XmlElement(name = "sink-end", namespace = "urn:jboss:io:1.0")
    public void setSinkEnd(final PipeEndMetaData sinkEnd) {
        this.sinkEnd = sinkEnd;
    }

    public String getName() {
        return name;
    }

    @XmlAttribute(name = "name")
    public void setName(final String name) {
        this.name = name;
    }

    public BeanMetaData getBeanMetaData(final NamedBeanMetaData defaultExecutorBean, final BeanMetaData providerBean) {
        final BeanMetaDataBuilder builder = BeanMetaDataBuilder.createBuilder(name, Object.class.getName());
        builder.setFactory(providerBean);
        builder.setFactoryMethod("createPipe");
        final NamedBeanMetaData pipeExecutorBean = executorBean;
        if (pipeExecutorBean != null) builder.addPropertyMetaData("executor", pipeExecutorBean.getName());
        final BeanMetaData pipeBeanMetaData = builder.getBeanMetaData();

        final BeanMetaDataBuilder sourceBuilder = BeanMetaDataBuilder.createBuilder(Object.class.getName());
        sourceBuilder.setFactory(pipeBeanMetaData);
        sourceBuilder.setFactoryMethod("getSourceEnd");
        final NamedBeanMetaData sourceExecutorBean = sourceEnd.getExecutorBean();
        if (sourceExecutorBean != null) sourceBuilder.addPropertyMetaData("executor", sourceExecutorBean.getName());

        final BeanMetaDataBuilder sinkBuilder = BeanMetaDataBuilder.createBuilder(Object.class.getName());
        sinkBuilder.setFactory(pipeBeanMetaData);
        sinkBuilder.setFactoryMethod("getSinkEnd");
        final NamedBeanMetaData sinkExecutorBean = sinkEnd.getExecutorBean();
        if (sinkExecutorBean != null) sinkBuilder.addPropertyMetaData("executor", sinkExecutorBean.getName());

        return pipeBeanMetaData;
    }
}