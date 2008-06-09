package org.jboss.xnio.metadata;

import org.jboss.beans.metadata.spi.BeanMetaData;
import org.jboss.beans.metadata.spi.builder.BeanMetaDataBuilder;
import org.jboss.xnio.ConnectionAddress;
import org.jboss.xnio.StreamIoClient;
import java.io.Serializable;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

/**
 *
 */
@XmlType(name = "tcp-client", namespace = "urn:jboss:io:1.0")
public final class TcpClientMetaData implements IoMetaData, Serializable {

    private static final long serialVersionUID = 2101881740511543307L;

    private NamedBeanMetaData tcpConnectorBean;
    private TcpConnectorMetaData tcpConnectorMetaData;
    private ConnectAddressMetaData[] connectAddresses = new ConnectAddressMetaData[0];
    private String name;

    public NamedBeanMetaData getTcpConnectorBean() {
        return tcpConnectorBean;
    }

    @XmlElement(name = "tcp-connector-bean", namespace = "urn:jboss:io:1.0")
    public void setTcpConnectorBean(final NamedBeanMetaData tcpConnectorBean) {
        this.tcpConnectorBean = tcpConnectorBean;
    }

    public TcpConnectorMetaData getTcpConnectorMetaData() {
        return tcpConnectorMetaData;
    }

    @XmlElement(name = "tcp-connector", namespace = "urn:jboss:io:1.0")
    public void setTcpConnectorMetaData(final TcpConnectorMetaData tcpConnectorMetaData) {
        this.tcpConnectorMetaData = tcpConnectorMetaData;
    }

    public ConnectAddressMetaData[] getConnectAddresses() {
        return connectAddresses;
    }

    @XmlElement(name = "connect-address", namespace = "urn:jboss:io:1.0")
    public void setConnectAddresses(final ConnectAddressMetaData[] connectAddresses) {
        this.connectAddresses = connectAddresses;
    }

    public String getName() {
        return name;
    }

    @XmlAttribute(name = "name")
    public void setName(final String name) {
        this.name = name;
    }

    @XmlTransient
    public BeanMetaData getBeanMetaData(final NamedBeanMetaData defaultExecutorBean, final BeanMetaData nioCoreBean) {
        BeanMetaDataBuilder builder = BeanMetaDataBuilder.createBuilder(name, StreamIoClient.class.getName());
        if (tcpConnectorBean != null) builder.addPropertyMetaData("connector", builder.createInject(tcpConnectorBean.getName()));
        if (tcpConnectorMetaData != null) builder.addPropertyMetaData("connector", tcpConnectorMetaData.getBeanMetaData(defaultExecutorBean, nioCoreBean));
        final int addressCount = connectAddresses.length;
        ConnectionAddress[] connectionAddresses = new ConnectionAddress[addressCount];
        for (int i = 0; i < addressCount; i ++) {
            connectionAddresses[i] = connectAddresses[i].getConnectionAddress();
        }
        builder.addPropertyMetaData("addresses", connectionAddresses);
        return builder.getBeanMetaData();
    }
}