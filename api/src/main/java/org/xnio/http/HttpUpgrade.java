/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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

package org.xnio.http;

import static org.xnio._private.Messages.msg;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Pooled;
import org.xnio.StreamConnection;
import org.xnio.XnioWorker;
import org.xnio.channels.BoundChannel;
import org.xnio.ssl.SslConnection;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.PushBackStreamSourceConduit;
import org.xnio.conduits.StreamSourceConduit;
import org.xnio.ssl.XnioSsl;

/**
 * Simple HTTP client that can perform a HTTP upgrade. This is not a general purpose HTTP
 * client, all it can do is upgrade a HTTP
 *
 * @author Stuart Douglas
 */
public class HttpUpgrade {

    /**
     * Perform a HTTP upgrade that results in a SSL secured connection. This method should be used if the target endpoint is using https
     *
     * @param worker           The worker
     * @param ssl              The XnioSsl instance
     * @param bindAddress      The bind address
     * @param uri              The URI to connect to
     * @param headers          Any additional headers to include in the upgrade request. This must include an <code>Upgrade</code> header that specifies the type of upgrade being performed
     * @param openListener     The open listener that is invoked once the HTTP upgrade is done
     * @param bindListener     The bind listener that is invoked when the socket is bound
     * @param optionMap        The option map for the connection
     * @param handshakeChecker A handshake checker that can be supplied to verify that the server returned a valid response to the upgrade request
     * @return An IoFuture of the connection
     */
    public static IoFuture<SslConnection> performUpgrade(final XnioWorker worker, XnioSsl ssl, InetSocketAddress bindAddress, URI uri, final Map<String, String> headers, ChannelListener<? super SslConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap, HandshakeChecker handshakeChecker) {
        return new HttpUpgradeState<SslConnection>(worker, ssl, bindAddress, uri, headers, openListener, bindListener, optionMap, handshakeChecker).doUpgrade();
    }


    /**
     * Connects to the target server using HTTP upgrade.
     *
     * @param worker           The worker
     * @param bindAddress      The bind address
     * @param uri              The URI to connect to
     * @param headers          Any additional headers to include in the upgrade request. This must include an <code>Upgrade</code> header that specifies the type of upgrade being performed
     * @param openListener     The open listener that is invoked once the HTTP upgrade is done
     * @param bindListener     The bind listener that is invoked when the socket is bound
     * @param optionMap        The option map for the connection
     * @param handshakeChecker A handshake checker that can be supplied to verify that the server returned a valid response to the upgrade request
     * @return An IoFuture of the connection
     */
    public static IoFuture<StreamConnection> performUpgrade(final XnioWorker worker, InetSocketAddress bindAddress, URI uri, final Map<String, String> headers, ChannelListener<? super StreamConnection> openListener, ChannelListener<? super BoundChannel> bindListener, OptionMap optionMap, HandshakeChecker handshakeChecker) {
        return new HttpUpgradeState<StreamConnection>(worker, null, bindAddress, uri, headers, openListener, bindListener, optionMap, handshakeChecker).doUpgrade();
    }


    /**
     * Performs a HTTP upgrade on an existing connection.
     *
     * @param connection       The existing connection to upgrade
     * @param uri              The URI to connect to
     * @param headers          Any additional headers to include in the upgrade request. This must include an <code>Upgrade</code> header that specifies the type of upgrade being performed
     * @param openListener     The open listener that is invoked once the HTTP upgrade is done
     * @param handshakeChecker A handshake checker that can be supplied to verify that the server returned a valid response to the upgrade request
     * @return An IoFuture of the connection
     */
    public static <T extends StreamConnection> IoFuture<T> performUpgrade(final T connection, URI uri, final Map<String, String> headers, ChannelListener<? super StreamConnection> openListener, HandshakeChecker handshakeChecker) {
        return new HttpUpgradeState<T>(connection, uri, headers, openListener, handshakeChecker).upgradeExistingConnection();
    }

    private HttpUpgrade() {

    }

    private static class HttpUpgradeState<T extends StreamConnection> {


