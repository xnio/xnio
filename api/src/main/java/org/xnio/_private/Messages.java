/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
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

package org.xnio._private;

import java.io.CharConversionException;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.Channel;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLEngineResult;
import javax.security.sasl.SaslException;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.Field;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.xnio.IoFuture;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConcurrentStreamChannelAccessException;
import org.xnio.channels.ConnectedChannel;
import org.xnio.channels.FixedLengthOverflowException;
import org.xnio.channels.FixedLengthUnderflowException;
import org.xnio.channels.ReadTimeoutException;
import org.xnio.channels.WriteTimeoutException;

import static org.jboss.logging.Logger.Level.*;

@MessageLogger(projectCode = "XNIO")
public interface Messages extends BasicLogger {
    Messages msg = Logger.getMessageLogger(Messages.class, "org.xnio");
    Messages futureMsg = Logger.getMessageLogger(Messages.class, "org.xnio.future");
    Messages optionParseMsg = Logger.getMessageLogger(Messages.class, "org.xnio.option.parse");
    Messages closeMsg = Logger.getMessageLogger(Messages.class, "org.xnio.safe-close");
    Messages listenerMsg = Logger.getMessageLogger(Messages.class, "org.xnio.listener");

    // Greeting

    @Message(value = "XNIO version %s")
    @LogMessage(level = INFO)
    void greeting(String version);

    // Validation messages - cross-check with xnio-nio

    @Message(id = 0, value = "Method parameter '%s' cannot be null")
    IllegalArgumentException nullParameter(String name);

    @Message(id = 1, value = "Method parameter '%s' must be greater than %d")
    IllegalArgumentException minRange(String paramName, int minimumValue);

    @Message(id = 2, value = "Unsupported socket address %s")
    IllegalArgumentException badSockType(Class<? extends SocketAddress> type);

    @Message(id = 3, value = "Method parameter '%s' contains disallowed null value at index %d")
    IllegalArgumentException nullArrayIndex(String paramName, int index);

    @Message(id = 4, value = "Bind address %s is not the same type as destination address %s")
    IllegalArgumentException mismatchSockType(Class<? extends SocketAddress> bindType, Class<? extends SocketAddress> destType);

    @Message(id = 5, value = "No such field named \"%s\" in %s")
    IllegalArgumentException noField(String fieldName, Class<?> clazz);

    @Message(id = 6, value = "Class \"%s\" not found in %s")
    IllegalArgumentException optionClassNotFound(String className, ClassLoader classLoader);

    @Message(id = 7, value = "Field named \"%s\" is not accessible (public) in %s")
    IllegalArgumentException fieldNotAccessible(String fieldName, Class<?> clazz);

    @Message(id = 8, value = "Field named \"%s\" is not static in %s")
    IllegalArgumentException fieldNotStatic(String fieldName, Class<?> clazz);

    @Message(id = 9, value = "Copy with negative count is not supported")
    UnsupportedOperationException copyNegative();

    @Message(id = 10, value = "Invalid option '%s' in property '%s': %s")
    @LogMessage(level = WARN)
    void invalidOptionInProperty(String optionName, String name, /* ! @Cause */ Throwable problem);

    // id = 11 - task failed to execute

    @Message(id = 12, value = "Attempt to write to a read-only buffer")
    ReadOnlyBufferException readOnlyBuffer();

    @Message(id = 13, value = "Buffer underflow")
    BufferUnderflowException bufferUnderflow();

    @Message(id = 14, value = "Buffer overflow")
    BufferOverflowException bufferOverflow();

    // all param range checks should have ID 15

    @Message(id = 15, value = "Parameter '%s' is out of range")
    IllegalArgumentException parameterOutOfRange(String name);

    // end range checks

    @Message(id = 16, value = "Mixed direct and heap buffers not allowed")
    IllegalArgumentException mixedDirectAndHeap();

    @Message(id = 17, value = "Buffer was already freed")
    IllegalStateException bufferFreed();

    @Message(id = 18, value = "Access a thread-local random from the wrong thread")
    IllegalStateException randomWrongThread();

    @Message(id = 19, value = "Channel not available from connection")
    IllegalStateException channelNotAvailable();

    @Message(id = 20, value = "No parser for this option value type")
    IllegalArgumentException noOptionParser();

