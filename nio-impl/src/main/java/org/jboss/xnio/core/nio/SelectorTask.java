package org.jboss.xnio.core.nio;

import java.nio.channels.Selector;

/**
 *
 */
public interface SelectorTask {
    void run(Selector selector);
}
