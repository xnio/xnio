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
import java.util.Collections;

import org.wildfly.client.config.ClientConfiguration;
import org.wildfly.client.config.ConfigXMLParseException;
import org.wildfly.client.config.ConfigurationXMLStreamReader;

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
        final OptionMap.Builder mapBuilder = OptionMap.builder();
        if (clientConfiguration == null) {
            return null;
        }
        mapBuilder.set(Options.THREAD_DAEMON, true);
        final ConfigurationXMLStreamReader reader = clientConfiguration.readConfiguration(Collections.singleton(NS_XNIO_3_5));

        parseWorkerElement(reader, mapBuilder);

        return xnio.createWorker(mapBuilder.getMap());
    }

    private static void parseWorkerElement(final ConfigurationXMLStreamReader reader, final OptionMap.Builder mapBuilder) throws ConfigXMLParseException {
        requireNoAttributes(reader);
        int foundBits = 0;
        foundBits = setBit(foundBits, 1);

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
                            parseDaemonThreads(reader, mapBuilder);
                            break;
                        }
                        case "worker-name": {
                            if (isSet(foundBits, 1)) throw reader.unexpectedElement();
                            foundBits = setBit(foundBits, 1);
                            parseWorkerName(reader, mapBuilder);
                            break;
                        }
                        case "pool-size": {
                            if (isSet(foundBits, 2)) throw reader.unexpectedElement();
                            foundBits = setBit(foundBits, 2);
                            parsePoolSize(reader, mapBuilder);
                            break;
                        }
                        case "task-keepalive": {
                            if (isSet(foundBits, 3)) throw reader.unexpectedElement();
                            foundBits = setBit(foundBits, 3);
                            parseTaskKeepalive(reader, mapBuilder);
                            break;
                        }
                        case "io-threads": {
                            if (isSet(foundBits, 4)) throw reader.unexpectedElement();
                            foundBits = setBit(foundBits, 3);
                            parseIoThreads(reader, mapBuilder);
                            break;
                        }
                        case "stack-size": {
                            if (isSet(foundBits, 5)) throw reader.unexpectedElement();
                            foundBits = setBit(foundBits, 3);
                            parseStackSize(reader, mapBuilder);
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

    private static void parseDaemonThreads(final ConfigurationXMLStreamReader reader, final OptionMap.Builder mapBuilder) throws ConfigXMLParseException {
        requireSingleAttribute(reader, "value");
        final boolean daemon = reader.getBooleanAttributeValue(0);
        if (! reader.hasNext()) throw reader.unexpectedDocumentEnd();
        if (reader.nextTag() != END_ELEMENT) throw reader.unexpectedElement();
        mapBuilder.set(Options.THREAD_DAEMON, daemon);
        return;
    }

    private static void parseWorkerName(final ConfigurationXMLStreamReader reader, final OptionMap.Builder mapBuilder) throws ConfigXMLParseException {
        requireSingleAttribute(reader, "value");
        String name = reader.getAttributeValue(0);
        if (! reader.hasNext()) throw reader.unexpectedDocumentEnd();
        if (reader.nextTag() != END_ELEMENT) throw reader.unexpectedElement();
        mapBuilder.set(Options.WORKER_NAME, name);
        return;
    }

    private static void parsePoolSize(final ConfigurationXMLStreamReader reader, final OptionMap.Builder mapBuilder) throws ConfigXMLParseException {
        requireSingleAttribute(reader, "max-threads");
        int threadCount = reader.getIntAttributeValue(0);
        if (! reader.hasNext()) throw reader.unexpectedDocumentEnd();
        if (reader.nextTag() != END_ELEMENT) throw reader.unexpectedElement();
        mapBuilder.set(Options.WORKER_TASK_CORE_THREADS, threadCount);
        mapBuilder.set(Options.WORKER_TASK_MAX_THREADS, threadCount);
        return;
    }

    private static void parseTaskKeepalive(final ConfigurationXMLStreamReader reader, final OptionMap.Builder mapBuilder) throws ConfigXMLParseException {
        requireSingleAttribute(reader, "value");
        int duration = reader.getIntAttributeValue(0);
        if (! reader.hasNext()) throw reader.unexpectedDocumentEnd();
        if (reader.nextTag() != END_ELEMENT) throw reader.unexpectedElement();
        mapBuilder.set(Options.WORKER_TASK_KEEPALIVE, duration);
        return;
    }

    private static void parseIoThreads(final ConfigurationXMLStreamReader reader, final OptionMap.Builder mapBuilder) throws ConfigXMLParseException {
        requireSingleAttribute(reader, "value");
        int threadCount = reader.getIntAttributeValue(0);
        if (! reader.hasNext()) throw reader.unexpectedDocumentEnd();
        if (reader.nextTag() != END_ELEMENT) throw reader.unexpectedElement();
        mapBuilder.set(Options.WORKER_IO_THREADS, threadCount);
        return;
    }

    private static void parseStackSize(final ConfigurationXMLStreamReader reader, final OptionMap.Builder mapBuilder) throws ConfigXMLParseException {
        requireSingleAttribute(reader, "value");
        long stackSize = reader.getLongAttributeValue(0);
        if (! reader.hasNext()) throw reader.unexpectedDocumentEnd();
        if (reader.nextTag() != END_ELEMENT) throw reader.unexpectedElement();
        mapBuilder.set(Options.STACK_SIZE, stackSize);
        return;
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