        private final XnioWorker worker;
        private final XnioSsl ssl;
        private final InetSocketAddress bindAddress;
        private final URI uri;
        private final Map<String, String> headers;
        private final ChannelListener<? super T> openListener;
        private final ChannelListener<? super BoundChannel> bindListener;
        private final OptionMap optionMap;
        private final HandshakeChecker handshakeChecker;
        private final FutureResult<T> future = new FutureResult<T>();
        private T connection;


        private HttpUpgradeState(final XnioWorker worker, final XnioSsl ssl, final InetSocketAddress bindAddress, final URI uri, final Map<String, String> headers, final ChannelListener<? super T> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap, final HandshakeChecker handshakeChecker) {
            this.worker = worker;
            this.ssl = ssl;
            this.bindAddress = bindAddress;
            this.uri = uri;
            this.headers = headers;
            this.openListener = openListener;
            this.bindListener = bindListener;
            this.optionMap = optionMap;
            this.handshakeChecker = handshakeChecker;
        }

        public HttpUpgradeState(final T connection, final URI uri, final Map<String, String> headers, final ChannelListener<? super StreamConnection> openListener, final HandshakeChecker handshakeChecker) {
            this.worker = connection.getWorker();
            this.ssl = null;
            this.bindAddress = null;
            this.uri = uri;
            this.headers = headers;
            this.openListener = openListener;
            this.bindListener = null;
            this.optionMap = OptionMap.EMPTY;
            this.handshakeChecker = handshakeChecker;
            this.connection = connection;
        }

        private IoFuture<T> doUpgrade() {
            InetSocketAddress address = new InetSocketAddress(uri.getHost(), uri.getPort());

            final ChannelListener<StreamConnection> connectListener = new ConnectionOpenListener();
            final String scheme = uri.getScheme();
            if (scheme.equals("http")) {
                if (bindAddress == null) {
                    worker.openStreamConnection(address, connectListener, bindListener, optionMap);
                } else {
                    worker.openStreamConnection(bindAddress, address, connectListener, bindListener, optionMap);
                }
            } else if (scheme.equals("https")) {
                if (ssl == null) {
                    throw msg.missingSslProvider();
                }
                if (bindAddress == null) {
                    ssl.openSslConnection(worker, address, connectListener, bindListener, optionMap);
                } else {
                    ssl.openSslConnection(worker, bindAddress, address, connectListener, bindListener, optionMap);
                }
            } else {
                throw msg.invalidURLScheme(scheme);
            }
            return future.getIoFuture();
        }

        private String buildHttpRequest() {

            final StringBuilder builder = new StringBuilder();
            builder.append("GET ");
            builder.append(uri.getPath().isEmpty() ? "/" : uri.getPath());
            builder.append(" HTTP/1.1\r\n");
            final Set<String> seen = new HashSet<String>();
            for (Map.Entry<String, String> header : headers.entrySet()) {
                builder.append(header.getKey());
                builder.append(": ");
                builder.append(header.getValue());
                builder.append("\r\n");
                seen.add(header.getKey().toLowerCase(Locale.ENGLISH));
            }
            if (!seen.contains("host")) {
                builder.append("Host: ");
                builder.append(getHost());
                builder.append("\r\n");
            }
            if (!seen.contains("connection")) {
                builder.append("Connection: upgrade\r\n");
            }
            if (!seen.contains("upgrade")) {
                throw new IllegalArgumentException("Upgrade: header was not supplied in header arguments");
            }
            builder.append("\r\n");
            return builder.toString();
        }

        private String getHost() {
            String scheme = uri.getScheme();
            int port = uri.getPort();

            if (port < 0 || "http".equals(scheme) && port == 80 || "https".equals(scheme) && port == 443) {
                // No port or default port.
                return uri.getHost();
            }

            return uri.getHost() + ":" + port;
        }

        public IoFuture<T> upgradeExistingConnection() {
            final ChannelListener<StreamConnection> connectListener = new ConnectionOpenListener();
            connectListener.handleEvent(connection);
            return future.getIoFuture();
        }


        private class ConnectionOpenListener implements ChannelListener<StreamConnection> {
            @Override
            public void handleEvent(final StreamConnection channel) {
                connection = (T) channel;
                final ByteBuffer buffer = ByteBuffer.wrap(buildHttpRequest().getBytes());
                int r;
                do {
                    try {
                        r = channel.getSinkChannel().write(buffer);
                        if (r == 0) {
                            channel.getSinkChannel().getWriteSetter().set(new StringWriteListener(buffer));
                            channel.getSinkChannel().resumeWrites();
                            return;
                        }
                    } catch (IOException e) {
                        IoUtils.safeClose(channel);
                        future.setException(e);
                        return;
                    }
                } while (buffer.hasRemaining());
                new UpgradeResultListener().handleEvent(connection.getSourceChannel());
            }
        }

