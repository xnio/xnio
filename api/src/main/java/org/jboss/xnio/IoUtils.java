package org.jboss.xnio;

import java.io.IOException;
import java.io.Closeable;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import java.nio.channels.Channel;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.channels.ConnectedChannel;

/**
 * General I/O utility methods.
 */
public final class IoUtils {
    private static final Executor NULL_EXECUTOR = new Executor() {
        public void execute(final Runnable command) {
            // no operation
        }
    };
    private static final Executor DIRECT_EXECUTOR = new Executor() {
        public void execute(final Runnable command) {
            command.run();
        }
    };
    private static final IoHandler<Channel> NULL_HANDLER = new IoHandler<Channel>() {
        public void handleOpened(final Channel channel) {
        }

        public void handleReadable(final Channel channel) {
        }

        public void handleWritable(final Channel channel) {
        }

        public void handleClose(final Channel channel) {
        }
    };
    private static final IoHandlerFactory<Channel> NULL_HANDLER_FACTORY = new IoHandlerFactory<Channel>() {
        public IoHandler<Channel> createHandler() {
            return NULL_HANDLER;
        }
    };

    private IoUtils() {}

    /**
     * Create a client from a connector and an address list.  If more than one address is provided, an address is
     * randomly chosen from the list.
     *
     * @param connector the connector to use
     * @param addresses the list of potential addresses to connect to
     * @param <A> the address type
     * @param <T> the channel type
     * @return the client
     */
    public static <A, T extends ConnectedChannel<A> & StreamChannel> StreamIoClient<T> createClient(final StreamIoConnector<A, T> connector, final A... addresses) {
        return new StreamIoClient<T>() {
            public IoFuture<T> connect(final IoHandler<? super T> handler) {
                final int idx = addresses.length == 1 ? 0 : new Random().nextInt(addresses.length);
                final A destAddress = addresses[idx];
                return connector.connectTo(destAddress, handler);
            }
        };
    }

    /**
     * Create a client from a connector and an address list.  If more than one address is provided, an address is
     * randomly chosen from the list.
     *
     * @param connector the connector to use
     * @param addresses the list of potential addresses to connect to
     * @param <A> the address type
     * @param <T> the channel type
     * @return the client
     */
    public static <A, T extends ConnectedChannel<A> & StreamChannel> StreamIoClient<T> createClient(final StreamIoConnector<A, T> connector, final ConnectionAddress<A>... addresses) {
        // todo - addresses is a generic array, which causes a warning
        return new StreamIoClient<T>() {
            public IoFuture<T> connect(final IoHandler<? super T> handler) {
                final int idx = addresses.length == 1 ? 0 : new Random().nextInt(addresses.length);
                final ConnectionAddress<A> connectionAddress = addresses[idx];
                return connector.connectTo(connectionAddress.getLocalAddress(), connectionAddress.getRemoteAddress(), handler);
            }
        };
    }

    /**
     * Create a connection using a client.  The provided handler will handler the connection.  The {@code reconnectExecutor} will
     * be used to execute a reconnect task in the event that the connection fails or is lost or terminated.  If you wish
     * to impose a time delay on reconnect, use the {@link org.jboss.xnio.IoUtils#delayedExecutor(java.util.concurrent.ScheduledExecutorService, long, java.util.concurrent.TimeUnit) delayedExecutor()} method
     * to create a delayed executor.  If you do not want to auto-reconnect use the {@link org.jboss.xnio.IoUtils#nullExecutor()} method to
     * create a null executor.  If you want auto-reconnect to take place immediately, use the {@link IoUtils#directExecutor()} method
     * to create a direct executor.
     *
     * @param client the client to connect on
     * @param handler the handler for the connection
     * @param reconnectExecutor the executor that should execute the reconnect task
     * @return a handle which can be used to terminate the connection
     */
    public static <T extends StreamChannel> Closeable createConnection(final StreamIoClient<T> client, final IoHandler<? super T> handler, final Executor reconnectExecutor) {
        return new Connection<T>(client, handler, reconnectExecutor);
    }

