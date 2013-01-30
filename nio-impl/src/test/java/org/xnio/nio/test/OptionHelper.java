/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
package org.xnio.nio.test;

import java.util.HashSet;

import org.xnio.Option;
import org.xnio.Options;

/**
 * Utility class.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public class OptionHelper {

    @SuppressWarnings("deprecation")
    private static Option<?>[] options = {Options.ALLOW_BLOCKING, Options.BACKLOG, Options.BROADCAST,
        Options.CLOSE_ABORT, Options.CONNECTION_HIGH_WATER, Options.CONNECTION_LOW_WATER, Options.CORK,
        Options.FILE_ACCESS, Options.IP_TRAFFIC_CLASS, Options.KEEP_ALIVE, Options.MAX_INBOUND_MESSAGE_SIZE,
        Options.MAX_OUTBOUND_MESSAGE_SIZE, Options.MULTICAST, Options.MULTICAST_TTL, Options.READ_TIMEOUT,
        Options.RECEIVE_BUFFER, Options.REUSE_ADDRESSES, Options.SASL_DISALLOWED_MECHANISMS, Options.SASL_MECHANISMS,
        Options.SASL_POLICY_FORWARD_SECRECY, Options.SASL_POLICY_NOACTIVE, Options.SASL_POLICY_NOANONYMOUS,
        Options.SASL_POLICY_NODICTIONARY, Options.SASL_POLICY_NOPLAINTEXT, Options.SASL_POLICY_PASS_CREDENTIALS,
        Options.SASL_PROPERTIES, Options.SASL_QOP, Options.SASL_REUSE, Options.SASL_SERVER_AUTH, Options.SASL_STRENGTH,
        Options.SECURE, Options.SEND_BUFFER, Options.SSL_APPLICATION_BUFFER_REGION_SIZE,
        Options.SSL_APPLICATION_BUFFER_SIZE, Options.SSL_CLIENT_AUTH_MODE, Options.SSL_CLIENT_SESSION_CACHE_SIZE,
        Options.SSL_CLIENT_SESSION_TIMEOUT, Options.SSL_ENABLE_SESSION_CREATION, Options.SSL_ENABLED,
        Options.SSL_ENABLED_CIPHER_SUITES, Options.SSL_ENABLED_PROTOCOLS, Options.SSL_JSSE_KEY_MANAGER_CLASSES,
        Options.SSL_JSSE_TRUST_MANAGER_CLASSES, Options.SSL_PACKET_BUFFER_REGION_SIZE, Options.SSL_PACKET_BUFFER_SIZE,
        Options.SSL_PEER_HOST_NAME, Options.SSL_PEER_PORT, Options.SSL_PROTOCOL, Options.SSL_PROVIDER,
        Options.SSL_RNG_OPTIONS, Options.SSL_SERVER_SESSION_CACHE_SIZE, Options.SSL_SERVER_SESSION_TIMEOUT,
        Options.SSL_STARTTLS, Options.SSL_SUPPORTED_CIPHER_SUITES, Options.SSL_SUPPORTED_PROTOCOLS,
        Options.SSL_USE_CLIENT_MODE, Options.STACK_SIZE, Options.TCP_OOB_INLINE, Options.TCP_NODELAY,
        Options.THREAD_DAEMON, Options.THREAD_PRIORITY, Options.USE_DIRECT_BUFFERS, Options.WRITE_TIMEOUT,
        Options.WORKER_NAME, Options.WORKER_ACCEPT_THREADS, Options.WORKER_READ_THREADS,
        Options.WORKER_ESTABLISH_WRITING, Options.WORKER_TASK_CORE_THREADS, Options.WORKER_TASK_KEEPALIVE,
        Options.WORKER_TASK_LIMIT, Options.WORKER_TASK_MAX_THREADS, Options.WORKER_WRITE_THREADS};

    public static Option<?>[] getNotSupportedOptions(Option<?> ... supportedOptions) {
        final HashSet<Option<?>> temp = new HashSet<Option<?>>();
        for (Option<?> option: options) {
            temp.add(option);
        }
        for (Option<?> supportedOption: supportedOptions) {
            temp.remove(supportedOption);
        }
        return temp.toArray(new Option<?>[0]);
    }
}
