
/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2014 Red Hat, Inc. and/or its affiliates.
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

package org.xnio.nio;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * The version class.
 *
 * @apiviz.exclude
 */
public final class Version {
    private Version() {}

    /**
     * Print out the current XNIO version on {@code System.out}.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        System.out.print(VERSION_STRING);
    }


    private static final String JAR_NAME;
    private static final String VERSION_STRING;

    static {
        final Enumeration<URL> resources;
        String jarName = "(unknown)";
        String versionString = "(unknown)";
        try {
            final ClassLoader classLoader = Version.class.getClassLoader();
            resources = classLoader == null ? ClassLoader.getSystemResources("META-INF/MANIFEST.MF") : classLoader.getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                final URL url = resources.nextElement();
                try {
                    final InputStream stream = url.openStream();
                    if (stream != null) try {
                        final Manifest manifest = new Manifest(stream);
                        final Attributes mainAttributes = manifest.getMainAttributes();
                        if (mainAttributes != null && "XNIO NIO Implementation".equals(mainAttributes.getValue("Specification-Title"))) {
                            jarName = mainAttributes.getValue("Jar-Name");
                            versionString = mainAttributes.getValue("Jar-Version");
                        }
                    } finally {
                        try {
                            stream.close();
                        } catch (Throwable ignored) {
                        }
                    }
                } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {}
        JAR_NAME = jarName;
        VERSION_STRING = versionString;
    }

    /**
     * Get the name of the JBoss Modules JAR.
     *
     * @return the name
     */
    public static String getJarName() {
        return JAR_NAME;
    }

    /**
     * Get the version string of JBoss Modules.
     *
     * @return the version string
     */
    public static String getVersionString() {
        return VERSION_STRING;
    }
}
