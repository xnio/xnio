/**
 * Defines an enhanced set of channels.  A channel is an abstraction representing a means of performing data transfer
 * between processes and systems.
 * <p/>
 * The channels provided by this package are extended beyond the basic NIO channels, including special
 * channel types for point-to-point and point-to-multipoint message-oriented channels, multicast support,
 * and generalized support for stream channels (such as serial ports).
 * <p/>
 * In addition, these channels provide a simplified alternative to selectors by way of the {@link org.jboss.xnio.channels.SuspendableChannel}
 * interface.  Rather than associating a channel with a selector, and subsequently imposing complex locking and
 * usage restrictions on that association, you simply define a handler which informs the channel when the handler is
 * ready to handle reads or writes on the channel.
 * <p/>
 * See {@link java.nio.channels} for more information about channels.
 */
package org.jboss.xnio.channels;
