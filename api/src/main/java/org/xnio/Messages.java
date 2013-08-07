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

package org.xnio;

import java.net.SocketAddress;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

import static org.jboss.logging.Logger.Level.*;

@MessageLogger(projectCode = "XNIO")
interface Messages {
    Messages msg = Logger.getMessageLogger(Messages.class, "org.xnio");
    Messages futureMsg = Logger.getMessageLogger(Messages.class, "org.xnio.future");
    Messages optionParseMsg = Logger.getMessageLogger(Messages.class, "org.xnio.option.parse");
    Messages closeMsg = Logger.getMessageLogger(Messages.class, "org.xnio.safe-close");

    // Validation messages

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

    // General run-time messages

    @Message(id = 100, value = "Blocking I/O is not allowed on the current thread")
    IllegalStateException blockingNotAllowed();

    @Message(id = 101, value = "No XNIO provider found")
    IllegalArgumentException noProviderFound();

    @Message(id = 102, value = "Operation was cancelled")
    CancellationException opCancelled();

    @Message(id = 103, value = "Running IoFuture notifier %s failed")
    @LogMessage(level = WARN)
    void notifierFailed(@Cause Throwable cause, IoFuture.Notifier<?, ?> notifier);

    @Message(id = 104, value = "Operation timed out")
    TimeoutException opTimedOut();

    @Message(id = 105, value = "Not allowed to read non-XNIO properties")
    SecurityException propReadForbidden();

    @Message(id = 106, value = "Failed to invoke file watch callback")
    @LogMessage(level = WARN)
    void failedToInvokeFileWatchCallback(@Cause Throwable cause);

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
