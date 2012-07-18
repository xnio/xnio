/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008 Red Hat, Inc. and/or its affiliates.
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

package org.xnio.channels;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.io.IOException;
import java.io.Closeable;
import org.xnio.ChannelListener;

/**
 * A multicast-capable point-to-multipoint channel.  UDP channels are multipoint datagram channels which support multicast registration.
 *
 * @apiviz.landmark
 */
public interface MulticastMessageChannel extends BoundMultipointMessageChannel {

    /**
     * A registration key for a multicast group.
     *
     * @apiviz.exclude
     */
    interface Key extends Closeable {
        /**
         * Block multicast packets from the given source address.
         *
         * If this membership key is not source-specific, and the implementation supports source filtering,
         * then this method blocks multicast packets from the given source addresses. After a source address is blocked
         * it may still be possible to receive datagrams from that source. This can arise when datagrams are waiting
         * to be received in the socket's receive buffer.
         *
         * @param source the source address to block
         * @return this key
         * @throws IOException if an I/O error occurs
         * @throws UnsupportedOperationException if the implementation does not support source filtering
         * @throws IllegalStateException if this key is source-specific or is no longer valid
         * @throws IllegalArgumentException if the {@code source} parameter is not a unicast address or is not the same address type as the multicast group
         */
        Key block(InetAddress source) throws IOException, UnsupportedOperationException, IllegalStateException, IllegalArgumentException;

        /**
         * Unblock multicast packets from the given source address that was previously blocked using the {@link #block(InetAddress)} method.
         *
         * @param source the source address to unblock
         * @return this key
         * @throws IOException if an I/O error occurs
         * @throws IllegalStateException if the given source address is not currently blocked or the key is no longer valid
         * @throws UnsupportedOperationException if the implementation does not support source filtering
         */
        Key unblock(InetAddress source) throws IOException, IllegalStateException, UnsupportedOperationException;

        /**
         * Return the channel associated with this key.  This method will return the channel even after the membership
         * is dropped.
         *
         * @return the channel
         */
        MulticastMessageChannel getChannel();

        /**
         * Return the multicast group for which this key was created.  This method will continue to return the group even
         * after the membership is dropped.
         *
         * @return the multicast group
         */
        InetAddress getGroup();

        /**
         * Return the network interface for which this key was created.  This method will continue to return the network
         * interface even after the membership is dropped or the channel is closed.
         *
         * @return the network interface
         */
        NetworkInterface getNetworkInterface();

        /**
         * Return the source address if this membership key is source specific, or {@code null} if this membership is not
         * source specific.
         *
         * @return the source address
         */
        InetAddress getSourceAddress();

        /**
         * Determine if this membership is active.
         *
         * @return {@code true} if the membership is still active
         */
        boolean isOpen();
    }

    /**
     * Join a multicast group to begin receiving all datagrams sent to the group.
     * 
     * A multicast channel may join several multicast groups, including the same group on more than one interface.  An
     * implementation may impose a limit on the number of groups that may be joined at the same time.
     *
     * @param group the multicast address to join
     * @param iface the network interface to join on
     * @return a new key
     * @throws IOException if an I/O error occurs
     * @throws IllegalStateException if the channel is already a member of the group on this interface
     * @throws IllegalArgumentException if the {@code group} parameters is not a multicast address, or is an unsupported address type
     * @throws SecurityException if a security manager is set, and its {@link SecurityManager#checkMulticast(InetAddress)} method denies access to the group
     */
    Key join(InetAddress group, NetworkInterface iface) throws IOException;

    /**
     * Join a multicast group to begin receiving all datagrams sent to the group from a given source address.
     *
     * A multicast channel may join several multicast groups, including the same group on more than one interface.  An
     * implementation may impose a limit on the number of groups that may be joined at the same time.
     *
     * @param group the multicast address to join
     * @param iface the network interface to join on
     * @param source the source address to listen for
     * @return a new key
     * @throws IOException if an I/O error occurs
     * @throws IllegalStateException if the channel is already a member of the group on this interface
     * @throws IllegalArgumentException if the {@code group} parameters is not a multicast address, or is an unsupported address type
     * @throws SecurityException if a security manager is set, and its {@link SecurityManager#checkMulticast(InetAddress)} method denies access to the group
     * @throws UnsupportedOperationException if the implementation does not support source filtering
     */
    Key join(InetAddress group, NetworkInterface iface, InetAddress source) throws IOException;

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends MulticastMessageChannel> getReadSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends MulticastMessageChannel> getCloseSetter();

    /** {@inheritDoc} */
    ChannelListener.Setter<? extends MulticastMessageChannel> getWriteSetter();
}
