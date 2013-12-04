package org.xnio;

import java.io.IOException;
import java.net.SocketAddress;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.StreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * An XNIO I/O factory which can be used to create channels.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface XnioIoFactory {

    /**
     * Connect to a remote stream server.  The protocol family is determined by the type of the socket address given.
     * If an open listener is used, the channel should not be accessed via the returned
     * {@code IoFuture}, and vice-versa.
     *
     * @param destination the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    IoFuture<StreamConnection> openStreamConnection(SocketAddress destination, ChannelListener<? super StreamConnection> openListener, OptionMap optionMap);

    /**
     * Connect to a remote stream server.  The protocol family is determined by the type of the socket address given.
     * If an open listener is used, the channel should not be accessed via the returned
     * {@code IoFuture}, and vice-versa.
     *
     * @param destination the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    IoFuture<StreamConnection> openStreamConnection(SocketAddress destination, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap);

    /**
     * Connect to a remote stream server.  The protocol family is determined by the type of the socket addresses given
     * (which must match).  If an open listener is used, the channel should not be accessed via the returned
     * {@code IoFuture}, and vice-versa.
     *
     * @param bindAddress the local address to bind to
     * @param destination the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    IoFuture<StreamConnection> openStreamConnection(SocketAddress bindAddress, SocketAddress destination, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap);

    /**
     * Accept a stream connection at a destination address.  If a wildcard address is specified, then a destination address
     * is chosen in a manner specific to the OS and/or channel type.
     *
     * @param destination the destination (bind) address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the acceptor is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future connection
     */
    IoFuture<StreamConnection> acceptStreamConnection(SocketAddress destination, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap);

    /**
     * Connect to a remote message server.  The protocol family is determined by the type of the socket address given.
     * If an open listener is used, the channel should not be accessed via the returned
     * {@code IoFuture}, and vice-versa.
     *
     * @param destination the destination address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param optionMap the option map
     * @return the future result of this operation
     */
    IoFuture<MessageConnection> openMessageConnection(SocketAddress destination, ChannelListener<? super MessageConnection> openListener, OptionMap optionMap);

    /**
     * Accept a message connection at a destination address.  If a wildcard address is specified, then a destination address
     * is chosen in a manner specific to the OS and/or channel type.  If an open listener is used, the channel should
     * not be accessed via the returned {@code IoFuture}, and vice-versa.
     *
     * @param destination the destination (bind) address
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the acceptor is bound, or {@code null} for none
     * @param optionMap the option map
     * @return the future connection
     */
    IoFuture<MessageConnection> acceptMessageConnection(SocketAddress destination, ChannelListener<? super MessageConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap);

    /**
     * Create a two-way stream pipe.
     *
     * @return the created pipe
     * @throws java.io.IOException if the pipe could not be created
     */
    ChannelPipe<StreamChannel, StreamChannel> createFullDuplexPipe() throws IOException;

    /**
     * Create a two-way stream pipe.
     *
     * @return the created pipe
     * @throws java.io.IOException if the pipe could not be created
     */
    ChannelPipe<StreamConnection, StreamConnection> createFullDuplexPipeConnection() throws IOException;

    /**
     * Create a one-way stream pipe.
     *
     * @return the created pipe
     * @throws java.io.IOException if the pipe could not be created
     */
    ChannelPipe<StreamSourceChannel, StreamSinkChannel> createHalfDuplexPipe() throws IOException;

    /**
     * Create a two-way stream pipe.  The left side will be associated with this factory, and the right
     * side will be associated with the given peer.
     *
     * @param peer the peer to use for controlling the remote (right) side
     * @return the created pipe
     * @throws java.io.IOException if the pipe could not be created
     */
    ChannelPipe<StreamConnection, StreamConnection> createFullDuplexPipeConnection(XnioIoFactory peer) throws IOException;

    /**
     * Create a one-way stream pipe.  The left (source) side will be associated with this factory, and the right
     * (sink) side will be associated with the given peer.
     *
     * @param peer the peer to use for the sink (right) side
     * @return the created pipe
     * @throws java.io.IOException if the pipe could not be created
     */
    ChannelPipe<StreamSourceChannel, StreamSinkChannel> createHalfDuplexPipe(XnioIoFactory peer) throws IOException;
}