    /**
     * Create a delayed executor.  This is an executor that executes tasks after a given time delay with the help of the
     * provided {@code ScheduledExecutorService}.  To get an executor for this method, use one of the methods on the
     * {@link java.util.concurrent.Executors} class.
     *
     * @param scheduledExecutorService the executor service to use to schedule the task
     * @param delay the time delay before reconnect
     * @param unit the unit of time to use
     * @return an executor that delays execution
     */
    public static Executor delayedExecutor(final ScheduledExecutorService scheduledExecutorService, final long delay, final TimeUnit unit) {
        return new Executor() {
            public void execute(final Runnable command) {
                scheduledExecutorService.schedule(command, delay, unit);
            }
        };
    }

    /**
     * Get the direct executor.  This is an executor that executes the provided task in the same thread.
     *
     * @return a direct executor
     */
    public static Executor directExecutor() {
        return DIRECT_EXECUTOR;
    }

    /**
     * Get the null executor.  This is an executor that never actually executes the provided task.
     *
     * @return a null executor
     */
    public static Executor nullExecutor() {
        return NULL_EXECUTOR;
    }

    /**
     * Get the null handler.  This is a handler whose handler methods all return without taking any action.
     *
     * @return the null handler
     */
    @SuppressWarnings({"unchecked"})
    public static <T extends Channel> IoHandler<T> nullHandler() {
        return (IoHandler<T>) NULL_HANDLER;
    }

    /**
     * Get the null handler factory.  This is a handler factory that returns the null handler.
     *
     * @return the null handler factory
     */
    @SuppressWarnings({"unchecked"})
    public static <T extends Channel> IoHandlerFactory<T> nullHandlerFactory() {
        return (IoHandlerFactory<T>) NULL_HANDLER_FACTORY;
    }

    /**
     * Close a resource, logging an error if an error occurs.
     *
     * @param closeable the resource to close
     */
    public static void safeClose(final Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (Throwable throwable) {
            // todo log @ trace
            throwable.printStackTrace();
        }
    }


    private static final class Connection<T extends StreamChannel> implements Closeable {

        private final StreamIoClient<T> client;
        private final IoHandler<? super T> handler;
        private final Executor reconnectExecutor;

        private volatile boolean stopFlag = false;
        private volatile IoFuture<T> currentFuture;

        private final NotifierImpl notifier = new NotifierImpl();
        private final HandlerImpl handlerWrapper = new HandlerImpl();
        private final ReconnectTask reconnectTask = new ReconnectTask();

        private Connection(final StreamIoClient<T> client, final IoHandler<? super T> handler, final Executor reconnectExecutor) {
            this.client = client;
            this.handler = handler;
            this.reconnectExecutor = reconnectExecutor;
            currentFuture = client.connect(handlerWrapper);
        }

        public void close() throws IOException {
            stopFlag = true;
            final IoFuture<T> future = currentFuture;
            if (future != null) {
                future.cancel();
            }
        }

        private final class NotifierImpl implements IoFuture.Notifier<T> {

            public void notify(final IoFuture<T> future) {
                currentFuture = null;
                switch (future.getStatus()) {
                    case DONE:
                        // todo log normal completion
                        return;
                    case FAILED:
                        // todo log reason
                        future.getException().printStackTrace();
                        break;
                    case CANCELLED:
                        // todo log normal cancellation
                        break;
                }
                if (! stopFlag) {
                    reconnectExecutor.execute(reconnectTask);
                }
                return;
            }
        }

        private final class HandlerImpl implements IoHandler<T> {
            public void handleOpened(final T channel) {
                handler.handleOpened(channel);
            }

            public void handleReadable(final T channel) {
                handler.handleReadable(channel);
            }

            public void handleWritable(final T channel) {
                handler.handleWritable(channel);
            }

            public void handleClose(final T channel) {
                try {
                    // todo log connection term
                    System.out.println("Connection closed");
                    if (! stopFlag) {
                        reconnectExecutor.execute(reconnectTask);
                    }
                } finally {
                    handler.handleClose(channel);
                }
            }
        }

        private final class ReconnectTask implements Runnable {
            public void run() {
                if (! stopFlag) {
                    final IoFuture<T> ioFuture = client.connect(handlerWrapper);
                    ioFuture.addNotifier(notifier);
                    currentFuture = ioFuture;
                }
            }
        }
    }
}
