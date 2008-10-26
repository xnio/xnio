/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.xnio.management;

import java.net.DatagramSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class AttributeResolver {

    private static final String QUOTE = "\"";

    public static List<NameValuePair> getAdditionalAttributes(final Object[] objects){
        List<NameValuePair> attributes = new ArrayList<NameValuePair>();
        for (Object object : objects) {
            attributes.addAll(getAdditionalAttributes(object));
        }
        return attributes;
    }

    private static List<NameValuePair> getAdditionalAttributes(final Object object) {

        List<NameValuePair> attributes = new ArrayList<NameValuePair>();

        if (object instanceof Socket) {
            Socket s = (Socket) object;
            attributes.add(new NameValuePair("RemoteAddress", quoted(s.getInetAddress().getHostAddress() + ":" + s.getPort())));
            attributes.add(new NameValuePair("LocalAddress", quoted(s.getLocalAddress().getHostAddress() + ":" + s.getLocalPort())));
            return attributes;
        } else  if (object instanceof DatagramSocket) {
            DatagramSocket s = (DatagramSocket) object;
            attributes.add(new NameValuePair("LocalAddress", quoted(s.getLocalAddress().getHostAddress() + ":" + s.getLocalPort())));
            return attributes;
        }

        attributes.add(new NameValuePair("Instance", object.toString()));
        return attributes;
    }

    private static String quoted(final String unquotedString) {
        return QUOTE + unquotedString + QUOTE;
    }
}
