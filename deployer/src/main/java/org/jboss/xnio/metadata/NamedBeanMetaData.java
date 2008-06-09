package org.jboss.xnio.metadata;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAttribute;

/**
 *
 */
@XmlType(name = "bean", namespace = "urn:jboss:io:1.0")
public final class NamedBeanMetaData implements Serializable {
    private static final long serialVersionUID = -2118014715379357274L;

    private String name;

    public String getName() {
        return name;
    }

    @XmlAttribute(required = true)
    public void setName(final String name) {
        this.name = name;
    }
}
