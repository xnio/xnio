package org.jboss.xnio.metadata;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlElement;

/**
 *
 */
@XmlType(name = "pipe-end", namespace = "urn:jboss:io:1.0")
public final class PipeEndMetaData {
    private NamedBeanMetaData executorBean;
    private NamedBeanMetaData handlerBean;

    public NamedBeanMetaData getExecutorBean() {
        return executorBean;
    }

    @XmlElement(name = "executor-bean")
    public void setExecutorBean(final NamedBeanMetaData executorBean) {
        this.executorBean = executorBean;
    }

    public NamedBeanMetaData getHandlerBean() {
        return handlerBean;
    }

    @XmlElement(name = "handler-bean")
    public void setHandlerBean(final NamedBeanMetaData handlerBean) {
        this.handlerBean = handlerBean;
    }
}
