/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2021 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xnio;

import java.util.Objects;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/*
 * XMLStreamReaderTestImpl implements only the close() method for test in XnioUtil.
 *
 * @author <a href="mailto:boris@unckel.net">Boris Unckel</a>
 */
class XMLStreamReaderTestImpl implements XMLStreamReader {

    @SuppressWarnings("rawtypes")
    private final Class clazzToThrow;

    public XMLStreamReaderTestImpl(@SuppressWarnings("rawtypes") final Class clazz) {
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
    public Object getProperty(String name) throws IllegalArgumentException {
        // NOP
        return null;
    }

    @Override
    public int next() throws XMLStreamException {
        // NOP
        return 0;
    }

    @Override
    public void require(int type, String namespaceURI, String localName) throws XMLStreamException {
        // NOP
    }

    @Override
    public String getElementText() throws XMLStreamException {
        // NOP
        return null;
    }

    @Override
    public int nextTag() throws XMLStreamException {
        // NOP
        return 0;
    }

    @Override
    public boolean hasNext() throws XMLStreamException {
        // NOP
        return false;
    }

    @Override
    public String getNamespaceURI(String prefix) {
        // NOP
        return null;
    }

    @Override
    public boolean isStartElement() {
        // NOP
        return false;
    }

    @Override
    public boolean isEndElement() {
        // NOP
        return false;
    }

    @Override
    public boolean isCharacters() {
        // NOP
        return false;
    }

    @Override
    public boolean isWhiteSpace() {
        // NOP
        return false;
    }

    @Override
    public String getAttributeValue(String namespaceURI, String localName) {
        // NOP
        return null;
    }

    @Override
    public int getAttributeCount() {
        // NOP
        return 0;
    }

    @Override
    public QName getAttributeName(int index) {
        // NOP
        return null;
    }

    @Override
    public String getAttributeNamespace(int index) {
        // NOP
        return null;
    }

    @Override
    public String getAttributeLocalName(int index) {
        // NOP
        return null;
    }

    @Override
    public String getAttributePrefix(int index) {
        // NOP
        return null;
    }

    @Override
    public String getAttributeType(int index) {
        // NOP
        return null;
    }

    @Override
    public String getAttributeValue(int index) {
        // NOP
        return null;
    }

    @Override
    public boolean isAttributeSpecified(int index) {
        // NOP
        return false;
    }

    @Override
    public int getNamespaceCount() {
        // NOP
        return 0;
    }

    @Override
    public String getNamespacePrefix(int index) {
        // NOP
        return null;
    }

    @Override
    public String getNamespaceURI(int index) {
        // NOP
        return null;
    }

    @Override
    public NamespaceContext getNamespaceContext() {
        // NOP
        return null;
    }

    @Override
    public int getEventType() {
        // NOP
        return 0;
    }

    @Override
    public String getText() {
        // NOP
        return null;
    }

    @Override
    public char[] getTextCharacters() {
        // NOP
        return null;
    }

    @Override
    public int getTextCharacters(int sourceStart, char[] target, int targetStart, int length)
            throws XMLStreamException {
        // NOP
        return 0;
    }

    @Override
    public int getTextStart() {
        // NOP
        return 0;
    }

    @Override
    public int getTextLength() {
        // NOP
        return 0;
    }

    @Override
    public String getEncoding() {
        // NOP
        return null;
    }

    @Override
    public boolean hasText() {
        // NOP
        return false;
    }

    @Override
    public Location getLocation() {
        // NOP
        return null;
    }

    @Override
    public QName getName() {
        // NOP
        return null;
    }

    @Override
    public String getLocalName() {
        // NOP
        return null;
    }

    @Override
    public boolean hasName() {
        // NOP
        return false;
    }

    @Override
    public String getNamespaceURI() {
        // NOP
        return null;
    }

    @Override
    public String getPrefix() {
        // NOP
        return null;
    }

    @Override
    public String getVersion() {
        // NOP
        return null;
    }

    @Override
    public boolean isStandalone() {
        // NOP
        return false;
    }

    @Override
    public boolean standaloneSet() {
        // NOP
        return false;
    }

    @Override
    public String getCharacterEncodingScheme() {
        // NOP
        return null;
    }

    @Override
    public String getPITarget() {
        // NOP
        return null;
    }

    @Override
    public String getPIData() {
        // NOP
        return null;
    }

}
