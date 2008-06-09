package org.jboss.xnio.helpers;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.StreamIoClient;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.channels.StreamChannel;

/**
 *
 */
public final class ConnectionHelper<T extends StreamChannel> {
    private Closeable connection;
    private int reconnectTime = -1;
    private ScheduledExecutorService scheduledExecutor;
    private StreamIoClient<T> client;
    private IoHandler<? super T> handler;

    public int getReconnectTime() {
        return reconnectTime;
    }

    public void setReconnectTime(final int reconnectTime) {
        this.reconnectTime = reconnectTime;
    }

    public ScheduledExecutorService getScheduledExecutor() {
        return scheduledExecutor;
    }

    public void setScheduledExecutor(final ScheduledExecutorService scheduledExecutor) {
        this.scheduledExecutor = scheduledExecutor;
    }

    public StreamIoClient<T> getClient() {
        return client;
    }

    public void setClient(final StreamIoClient<T> client) {
        this.client = client;
    }

    public IoHandler<? super T> getHandler() {
        return handler;
    }

    public void setHandler(final IoHandler<? super T> handler) {
        this.handler = handler;
    }

    public void start() {
        final Executor reconnectExecutor;
        if (reconnectTime == -1) {
            reconnectExecutor = IoUtils.nullExecutor();
        } else if (reconnectTime == 0) {
            reconnectExecutor = IoUtils.directExecutor();
        } else {
            reconnectExecutor = IoUtils.delayedExecutor(scheduledExecutor, (long) reconnectTime, TimeUnit.MILLISECONDS);
        }
        connection = IoUtils.<T>createConnection(client, handler, reconnectExecutor);
    }

    public void stop() {
        try {
            connection.close();
        } catch (IOException e) {
            // todo log
        }
    }
}
