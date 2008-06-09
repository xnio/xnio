package org.jboss.xnio.metadata;

import org.jboss.beans.metadata.spi.BeanMetaData;

import javax.xml.bind.annotation.XmlTransient;

/**
 *
 */
public interface IoMetaData {
    @XmlTransient
    BeanMetaData getBeanMetaData(NamedBeanMetaData defaultExecutorBean, BeanMetaData nioCoreBean);
}
