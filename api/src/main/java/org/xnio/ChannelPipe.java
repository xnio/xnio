/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

package org.xnio;

import org.xnio.channels.CloseableChannel;

/**
 * A one-way pipe.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ChannelPipe<L extends CloseableChannel, R extends CloseableChannel> {
    private final L leftSide;
    private final R rightSide;

    /**
     * Construct a new instance.
     *
     * @param leftSide the pipe left side
     * @param rightSide the pipe right side
     */
    public ChannelPipe(final L leftSide, final R rightSide) {
        this.rightSide = rightSide;
        this.leftSide = leftSide;
    }

    /**
     * Get the pipe source.
     *
     * @return the pipe source
     */
    public L getLeftSide() {
        return leftSide;
    }

    /**
     * Get the pipe sink.
     *
     * @return the pipe sink
     */
    public R getRightSide() {
        return rightSide;
    }
}
