package org.jboss.xnio.metadata;

import org.jboss.beans.metadata.spi.BeanMetaData;
import org.jboss.beans.metadata.spi.builder.BeanMetaDataBuilder;
import org.jboss.xnio.helpers.ConnectionHelper;
import java.io.Serializable;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

/**
 *
 */
@XmlType(name = "tcp-connection", namespace = "urn:jboss:io:1.0")
public final class TcpConnectionMetaData implements IoMetaData, Serializable {

    private static final long serialVersionUID = 2101881740511543307L;

    private NamedBeanMetaData handlerBean;
    private NamedBeanMetaData tcpClientBean;
    private TcpClientMetaData tcpClientMetaData;
    private NamedBeanMetaData executorBean;
    private NamedBeanMetaData scheduledExecutorBean;
    private int reconnectInterval = -1;
    private String name;

    public NamedBeanMetaData getHandlerBean() {
        return handlerBean;
    }

    @XmlElement(name = "handler-bean", namespace = "urn:jboss:io:1.0")
    public void setHandlerBean(final NamedBeanMetaData handlerBean) {
        this.handlerBean = handlerBean;
    }

    public NamedBeanMetaData getTcpClientBean() {
        return tcpClientBean;
    }

    @XmlElement(name = "tcp-client-bean", namespace = "urn:jboss:io:1.0")
    public void setTcpClientBean(final NamedBeanMetaData tcpClientBean) {
        this.tcpClientBean = tcpClientBean;
    }

    public TcpClientMetaData getTcpClientMetaData() {
        return tcpClientMetaData;
    }

    @XmlElement(name = "tcp-client", namespace = "urn:jboss:io:1.0")
    public void setTcpClientMetaData(final TcpClientMetaData tcpClientMetaData) {
        this.tcpClientMetaData = tcpClientMetaData;
    }

    public NamedBeanMetaData getExecutorBean() {
        return executorBean;
    }

    @XmlElement(name = "executor-bean", namespace = "urn:jboss:io:1.0")
    public void setExecutorBean(final NamedBeanMetaData executorBean) {
        this.executorBean = executorBean;
    }

    public String getName() {
        return name;
    }

    @XmlAttribute(name = "name")
    public void setName(final String name) {
        this.name = name;
    }

    public NamedBeanMetaData getScheduledExecutorBean() {
        return scheduledExecutorBean;
    }

    @XmlElement(name = "scheduled-executor-bean", namespace = "urn:jboss:io:1.0")
    public void setScheduledExecutorBean(final NamedBeanMetaData scheduledExecutorBean) {
        this.scheduledExecutorBean = scheduledExecutorBean;
    }

    public int getReconnectInterval() {
        return reconnectInterval;
    }

    @XmlAttribute(name = "reconnect-interval")
    public void setReconnectInterval(final int reconnectInterval) {
        this.reconnectInterval = reconnectInterval;
    }

    @XmlTransient
    public BeanMetaData getBeanMetaData(final NamedBeanMetaData defaultExecutorBean, final BeanMetaData providerBean) {
        BeanMetaDataBuilder builder = BeanMetaDataBuilder.createBuilder(name, ConnectionHelper.class.getName());
        if (tcpClientBean != null) builder.addPropertyMetaData("client", builder.createInject(tcpClientBean.getName()));
        if (tcpClientMetaData != null) builder.addPropertyMetaData("client", tcpClientMetaData.getBeanMetaData(executorBean, providerBean));
        builder.addPropertyMetaData("handler", builder.createInject(handlerBean.getName()));
        builder.addPropertyMetaData("reconnectTime", Integer.valueOf(reconnectInterval));
        if (scheduledExecutorBean != null) builder.addPropertyMetaData("scheduledExecutor", builder.createInject(scheduledExecutorBean.getName()));
        return builder.getBeanMetaData();
    }
}