    @Message(id = 21, value = "Invalid format for property value '%s'")
    IllegalArgumentException invalidOptionPropertyFormat(String value);

    @Message(id = 22, value = "Class %s not found")
    IllegalArgumentException classNotFound(String name, @Cause ClassNotFoundException cause);

    @Message(id = 23, value = "Class %s is not an instance of %s")
    IllegalArgumentException classNotInstance(String name, Class<?> expectedType);

    @Message(id = 24, value = "Invalid option name '%s'")
    IllegalArgumentException invalidOptionName(String name);

    @Message(id = 25, value = "Invalid null option '%s'")
    IllegalArgumentException invalidNullOption(String name);

    @Message(id = 26, value = "Read with append is not supported")
    IOException readAppendNotSupported();

    @Message(id = 27, value = "Requested file open mode requires Java 7 or higher")
    IOException openModeRequires7();

    @Message(id = 28, value = "Current thread is not an XNIO I/O thread")
    IllegalStateException xnioThreadRequired();

    @Message(id = 29, value = "Compression format not supported")
    IllegalArgumentException badCompressionFormat();

    @Message(id = 30, value = "Both channels must come from the same worker")
    IllegalArgumentException differentWorkers();

    @Message(id = 31, value = "At least one channel must have a connection")
    IllegalArgumentException oneChannelMustBeConnection();

    @Message(id = 32, value = "At least one channel must be an SSL channel")
    IllegalArgumentException oneChannelMustBeSSL();

    @Message(id = 33, value = "'%s' is not a valid QOP value")
    IllegalArgumentException invalidQop(String name);

    @Message(id = 34, value = "Failed to instantiate %s")
    IllegalArgumentException cantInstantiate(Class<?> clazz, @Cause Throwable cause);

    @Message(id = 35, value = "Stream channel was accessed concurrently")
    ConcurrentStreamChannelAccessException concurrentAccess();

    @Message(id = 36, value = "Malformed input")
    CharConversionException malformedInput();

    @Message(id = 37, value = "Unmappable character")
    CharConversionException unmappableCharacter();

    @Message(id = 38, value = "Character decoding problem")
    CharConversionException characterDecodingProblem();

    // id = 39 - Option value range

    @Message(id = 40, value = "Mismatched IP address type; expected %s but got %s")
    IllegalArgumentException mismatchAddressType(Class<? extends InetAddress> expected, Class<? extends InetAddress> actual);

    @Message(id = 41, value = "'%s' is not a valid Strength value")
    IllegalArgumentException invalidStrength(String name);

    @Message(id = 42, value = "Cannot add unresolved address '%s'")
    IllegalArgumentException addressUnresolved(InetSocketAddress bindAddress);

    // HTTP upgrade

    @Message(id = 100, value = "'https' URL scheme chosen but no SSL provider given")
    IllegalArgumentException missingSslProvider();

    @Message(id = 101, value = "Unknown URL scheme '%s' given; must be one of 'http' or 'https'")
    IllegalArgumentException invalidURLScheme(String scheme);

    // SASL/authentication

    @Message(id = 200, value = "Unexpected extra SASL challenge data received")
    SaslException extraChallenge();

    @Message(id = 201, value = "Unexpected extra SASL response data received")
    SaslException extraResponse();

    // SSL

    @Message(id = 300, value = "Socket buffer is too small")
    IllegalArgumentException socketBufferTooSmall();

    @Message(id = 301, value = "Application buffer is too small")
    IllegalArgumentException appBufferTooSmall();

    @Message(id = 302, value = "SSLEngine required a bigger send buffer but our buffer was already big enough")
    IOException wrongBufferExpansion();

    @Message(id = 303, value = "Unexpected wrap result status: %s")
    IOException unexpectedWrapResult(SSLEngineResult.Status status);

    @Message(id = 304, value = "Unexpected handshake status: %s")
    IOException unexpectedHandshakeStatus(SSLEngineResult.HandshakeStatus status);

    @Message(id = 305, value = "Unexpected unwrap result status: %s")
    IOException unexpectedUnwrapResult(SSLEngineResult.Status status);

    @Message(id = 306, value = "SSL connection is not from this provider")
    IllegalArgumentException notFromThisProvider();

