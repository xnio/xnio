package org.jboss.xnio.metadata;

import org.jboss.beans.metadata.spi.BeanMetaData;
import org.jboss.beans.metadata.spi.builder.BeanMetaDataBuilder;
import org.jboss.xnio.spi.PipeEnd;
import org.jboss.xnio.spi.OneWayPipe;

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
        final BeanMetaDataBuilder builder = BeanMetaDataBuilder.createBuilder(name, OneWayPipe.class.getName());
        builder.setFactory(providerBean);
        builder.setFactoryMethod("createPipe");
        final NamedBeanMetaData pipeExecutorBean = executorBean;
        if (pipeExecutorBean != null) builder.addPropertyMetaData("executor", pipeExecutorBean.getName());
        final BeanMetaData pipeBeanMetaData = builder.getBeanMetaData();

        final BeanMetaDataBuilder sourceBuilder = BeanMetaDataBuilder.createBuilder(PipeEnd.class.getName());
        sourceBuilder.setFactory(pipeBeanMetaData);
        sourceBuilder.setFactoryMethod("getSourceEnd");
        final NamedBeanMetaData sourceExecutorBean = sourceEnd.getExecutorBean();
        if (sourceExecutorBean != null) sourceBuilder.addPropertyMetaData("executor", sourceExecutorBean.getName());

        final BeanMetaDataBuilder sinkBuilder = BeanMetaDataBuilder.createBuilder(PipeEnd.class.getName());
        sinkBuilder.setFactory(pipeBeanMetaData);
        sinkBuilder.setFactoryMethod("getSinkEnd");
        final NamedBeanMetaData sinkExecutorBean = sinkEnd.getExecutorBean();
        if (sinkExecutorBean != null) sinkBuilder.addPropertyMetaData("executor", sinkExecutorBean.getName());

        return pipeBeanMetaData;
    }
}