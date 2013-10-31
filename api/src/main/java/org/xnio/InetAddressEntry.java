package org.xnio;

import java.math.BigInteger;
import java.net.InetAddress;

/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates, and individual
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

/**
 * Entry containing InetAddress and Mask.
 * 
 * @author Eduardo Sant'Ana da Silva
 * 
 */
public final class InetAddressEntry implements Comparable<InetAddressEntry> {
    /* Used to store the inet address provider */
    private final InetAddress address;
    
    /* Used to store the mask */
    private final byte mask;
    
    /**
     * Constructor using InetAddress
     * @param inet address
     */
    public InetAddressEntry(InetAddress address) {
        this.address = address;
        this.mask = -1;
    }

    /**
     * Constructor using InetAddress and Mask
     * @param address
     * @param mask
     */
    public InetAddressEntry(InetAddress address, byte mask) {
        this.address = address;
        this.mask = mask;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        if (address.getAddress().length == 4) {
            return toInt(address.getAddress());
        } else {
            BigInteger bAddress = new BigInteger(address.getAddress());
            return bAddress.intValue();
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public boolean equals(Object comp) {
        if (comp instanceof InetAddressEntry) {
            InetAddressEntry entry = (InetAddressEntry) comp;
            if (entry.getInetAddress().equals(this.address)) {
                return entry.getMask() == this.mask;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int compareTo(InetAddressEntry addressEntry) {
        return Integer.signum(toInt(address.getAddress())
                - toInt(addressEntry.address.getAddress()));
    }

    /**
     * Gets the InetAddress
     * @return the InetAddress stored by the entry
     */
    protected InetAddress getInetAddress() {
        return address;
    }

    /**
     * Gets the mask
     * @return the mask stored by the entry
     */
    protected byte getMask() {
        return mask;
    }

    /* convert byte array to int */
    private static final int toInt(byte[] array) {
        return (((array[0]) << 24) | ((array[1] & 0xff) << 16)
                | ((array[2] & 0xff) << 8) | ((array[3] & 0xff)));
    }
}
