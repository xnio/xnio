/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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

package org.xnio.ducts;

import java.io.IOException;
import org.xnio.Option;
import org.xnio.XnioWorker;

/**
 * An abstract base class for ducts.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractDuct<D extends Duct> implements Duct {

    /**
     * The delegate duct.
     */
    protected final D next;

    /**
     * Construct a new instance.
     *
     * @param next the delegate duct to set
     */
    protected AbstractDuct(final D next) {
        this.next = next;
    }

    public XnioWorker getWorker() {
        return next.getWorker();
    }

    public boolean supportsOption(final Option<?> option) {
        return next.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return next.getOption(option);
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return next.setOption(option, value);
    }
}
