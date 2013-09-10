/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

package org.xnio.nio;

import static org.jboss.logging.Logger.Level.*;
import static org.jboss.logging.annotations.Transform.TransformType.*;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.RejectedExecutionException;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.Field;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.Transform;
import org.xnio.ClosedWorkerException;
import org.xnio.channels.ReadTimeoutException;
import org.xnio.channels.WriteTimeoutException;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MessageLogger(projectCode = "XNIO")
interface Log extends BasicLogger {
    Log log = Logger.getMessageLogger(Log.class, "org.xnio.nio");
    Log socketLog = Logger.getMessageLogger(Log.class, "org.xnio.nio.socket");
    Log selectorLog = Logger.getMessageLogger(Log.class, "org.xnio.nio.selector");
    Log tcpServerLog = Logger.getMessageLogger(Log.class, "org.xnio.nio.tcp.server");
    Log udpServerChannelLog = Logger.getMessageLogger(Log.class, "org.xnio.nio.udp.server.channel");

    // Greeting

    @LogMessage(level = INFO)
    @Message(value = "XNIO NIO Implementation Version %s")
    void greeting(String version);

    // Validation messages - cross-check with xnio-api

    @LogMessage(level = ERROR)
    @Message(id = 11, value = "Task %s failed with an exception")
    void taskFailed(Runnable command, @Cause Throwable cause);

    @Message(id = 15, value = "Parameter '%s' is out of range")
    IllegalArgumentException parameterOutOfRange(String name);

    @Message(id = 39, value = "Value for option '%s' is out of range")
    IllegalArgumentException optionOutOfRange(String name);

    // I/O errors - cross-check with xnio-api

    @Message(id = 800, value = "Read timed out")
    ReadTimeoutException readTimeout();

    @Message(id = 801, value = "Write timed out")
    WriteTimeoutException writeTimeout();

    @Message(id = 808, value = "I/O operation was interrupted")
    InterruptedIOException interruptedIO();

    InterruptedIOException interruptedIO(@Field int bytesTransferred);

    @Message(id = 815, value = "Worker is shut down")
    ClosedWorkerException workerShutDown();

    // Unsupported implementation operations - cross-check with xnio-api

    @Message(id = 900, value = "Method '%s' is not supported on this implementation")
    UnsupportedOperationException unsupported(String methodName);

    // General run-time messages - cross-check with xnio-api

    @Message(id = 1006, value = "Failed to invoke file watch callback")
    @LogMessage(level = ERROR)
    void failedToInvokeFileWatchCallback(@Cause Throwable cause);

    // Validation messages
    @Message(id = 7000, value = "No threads configured")
    IllegalArgumentException noThreads();

    @Message(id = 7001, value = "Balancing token count must be greater than zero and less than thread count")
    IllegalArgumentException balancingTokens();

    @Message(id = 7002, value = "Balancing connection count must be greater than zero when tokens are used")
    IllegalArgumentException balancingConnectionCount();

    @Message(id = 7003, value = "Buffer is too large")
    IllegalArgumentException bufferTooLarge();

    @Message(id = 7004, value = "No functional selector provider is available")
    IllegalStateException noSelectorProvider();

    @Message(id = 7005, value = "Unexpected exception opening a selector")
    IllegalStateException unexpectedSelectorOpenProblem(@Cause Throwable cause);

    @Message(id = 7006, value = "XNIO IO factory is from the wrong provider")
    IllegalArgumentException notNioProvider();

    @Message(id = 7007, value = "Thread is terminating")
    RejectedExecutionException threadExiting();

    // I/O messages

    @LogMessage(level = WARN)
    @Message(id = 8000, value = "Received an I/O error on selection: %s")
    void selectionError(IOException e);

    // Trace

    @LogMessage(level = TRACE)
    @Message(value = "Starting up with selector provider %s")
    void selectorProvider(@Transform(GET_CLASS) SelectorProvider provider);

    @LogMessage(level = TRACE)
    @Message(value = "Using %s for main selectors and %s for temp selectors")
    void selectors(Object mainSelectorCreator, Object tempSelectorCreator);
}
