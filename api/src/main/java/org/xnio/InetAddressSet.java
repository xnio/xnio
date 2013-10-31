package org.xnio;

import java.io.Serializable;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.TreeSet;

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
 * Set for IP addresses.
 * 
 * @author Eduardo Sant'Ana da Silva
 * 
 */
public class InetAddressSet implements Serializable {
    private static final long serialVersionUID = -1532961420437645552L;

    /**
     * The backing storage
     */
    private TreeSet<InetAddressEntry> set = new TreeSet<InetAddressEntry>();

    public void add(InetAddress address) {
        this.set.add(new InetAddressEntry(address));
    }

    /**
     * Check if the Set contains the exact address
     * 
     * @param address
     * @return true if the address is in the set, otherwise false
     */
    public boolean contains(InetAddress address) {
        InetAddressEntry entry = new InetAddressEntry(address);
        return set.contains(entry);
    }

    /**
     * Check if the Set contains the exact address
     * 
     * @param address
     * @param mask
     * @return true if the address is in the set, otherwise false
     */
    public boolean contains(InetAddress address, byte mask) {
        InetAddressEntry entry = new InetAddressEntry(address, mask);
        return set.contains(entry);
    }

    /**
     * Matches the address according to the Returns the IP address that has the
     * longest match. (Classless Inter-Domain Routing (CIDR))
     * 
     * @param address
     * @return
     */
    public InetAddress getFirstMatch(InetAddress address) {
        if (contains(address)) {
            return address;
        } else {
            InetAddressEntry entry = new InetAddressEntry(address);
            InetAddressEntry lower = set.lower(entry);
            InetAddressEntry higher = set.higher(entry);
            if (lower != null && lower.equals(entry)) {
                return lower.getInetAddress();
            } else {
                if (lower == null && higher != null) {
                    return higher.getInetAddress();
                } else if (lower != null && higher == null) {
                    return lower.getInetAddress();
                } else {
                    // IPv4
                    if (address.getAddress().length == 4) {
                        int iAddress = toInt(address.getAddress());
                        int iLower = toInt(lower.getInetAddress().getAddress());
                        int iHigher = toInt(higher.getInetAddress()
                                .getAddress());
                        if ((iLower ^ iAddress) < (iHigher ^ iAddress)) {
                            return lower.getInetAddress();
                        } else {
                            return higher.getInetAddress();
                        }

                    } else
                    // IPv6
                    if (address.getAddress().length == 16) {
                        BigInteger iAddress = new BigInteger(
                                address.getAddress());
                        BigInteger iLower = new BigInteger(lower
                                .getInetAddress().getAddress());
                        BigInteger iHigher = new BigInteger(higher
                                .getInetAddress().getAddress());
                        if (iLower.xor(iAddress).compareTo(
                                iHigher.xor(iAddress)) < 0) {
                            return lower.getInetAddress();
                        } else {
                            return higher.getInetAddress();
                        }
                    } else {
                        return null;
                    }
                }
            }
        }
    }

    /**
     * Remove the address from the set
     * 
     * @param address
     * @return true if the address was found and removed false otherwise.
     */
    public boolean remove(InetAddress address) {
        InetAddressEntry entry = new InetAddressEntry(address);
        return this.set.remove(entry);
    }

    /**
     * Remove the address from the set
     * 
     * @param address
     * @param mask
     * @return true if the address was found and removed false otherwise.
     */
    public boolean remove(InetAddress address, byte mask) {
        InetAddressEntry entry = new InetAddressEntry(address, mask);
        return this.set.remove(entry);
    }

    /**
     * Size of the set
     * 
     * @return the set's size
     */
    public int size() {
        return set.size();
    }

    private static final int toInt(byte[] array) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(array);
        int num = byteBuffer.getInt();
        return num;
    }
}
