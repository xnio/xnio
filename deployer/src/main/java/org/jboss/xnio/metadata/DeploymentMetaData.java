package org.jboss.xnio.metadata;

import org.jboss.beans.metadata.spi.BeanMetaDataFactory;
import org.jboss.beans.metadata.spi.BeanMetaData;
import org.jboss.beans.metadata.spi.builder.BeanMetaDataBuilder;
import org.jboss.xb.annotations.JBossXmlSchema;
import org.jboss.xnio.spi.Provider;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlRootElement;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

/**
 *
 */
@XmlType(name = "deployment", namespace = "urn:jboss:io:1.0")
@XmlRootElement(namespace = "urn:jboss:io:1.0", name = "deployment")
@JBossXmlSchema(namespace = "urn:jboss:io:1.0")
public final class DeploymentMetaData implements BeanMetaDataFactory, Serializable {
    private static final long serialVersionUID = -1616974182990862225L;

    private NamedBeanMetaData executorBean;
    private List<TcpServerMetaData> tcpServers = arrayList();
    private List<TcpConnectorMetaData> tcpConnectors = arrayList();
    private List<TcpClientMetaData> tcpClients = arrayList();
    private List<TcpConnectionMetaData> tcpConnections = arrayList();
    private List<UdpServerMetaData> udpServers = arrayList();
    private List<PipeMetaData> pipes = arrayList();

    private static <T> List<T> arrayList() {
        return new ArrayList<T>();
    }

    public NamedBeanMetaData getExecutorBean() {
        return executorBean;
    }

    @XmlElement(name = "executor-bean", namespace = "urn:jboss:io:1.0")
    public void setExecutorBean(final NamedBeanMetaData executorBean) {
        this.executorBean = executorBean;
    }

    public List<TcpServerMetaData> getTcpServers() {
        return tcpServers;
    }

    @XmlElement(name = "tcp-server", namespace = "urn:jboss:io:1.0", type = TcpServerMetaData.class)
    public void setTcpServers(final List<TcpServerMetaData> tcpServers) {
        this.tcpServers = tcpServers;
    }

    public List<TcpConnectorMetaData> getTcpConnectors() {
        return tcpConnectors;
    }

    @XmlElement(name = "tcp-connector", namespace = "urn:jboss:io:1.0", type = TcpConnectorMetaData.class)
    public void setTcpConnectors(final List<TcpConnectorMetaData> tcpConnectors) {
        this.tcpConnectors = tcpConnectors;
    }

    public List<TcpClientMetaData> getTcpClients() {
        return tcpClients;
    }

    @XmlElement(name = "tcp-client", namespace = "urn:jboss:io:1.0", type = TcpClientMetaData.class)
    public void setTcpClients(final List<TcpClientMetaData> tcpClients) {
        this.tcpClients = tcpClients;
    }

    public List<TcpConnectionMetaData> getTcpConnections() {
        return tcpConnections;
    }

    @XmlElement(name = "tcp-connection", namespace = "urn:jboss:io:1.0", type = TcpConnectionMetaData.class)
    public void setTcpConnections(final List<TcpConnectionMetaData> tcpConnections) {
        this.tcpConnections = tcpConnections;
    }

    public List<UdpServerMetaData> getUdpServers() {
        return udpServers;
    }

    @XmlElement(name = "udp-server", namespace = "urn:jboss:io:1.0", type = UdpServerMetaData.class)
    public void setUdpServers(final List<UdpServerMetaData> udpServers) {
        this.udpServers = udpServers;
    }

    public List<PipeMetaData> getPipes() {
        return pipes;
    }

    @XmlElement(name = "pipe", namespace = "urn:jboss:io:1.0", type = PipeMetaData.class)
    public void setPipes(final List<PipeMetaData> pipes) {
        this.pipes = pipes;
    }

    @XmlTransient
    public List<BeanMetaData> getBeans() {

        BeanMetaDataBuilder builder = BeanMetaDataBuilder.createBuilder("XnioProvider", Provider.class.getName());
        BeanMetaData nioCoreBeanMetaData = builder.getBeanMetaData();
        final List<BeanMetaData> beans = new ArrayList<BeanMetaData>();
        beans.add(nioCoreBeanMetaData);
        for (IoMetaData metaData : tcpServers) {
            beans.add(metaData.getBeanMetaData(executorBean, nioCoreBeanMetaData));
        }
        for (IoMetaData metaData : tcpConnectors) {
            beans.add(metaData.getBeanMetaData(executorBean, nioCoreBeanMetaData));
        }
        for (IoMetaData metaData : tcpClients) {
            beans.add(metaData.getBeanMetaData(executorBean, nioCoreBeanMetaData));
        }
        for (IoMetaData metaData : tcpConnections) {
            beans.add(metaData.getBeanMetaData(executorBean, nioCoreBeanMetaData));
        }
        for (IoMetaData metaData : udpServers) {
            beans.add(metaData.getBeanMetaData(executorBean, nioCoreBeanMetaData));
        }
        for (IoMetaData metaData : pipes) {
            beans.add(metaData.getBeanMetaData(executorBean, nioCoreBeanMetaData));
        }
        return beans;
    }
}
