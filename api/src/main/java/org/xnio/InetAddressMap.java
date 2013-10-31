package org.xnio;

import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.NavigableMap;
import java.util.TreeMap;

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
public class InetAddressMap<K extends InetAddress, V> {

    /**
     * The backing map.
     */
    private final transient NavigableMap<InetAddressEntry, Object> map;

    /**
     * Constructor
     */
    public InetAddressMap() {
        this.map = new TreeMap<InetAddressEntry, Object>();
    }

    /**
     * Put the entry in the map, the key a InetAddress wrapped 
     * in a InetAddressEntry
     * 
     * @param address
     * @param value
     */
    public void put(K address, V value) {
        this.map.put(new InetAddressEntry(address), value);
    }

    /**
     * Return true if the map contains the address
     * @param address
     * @return
     */
    public boolean contains(K address) {
        InetAddressEntry entry = new InetAddressEntry(address);
        V p = (V) this.map.get(entry);
        return p != null;
    }

    /**
     * Return the address if it is contained in the map
     * @param address
     * @return
     */
    public V get(K address) {
        InetAddressEntry entry = new InetAddressEntry(address);
        V p = (V) this.map.get(entry);
        return p;
    }

    /**
     * Matches the address according to the Returns the IP address that has the
     * longest match. (Classless Inter-Domain Routing (CIDR))
     * 
     * @param address
     * @return
     */
    public K getFirstMatchKey(K address) {
        if (contains(address)) {
            InetAddressEntry entry = new InetAddressEntry(address);
            return (K) entry.getInetAddress();
        } else {
            InetAddressEntry entry = new InetAddressEntry(address);
            InetAddressEntry lower = map.lowerKey(entry);
            InetAddressEntry higher = map.higherKey(entry);
            if (lower != null && lower.equals(entry)) {
                return (K) lower.getInetAddress();
            } else {
                if (lower == null && higher != null) {
                    return (K) higher.getInetAddress();
                } else if (lower != null && higher == null) {
                    return (K) lower.getInetAddress();
                } else {
                    // IPv4
                    if (address.getAddress().length == 4) {
                        int iAddress = toInt(address.getAddress());
                        int iLower = toInt(lower.getInetAddress().getAddress());
                        int iHigher = toInt(higher.getInetAddress()
                                .getAddress());
                        if ((iLower ^ iAddress) < (iHigher ^ iAddress)) {
                            return (K) lower.getInetAddress();
                        } else {
                            return (K) higher.getInetAddress();
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
                            return (K) lower.getInetAddress();
                        } else {
                            return (K) higher.getInetAddress();
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
    public V remove(K address) {
        InetAddressEntry entry = new InetAddressEntry(address);
        return (V) this.map.remove(entry);
    }

    /**
     * Remove the address from the set
     * 
     * @param address
     * @param mask
     * @return true if the address was found and removed false otherwise.
     */
    public V remove(InetAddress address, byte mask) {
        InetAddressEntry entry = new InetAddressEntry(address, mask);
        return (V) this.map.remove(entry);
    }

    /**
     * Size of the map
     * 
     * @return the map's size
     */
    public int size() {
        return map.size();
    }

    private static final int toInt(byte[] array) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(array);
        int num = byteBuffer.getInt();
        return num;
    }
}