        private final class StringWriteListener implements ChannelListener<StreamSinkChannel> {

            final ByteBuffer buffer;

            private StringWriteListener(final ByteBuffer buffer) {
                this.buffer = buffer;
            }

            @Override
            public void handleEvent(final StreamSinkChannel channel) {
                int r;
                do {
                    try {
                        r = channel.write(buffer);
                        if (r == 0) {
                            channel.getWriteSetter().set(new StringWriteListener(buffer));
                            channel.resumeWrites();
                            return;
                        }
                    } catch (IOException e) {
                        IoUtils.safeClose(channel);
                        future.setException(e);
                        return;
                    }
                } while (buffer.hasRemaining());
                channel.suspendWrites();
                new UpgradeResultListener().handleEvent(connection.getSourceChannel());
            }
        }

        private final class UpgradeResultListener implements ChannelListener<StreamSourceChannel> {

            private final HttpUpgradeParser parser = new HttpUpgradeParser();
            private ByteBuffer buffer = ByteBuffer.allocate(1024);

            @Override
            public void handleEvent(final StreamSourceChannel channel) {
                int r;
                do {
                    try {
                        buffer.compact();
                        r = channel.read(buffer);
                        if (r == 0) {
                            channel.getReadSetter().set(this);
                            channel.resumeReads();
                            return;
                        } else if (r == -1) {
                            throw msg.connectionClosedEarly();
                        }
                        buffer.flip();
                        parser.parse(buffer);
                    } catch (IOException e) {
                        IoUtils.safeClose(channel);
                        future.setException(e);
                        return;
                    }

                } while (!parser.isComplete());
                channel.suspendReads();

                if (buffer.hasRemaining()) {
                    StreamSourceConduit orig = connection.getSourceChannel().getConduit();
                    PushBackStreamSourceConduit pushBack = new PushBackStreamSourceConduit(orig);
                    pushBack.pushBack(new Pooled<ByteBuffer>() {
                        @Override
                        public void discard() {
                            buffer = null;
                        }

                        @Override
                        public void free() {
                            buffer = null;
                        }

                        @Override
                        public ByteBuffer getResource() throws IllegalStateException {
                            return buffer;
                        }
                    });
                    connection.getSourceChannel().setConduit(pushBack);
                }

                //ok, we have a response
                if (parser.getResponseCode() == 101) { // Switching Protocols
                    handleUpgrade(parser, channel);
                } else if (parser.getResponseCode() == 301 || // Moved Permanently
                        parser.getResponseCode() == 302 || // Found
                        parser.getResponseCode() == 303 || // See Other
                        parser.getResponseCode() == 307 || // Temporary Redirect
                        parser.getResponseCode() == 308) { // Permanent Redirect
                    handleRedirect(parser, channel);
                } else {
                    IoUtils.safeClose(channel);
                    future.setException(new IOException("Invalid response code " + parser.getResponseCode()));
                }
            }

        }

        private void handleUpgrade(final HttpUpgradeParser parser, final StreamSourceChannel channel) {
            final String contentLength = parser.getHeaders().get("content-length");
            if (!"0".equals(contentLength)) {
                IoUtils.safeClose(channel);
                future.setException(new IOException("For now upgrade responses must have a content length of zero."));
                return;
            }
            if (handshakeChecker != null) {
                try {
                    handshakeChecker.checkHandshake(parser.getHeaders());
                } catch (IOException e) {
                    IoUtils.safeClose(channel);
                    future.setException(e);
                    return;
                }
            }
            future.setResult(connection);
            ChannelListeners.invokeChannelListener(connection, openListener);
        }

        private void handleRedirect(final HttpUpgradeParser parser, final StreamSourceChannel channel) {
            IoUtils.safeClose(channel);
            future.setException(msg.redirect(parser.getResponseCode(), parser.getHeaders().get("location")));
        }

    }
}
