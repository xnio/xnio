/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.xnio.nio;

import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class NioProvider {
    private static final Logger log = Logger.getLogger(NioProvider.class);

    private Executor executor;
    private ThreadFactory selectorThreadFactory;

    private final Set<NioSelectorRunnable> readers = new HashSet<NioSelectorRunnable>();
    private final Set<NioSelectorRunnable> writers = new HashSet<NioSelectorRunnable>();
    private final Set<NioSelectorRunnable> connectors = new HashSet<NioSelectorRunnable>();

    private int readSelectorThreads = 2;
    private int writeSelectorThreads = 1;
    private int connectionSelectorThreads = 1;

    private Set<Channel> managedChannelSet = Collections.synchronizedSet(new HashSet<Channel>());

    // dependencies

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    public ThreadFactory getSelectorThreadFactory() {
        return selectorThreadFactory;
    }

    public void setSelectorThreadFactory(final ThreadFactory selectorThreadFactory) {
        this.selectorThreadFactory = selectorThreadFactory;
    }

    // configuration

    public int getReadSelectorThreads() {
        return readSelectorThreads;
    }

    public void setReadSelectorThreads(final int readSelectorThreads) {
        this.readSelectorThreads = readSelectorThreads;
    }

    public int getWriteSelectorThreads() {
        return writeSelectorThreads;
    }

    public void setWriteSelectorThreads(final int writeSelectorThreads) {
        this.writeSelectorThreads = writeSelectorThreads;
    }

    public int getConnectionSelectorThreads() {
        return connectionSelectorThreads;
    }

    public void setConnectionSelectorThreads(final int connectionSelectorThreads) {
        this.connectionSelectorThreads = connectionSelectorThreads;
    }

    // lifecycle

    public void start() throws IOException {
        if (selectorThreadFactory == null) {
            selectorThreadFactory = Executors.defaultThreadFactory();
        }
        if (executor == null) {
            executor = IoUtils.directExecutor();
        }
        for (int i = 0; i < readSelectorThreads; i ++) {
            readers.add(new NioSelectorRunnable());
        }
        for (int i = 0; i < writeSelectorThreads; i ++) {
            writers.add(new NioSelectorRunnable());
        }
        for (int i = 0; i < connectionSelectorThreads; i ++) {
            connectors.add(new NioSelectorRunnable());
        }
        for (NioSelectorRunnable runnable : readers) {
            selectorThreadFactory.newThread(runnable).start();
        }
        for (NioSelectorRunnable runnable : writers) {
            selectorThreadFactory.newThread(runnable).start();
        }
        for (NioSelectorRunnable runnable : connectors) {
            selectorThreadFactory.newThread(runnable).start();
        }
    }

    public void stop() throws IOException {
        final List<Channel> channels;
        synchronized (managedChannelSet) {
            channels = new ArrayList<Channel>(managedChannelSet);
            managedChannelSet.clear();
        }
        for (Channel channel : channels) {
            IoUtils.safeClose(channel);
        }
        for (NioSelectorRunnable runnable : readers) {
            runnable.shutdown();
        }
        for (NioSelectorRunnable runnable : writers) {
            runnable.shutdown();
        }
        for (NioSelectorRunnable runnable : connectors) {
            runnable.shutdown();
        }
        readers.clear();
        writers.clear();
        connectors.clear();
    }

    // API

    private NioHandle doAdd(final SelectableChannel channel, final Set<NioSelectorRunnable> runnableSet, final Runnable handler, final boolean oneshot) throws IOException {
        final SynchronousHolder<NioHandle, IOException> holder = new SynchronousHolder<NioHandle, IOException>();
        NioSelectorRunnable nioSelectorRunnable = null;
        int bestLoad = Integer.MAX_VALUE;
        for (NioSelectorRunnable item : runnableSet) {
            final int load = item.getKeyLoad();
            if (load < bestLoad) {
                nioSelectorRunnable = item;
                bestLoad = load;
            }
        }
        if (nioSelectorRunnable == null) {
            throw new IOException("No threads defined to handle this event type");
        }
        final NioSelectorRunnable actualSelectorRunnable = nioSelectorRunnable;
        nioSelectorRunnable.runTask(new SelectorTask() {
            public void run(final Selector selector) {
                try {
                    final SelectionKey selectionKey = channel.register(selector, 0);
                    final NioHandle handle = new NioHandle(selectionKey, actualSelectorRunnable, handler, executor, oneshot);
                    selectionKey.attach(handle);
                    holder.set(handle);
                } catch (ClosedChannelException e) {
                    holder.setProblem(e);
                }
            }
        });
        return holder.get();
    }

    public NioHandle addConnectHandler(final SelectableChannel channel, final Runnable handler, final boolean oneshot) throws IOException {
        return doAdd(channel, connectors, handler, oneshot);
    }

    public NioHandle addReadHandler(final SelectableChannel channel, final Runnable handler) throws IOException {
        return doAdd(channel, readers, handler, true);
    }

    public NioHandle addWriteHandler(final SelectableChannel channel, final Runnable handler) throws IOException {
        return doAdd(channel, writers, handler, true);
    }

    public void addChannel(Channel channel) {
        managedChannelSet.add(channel);
    }

    public void removeChannel(Channel channel) {
        managedChannelSet.remove(channel);
    }
}