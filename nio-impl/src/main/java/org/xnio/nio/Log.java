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

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
interface Log extends BasicLogger {
    // todo: turn this into a message logger
    Logger log = Logger.getLogger("org.xnio.nio");
    Logger socketLog = Logger.getLogger("org.xnio.nio.socket");
    Logger selectorLog = Logger.getLogger("org.xnio.nio.selector");
}
