/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
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

import static javax.xml.stream.XMLStreamConstants.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;

import org.wildfly.client.config.ClientConfiguration;
import org.wildfly.client.config.ConfigXMLParseException;
import org.wildfly.client.config.ConfigurationXMLStreamReader;
import org.wildfly.common.net.CidrAddress;

/**
 * The XNIO XML configuration parser.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class XnioXmlParser {

    private static final String NS_XNIO_3_5 = "urn:xnio:3.5";

    static XnioWorker parseWorker(Xnio xnio) throws ConfigXMLParseException, IOException {
        return parseWorker(xnio, ClientConfiguration.getInstance());
    }

    static XnioWorker parseWorker(Xnio xnio, ClientConfiguration clientConfiguration) throws ConfigXMLParseException, IOException {
        final XnioWorker.Builder builder = xnio.createWorkerBuilder();
        if (clientConfiguration == null) {
            return null;
        }
        builder.setDaemon(true);
        final ConfigurationXMLStreamReader reader = clientConfiguration.readConfiguration(Collections.singleton(NS_XNIO_3_5));

        parseDocument(reader, builder);

        return builder.build();
    }

    private static void parseDocument(final ConfigurationXMLStreamReader reader, final XnioWorker.Builder workerBuilder) throws ConfigXMLParseException {
        if (reader.hasNext()) switch (reader.nextTag()) {
            case START_ELEMENT: {
                checkElementNamespace(reader);
                switch (reader.getLocalName()) {
                    case "worker": {
                        parseWorkerElement(reader, workerBuilder);
                        break;
                    }
                    default: throw reader.unexpectedElement();
                }
                break;
            }
            default: {
                throw reader.unexpectedContent();
            }
        }
    }

    private static void parseWorkerElement(final ConfigurationXMLStreamReader reader, final XnioWorker.Builder workerBuilder) throws ConfigXMLParseException {
        requireNoAttributes(reader);
        int foundBits = 0;

        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case END_ELEMENT: {
                    return;
                }
                case START_ELEMENT: {
                    checkElementNamespace(reader);
                    switch (reader.getLocalName()) {
                        case "daemon-threads": {
                            if (isSet(foundBits, 0)) throw reader.unexpectedElement();
                            foundBits = setBit(foundBits, 0);
                            parseDaemonThreads(reader, workerBuilder);
                            break;
                        }
                        case "worker-name": {
                            if (isSet(foundBits, 1)) throw reader.unexpectedElement();
                            foundBits = setBit(foundBits, 1);
                            parseWorkerName(reader, workerBuilder);
                            break;
                        }
                        case "pool-size": {
                            if (isSet(foundBits, 2)) throw reader.unexpectedElement();
                            foundBits = setBit(foundBits, 2);
                            parsePoolSize(reader, workerBuilder);
                            break;
                        }
                        case "task-keepalive": {
                            if (isSet(foundBits, 3)) throw reader.unexpectedElement();
                            foundBits = setBit(foundBits, 3);
                            parseTaskKeepalive(reader, workerBuilder);
                            break;
                        }
                        case "io-threads": {
                            if (isSet(foundBits, 4)) throw reader.unexpectedElement();
                            foundBits = setBit(foundBits, 4);
                            parseIoThreads(reader, workerBuilder);
                            break;
                        }
                        case "stack-size": {
                            if (isSet(foundBits, 5)) throw reader.unexpectedElement();
                            foundBits = setBit(foundBits, 5);
                            parseStackSize(reader, workerBuilder);
                            break;
                        }
                        case "outbound-bind-addresses": {
                            if (isSet(foundBits, 6)) throw reader.unexpectedElement();
                            foundBits = setBit(foundBits, 6);
                            parseOutboundBindAddresses(reader, workerBuilder);
                            break;
                        }
                        default: {
                            throw reader.unexpectedElement();
                        }
                    }
                }
            }
        }
        throw reader.unexpectedDocumentEnd();
    }

    private static void parseDaemonThreads(final ConfigurationXMLStreamReader reader, final XnioWorker.Builder workerBuilder) throws ConfigXMLParseException {
        requireSingleAttribute(reader, "value");
        final boolean daemon = reader.getBooleanAttributeValueResolved(0);
        if (! reader.hasNext()) throw reader.unexpectedDocumentEnd();
        if (reader.nextTag() != END_ELEMENT) throw reader.unexpectedElement();
        workerBuilder.setDaemon(daemon);
        return;
    }

    private static void parseWorkerName(final ConfigurationXMLStreamReader reader, final XnioWorker.Builder workerBuilder) throws ConfigXMLParseException {
        requireSingleAttribute(reader, "value");
        String name = reader.getAttributeValueResolved(0);
        if (! reader.hasNext()) throw reader.unexpectedDocumentEnd();
        if (reader.nextTag() != END_ELEMENT) throw reader.unexpectedElement();
        workerBuilder.setWorkerName(name);
        return;
    }

    private static void parsePoolSize(final ConfigurationXMLStreamReader reader, final XnioWorker.Builder workerBuilder) throws ConfigXMLParseException {
        requireSingleAttribute(reader, "max-threads");
        int threadCount = reader.getIntAttributeValueResolved(0);
        if (! reader.hasNext()) throw reader.unexpectedDocumentEnd();
        if (reader.nextTag() != END_ELEMENT) throw reader.unexpectedElement();
        workerBuilder.setCoreWorkerPoolSize(threadCount);
        workerBuilder.setMaxWorkerPoolSize(threadCount);
        return;
    }

    private static void parseTaskKeepalive(final ConfigurationXMLStreamReader reader, final XnioWorker.Builder workerBuilder) throws ConfigXMLParseException {
        requireSingleAttribute(reader, "value");
        int duration = reader.getIntAttributeValueResolved(0);
        if (! reader.hasNext()) throw reader.unexpectedDocumentEnd();
        if (reader.nextTag() != END_ELEMENT) throw reader.unexpectedElement();
        workerBuilder.setWorkerKeepAlive(duration);
        return;
    }

    private static void parseIoThreads(final ConfigurationXMLStreamReader reader, final XnioWorker.Builder workerBuilder) throws ConfigXMLParseException {
        requireSingleAttribute(reader, "value");
        int threadCount = reader.getIntAttributeValueResolved(0);
        if (! reader.hasNext()) throw reader.unexpectedDocumentEnd();
        if (reader.nextTag() != END_ELEMENT) throw reader.unexpectedElement();
        workerBuilder.setWorkerIoThreads(threadCount);
        return;
    }

    private static void parseStackSize(final ConfigurationXMLStreamReader reader, final XnioWorker.Builder workerBuilder) throws ConfigXMLParseException {
        requireSingleAttribute(reader, "value");
        long stackSize = reader.getLongAttributeValueResolved(0);
        if (! reader.hasNext()) throw reader.unexpectedDocumentEnd();
        if (reader.nextTag() != END_ELEMENT) throw reader.unexpectedElement();
        workerBuilder.setWorkerStackSize(stackSize);
        return;
    }

    private static void parseOutboundBindAddresses(final ConfigurationXMLStreamReader reader, final XnioWorker.Builder workerBuilder) throws ConfigXMLParseException {
        requireNoAttributes(reader);
        for (;;) {
            if (! reader.hasNext()) throw reader.unexpectedDocumentEnd();
            if (reader.nextTag() == START_ELEMENT) {
                checkElementNamespace(reader);
                if (! reader.getLocalName().equals("bind-address")) {
                    throw reader.unexpectedElement();
                }
                parseBindAddress(reader, workerBuilder);
            } else {
                assert reader.getEventType() == END_ELEMENT;
                return;
            }
        }
    }

    private static void parseBindAddress(final ConfigurationXMLStreamReader reader, final XnioWorker.Builder workerBuilder) throws ConfigXMLParseException {
        final int cnt = reader.getAttributeCount();
        InetAddress address = null;
        int port = 0;
        CidrAddress match = null;
        for (int i = 0; i < cnt; i ++) {
            checkAttributeNamespace(reader, i);
            switch (reader.getAttributeLocalName(i)) {
                case "match": {
                    match = reader.getCidrAddressAttributeValueResolved(i);
                    break;
                }
                case "bind-address": {
                    address = reader.getInetAddressAttributeValueResolved(i);
                    break;
                }
                case "bind-port": {
                    port = reader.getIntAttributeValueResolved(i, 0, 65535);
                    break;
                }
                default: {
                    throw reader.unexpectedAttribute(i);
                }
            }
        }
        if (match == null) throw reader.missingRequiredAttribute(null, "match");
        if (address == null) throw reader.missingRequiredAttribute(null, "bind-address");
        workerBuilder.addBindAddressConfiguration(match, new InetSocketAddress(address, port));
        if (! reader.hasNext()) throw reader.unexpectedDocumentEnd();
        if (reader.nextTag() != END_ELEMENT) throw reader.unexpectedElement();
    }

    private static void checkElementNamespace(final ConfigurationXMLStreamReader reader) throws ConfigXMLParseException {
        if (! reader.getNamespaceURI().equals(NS_XNIO_3_5)) {
            throw reader.unexpectedElement();
        }
    }

    private static void checkAttributeNamespace(final ConfigurationXMLStreamReader reader, final int idx) throws ConfigXMLParseException {
        final String attributeNamespace = reader.getAttributeNamespace(idx);
        if (attributeNamespace != null && ! attributeNamespace.isEmpty()) {
            throw reader.unexpectedAttribute(idx);
        }
    }

    private static void requireNoAttributes(final ConfigurationXMLStreamReader reader) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        if (attributeCount > 0) {
            throw reader.unexpectedAttribute(0);
        }
    }

    private static void requireSingleAttribute(final ConfigurationXMLStreamReader reader, final String attributeName) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        if (attributeCount < 1) {
            throw reader.missingRequiredAttribute("", attributeName);
        }
        checkAttributeNamespace(reader, 0);
        if (! reader.getAttributeLocalName(0).equals(attributeName)) {
            throw reader.unexpectedAttribute(0);
        }
        if (attributeCount > 1) {
            throw reader.unexpectedAttribute(1);
        }
    }

    private static boolean isSet(int var, int bit) {
        return (var & 1 << bit) != 0;
    }

    private static int setBit(int var, int bit) {
        return var | 1 << bit;
    }
}
