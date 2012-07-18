/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. 


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
