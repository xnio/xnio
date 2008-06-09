package org.jboss.xnio.metadata;

import org.jboss.beans.metadata.spi.BeanMetaData;
import org.jboss.beans.metadata.spi.builder.BeanMetaDataBuilder;
import org.jboss.xnio.spi.TcpConnector;
import java.io.Serializable;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

/**
 *
 */
@XmlType(name = "tcp-connector", namespace = "urn:jboss:io:1.0")
public final class TcpConnectorMetaData implements IoMetaData, Serializable {

    private static final long serialVersionUID = 2101881740511543307L;

    private NamedBeanMetaData executorBean;
    private Boolean keepAlive;
    private Boolean oobInline;
    private Integer receiveBufferSize;
    private Boolean reuseAddress;
    private Integer sendBufferSize;
    private Boolean tcpNoDelay;
    private Integer connectTimeout;
    private String name;

    @XmlElement(name = "executor-bean", namespace = "urn:jboss:io:1.0")
    public void setExecutorBean(final NamedBeanMetaData executorBean) {
        this.executorBean = executorBean;
    }

    public Boolean getKeepAlive() {
        return keepAlive;
    }

    @XmlAttribute(name = "keep-alive")
    public void setKeepAlive(final Boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public Boolean getOobInline() {
        return oobInline;
    }

    @XmlAttribute(name = "oob-inline")
    public void setOobInline(final Boolean oobInline) {
        this.oobInline = oobInline;
    }

    public Integer getReceiveBufferSize() {
        return receiveBufferSize;
    }

    @XmlAttribute(name = "receive-buffer-size")
    public void setReceiveBufferSize(final Integer receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public Boolean getReuseAddress() {
        return reuseAddress;
    }

    @XmlAttribute(name = "reuse-address")
    public void setReuseAddress(final Boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public Integer getSendBufferSize() {
        return sendBufferSize;
    }

    @XmlAttribute(name = "send-buffer-size")
    public void setSendBufferSize(final Integer sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    public Boolean getTcpNoDelay() {
        return tcpNoDelay;
    }

    @XmlAttribute(name = "tcp-no-delay")
    public void setTcpNoDelay(final Boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public Integer getConnectTimeout() {
        return connectTimeout;
    }

    @XmlAttribute(name = "connect-timeout")
    public void setConnectTimeout(final Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public String getName() {
        return name;
    }

    @XmlAttribute(name = "name")
    public void setName(final String name) {
        this.name = name;
    }

    @XmlTransient
    public BeanMetaData getBeanMetaData(final NamedBeanMetaData defaultExecutorBean, final BeanMetaData providerBean) {
        BeanMetaDataBuilder builder = BeanMetaDataBuilder.createBuilder(name, TcpConnector.class.getName());
        builder.setFactory(providerBean);
        builder.setFactoryMethod("createTcpConnector");
        if (executorBean != null) builder.addPropertyMetaData("executor", builder.createInject(executorBean.getName()));
        if (keepAlive != null) builder.addPropertyMetaData("keepAlive", keepAlive);
        if (oobInline != null) builder.addPropertyMetaData("oobInline", oobInline);
        if (receiveBufferSize != null) builder.addPropertyMetaData("receiveBufferSize", receiveBufferSize);
        if (reuseAddress != null) builder.addPropertyMetaData("reuseAddress", reuseAddress);
        if (sendBufferSize != null) builder.addPropertyMetaData("sendBufferSize", sendBufferSize);
        if (tcpNoDelay != null) builder.addPropertyMetaData("tcpNoDelay", tcpNoDelay);
        if (connectTimeout != null) builder.addPropertyMetaData("connectTimeout", connectTimeout);
        return builder.getBeanMetaData();
    }
}