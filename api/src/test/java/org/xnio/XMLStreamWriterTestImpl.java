/**
 * 
 */
package org.xnio;

import java.util.Objects;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/*
 * XMLStreamWriterTestImpl implements only the close() method for test in XnioUtil.
 *
 * @author <a href="mailto:boris@unckel.net">Boris Unckel</a>
 */
class XMLStreamWriterTestImpl implements XMLStreamWriter {

    @SuppressWarnings("rawtypes")
    private final Class clazzToThrow;

    public XMLStreamWriterTestImpl(@SuppressWarnings("rawtypes") final Class clazz) {
        this.clazzToThrow = Objects.requireNonNull(clazz);
    }

    @Override
    public void close() throws XMLStreamException {
        try {
            Throwable toThrow = (Throwable) clazzToThrow.newInstance();
            if (toThrow instanceof RuntimeException) {
                throw (RuntimeException) toThrow;
            }
            if (toThrow instanceof Error) {
                throw (Error) toThrow;
            }
            if (toThrow instanceof XMLStreamException) {
                throw (XMLStreamException) toThrow;
            }
            throw new RuntimeException(toThrow);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public void writeStartElement(String localName) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeStartElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeEmptyElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeEmptyElement(String localName) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeEndElement() throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeEndDocument() throws XMLStreamException {
        // NOP
    }

    @Override
    public void flush() throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeAttribute(String localName, String value) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeAttribute(String prefix, String namespaceURI, String localName, String value)
            throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeAttribute(String namespaceURI, String localName, String value) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeNamespace(String prefix, String namespaceURI) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeDefaultNamespace(String namespaceURI) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeComment(String data) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeProcessingInstruction(String target) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeProcessingInstruction(String target, String data) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeCData(String data) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeDTD(String dtd) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeEntityRef(String name) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeStartDocument() throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeStartDocument(String version) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeStartDocument(String encoding, String version) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeCharacters(String text) throws XMLStreamException {
        // NOP
    }

    @Override
    public void writeCharacters(char[] text, int start, int len) throws XMLStreamException {
        // NOP
    }

    @Override
    public String getPrefix(String uri) throws XMLStreamException {
        // NOP
        return null;
    }

    @Override
    public void setPrefix(String prefix, String uri) throws XMLStreamException {
        // NOP
    }

    @Override
    public void setDefaultNamespace(String uri) throws XMLStreamException {
        // NOP
    }

    @Override
    public void setNamespaceContext(NamespaceContext context) throws XMLStreamException {
        // NOP
    }

    @Override
    public NamespaceContext getNamespaceContext() {
        // NOP
        return null;
    }

    @Override
    public Object getProperty(String name) throws IllegalArgumentException {
        // NOP
        return null;
    }

}
