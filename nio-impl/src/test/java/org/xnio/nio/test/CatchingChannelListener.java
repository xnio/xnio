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

package org.xnio.nio.test;

import java.util.List;
import org.xnio.ChannelListener;
import java.nio.channels.Channel;

final class CatchingChannelListener<T extends Channel> implements ChannelListener<T> {

    private final ChannelListener<? super T> delegate;
    private final List<Throwable> problems;

    CatchingChannelListener(final ChannelListener<? super T> delegate, final List<Throwable> problems) {
        this.delegate = delegate;
        this.problems = problems;
    }

    public void handleEvent(final T channel) {
        try {
            if (delegate != null) delegate.handleEvent(channel);
        } catch (RuntimeException t) {
            problems.add(t);
            throw t;
        } catch (Error t) {
            problems.add(t);
            throw t;
        }
    }
}
