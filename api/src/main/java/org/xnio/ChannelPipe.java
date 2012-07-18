/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
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
