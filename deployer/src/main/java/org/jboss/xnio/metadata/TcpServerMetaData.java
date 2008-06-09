package org.jboss.xnio.metadata;

import org.jboss.beans.metadata.spi.BeanMetaData;
import org.jboss.beans.metadata.spi.builder.BeanMetaDataBuilder;
import org.jboss.xnio.spi.TcpServer;
import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;
import java.net.SocketAddress;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

/**
 *
 */
@XmlType(name = "tcp-server", namespace = "urn:jboss:io:1.0")
public final class TcpServerMetaData implements IoMetaData, Serializable {

    private static final long serialVersionUID = 4318538124489300012L;

    private List<BindAddressMetaData> bindAddresses = arrayList();
    private NamedBeanMetaData handlerFactoryBean;
    private NamedBeanMetaData executorBean;
    private Boolean keepAlive;
    private Boolean oobInline;
    private Integer receiveBufferSize;
    private Boolean reuseAddress;
    private Boolean tcpNoDelay;
    private Integer backlog;
    private String name;

    private static <T> List<T> arrayList() {
        return new ArrayList<T>();
    }

    public List<BindAddressMetaData> getBindAddresses() {
        return bindAddresses;
    }

    @XmlElement(name = "bind-address", namespace = "urn:jboss:io:1.0", type = BindAddressMetaData.class)
    public void setBindAddresses(final List<BindAddressMetaData> bindAddresses) {
        this.bindAddresses = bindAddresses;
    }

    public NamedBeanMetaData getHandlerFactoryBean() {
        return handlerFactoryBean;
    }

    @XmlElement(name = "handler-factory-bean", namespace = "urn:jboss:io:1.0", required = true)
    public void setHandlerFactoryBean(final NamedBeanMetaData handlerFactoryBean) {
        this.handlerFactoryBean = handlerFactoryBean;
    }

    public NamedBeanMetaData getExecutorBean() {
        return executorBean;
    }

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

    public Boolean getTcpNoDelay() {
        return tcpNoDelay;
    }

    @XmlAttribute(name = "tcp-no-delay")
    public void setTcpNoDelay(final Boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public Integer getBacklog() {
        return backlog;
    }

    @XmlAttribute(name = "backlog")
    public void setBacklog(final Integer backlog) {
        this.backlog = backlog;
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
        BeanMetaDataBuilder builder = BeanMetaDataBuilder.createBuilder(name, TcpServer.class.getName());
        builder.setFactory(providerBean);
        builder.setFactoryMethod("createTcpServer");
        final int bindCount = bindAddresses.size();
        SocketAddress[] addresses = new SocketAddress[bindCount];
        int i = 0;
        for (BindAddressMetaData metaData : bindAddresses) {
            addresses[i++] = metaData.getSocketAddress();
        }
        builder.addPropertyMetaData("bindAddresses", addresses);
        builder.addPropertyMetaData("handlerFactory", builder.createInject(handlerFactoryBean.getName()));
        if (executorBean != null) builder.addPropertyMetaData("executor", builder.createInject(executorBean.getName()));
        if (keepAlive != null) builder.addPropertyMetaData("keepAlive", keepAlive);
        if (oobInline != null) builder.addPropertyMetaData("oobInline", oobInline);
        if (receiveBufferSize != null) builder.addPropertyMetaData("receiveBufferSize", receiveBufferSize);
        if (reuseAddress != null) builder.addPropertyMetaData("reuseAddress", reuseAddress);
        if (tcpNoDelay != null) builder.addPropertyMetaData("tcpNoDelay", tcpNoDelay);
        if (backlog != null) builder.addPropertyMetaData("backlog", backlog);
        return builder.getBeanMetaData();
    }
}
