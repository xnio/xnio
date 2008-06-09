package org.jboss.xnio.core.nio;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.Executor;

/**
 *
 */
public final class NioHandle {
    private final SelectionKey selectionKey;
    private final NioSelectorRunnable selectorRunnable;
    private final Runnable handler;
    private final Executor handlerExecutor;

    NioHandle(final SelectionKey selectionKey, final NioSelectorRunnable selectorRunnable, final Runnable handler, final Executor handlerExecutor) {
        this.selectionKey = selectionKey;
        this.selectorRunnable = selectorRunnable;
        this.handler = handler;
        this.handlerExecutor = handlerExecutor;
    }

    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    NioSelectorRunnable getSelectorRunnable() {
        return selectorRunnable;
    }

    Runnable getHandler() {
        return handler;
    }

    Executor getHandlerExecutor() {
        return handlerExecutor;
    }

    public void cancelKey() {
        selectorRunnable.queueTask(new SelectorTask() {
            public void run(final Selector selector) {
                selectionKey.cancel();
            }
        });
        selectionKey.selector().wakeup();
    }
}