    // I/O errors

    @Message(id = 800, value = "Read timed out")
    ReadTimeoutException readTimeout();

    @Message(id = 801, value = "Write timed out")
    WriteTimeoutException writeTimeout();

    @Message(id = 802, value = "Write past the end of a fixed-length channel")
    FixedLengthOverflowException fixedOverflow();

    @Message(id = 803, value = "Close before all bytes were written to a fixed-length channel (%d bytes remaining)")
    FixedLengthUnderflowException fixedUnderflow(long remaining);

    @Message(id = 804, value = "Received an invalid message length of %d")
    IOException recvInvalidMsgLength(int length);

    @Message(id = 805, value = "Writes have been shut down")
    EOFException writeShutDown();

    @Message(id = 806, value = "Transmitted message is too large")
    IOException txMsgTooLarge();

    @Message(id = 807, value = "Unflushed data truncated")
    IOException unflushedData();

    @Message(id = 808, value = "I/O operation was interrupted")
    InterruptedIOException interruptedIO();

    InterruptedIOException interruptedIO(@Field int bytesTransferred);

    @Message(id = 809, value = "Cannot flush due to insufficient buffer space")
    IOException flushSmallBuffer();

    @Message(id = 810, value = "Deflater doesn't need input, but won't produce output")
    IOException deflaterState();

    @Message(id = 811, value = "Inflater needs dictionary")
    IOException inflaterNeedsDictionary();

    @Message(id = 812, value = "Connection closed unexpectedly")
    EOFException connectionClosedEarly();

    @Message(id = 813, value = "The stream is closed")
    IOException streamClosed();

    @Message(id = 814, value = "Mark not set")
    IOException markNotSet();

    // 815 - worker shut down

    @Message(id = 816, value = "Redirect encountered establishing connection")
    String redirect();

    // Unsupported implementation operations - cross-check with xnio-nio

    @Message(id = 900, value = "Method '%s' is not supported on this implementation")
    UnsupportedOperationException unsupported(String methodName);

    // General impl run-time messages - cross-check with xnio-nio

    @Message(id = 1000, value = "Blocking I/O is not allowed on the current thread")
    IllegalStateException blockingNotAllowed();

    @Message(id = 1001, value = "No XNIO provider found")
    IllegalArgumentException noProviderFound();

    @Message(id = 1002, value = "Operation was cancelled")
    CancellationException opCancelled();

    @Message(id = 1003, value = "Running IoFuture notifier %s failed")
    @LogMessage(level = WARN)
    void notifierFailed(@Cause Throwable cause, IoFuture.Notifier<?, ?> notifier);

    @Message(id = 1004, value = "Operation timed out")
    TimeoutException opTimedOut();

    @Message(id = 1005, value = "Not allowed to read non-XNIO properties")
    SecurityException propReadForbidden();

    @Message(id = 1006, value = "Failed to invoke file watch callback")
    @LogMessage(level = ERROR)
    void failedToInvokeFileWatchCallback(@Cause Throwable cause);

    @Message(id = 1007, value = "A channel event listener threw an exception")
    @LogMessage(level = ERROR)
    void listenerException(@Cause Throwable cause);

    @Message(id = 1008, value = "A channel exception handler threw an exception")
    @LogMessage(level = ERROR)
    void exceptionHandlerException(@Cause Throwable cause);

    @Message(id = 1009, value = "Failed to accept a connection on %s: %s")
    @LogMessage(level = ERROR)
    void acceptFailed(AcceptingChannel<? extends ConnectedChannel> channel, IOException reason);

    @Message(id = 1010, value = "Failed to submit task to executor: %s (closing %s)")
    @LogMessage(level = ERROR)
    void executorSubmitFailed(RejectedExecutionException cause, Channel channel);

    // Trace

    @Message(value = "Closing resource %s")
    @LogMessage(level = TRACE)
    void closingResource(Object resource);

    @Message(value = "Closing resource %s failed")
    @LogMessage(level = TRACE)
    void resourceCloseFailed(@Cause Throwable cause, Object resource);

    @Message(value = "Shutting down reads on %s failed")
    @LogMessage(level = TRACE)
    void resourceReadShutdownFailed(@Cause Throwable cause, Object resource);
}
