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
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
* @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
*/
public final class DuctWritableByteChannel implements WritableByteChannel {

    private StreamSinkDuct duct;

    public DuctWritableByteChannel(final StreamSinkDuct duct) {
        this.duct = duct;
    }

    public int write(final ByteBuffer src) throws IOException {
        return duct.write(src);
    }

    public boolean isOpen() {
        return ! duct.isWriteShutdown();
    }

    public void close() throws IOException {
        duct.truncateWrites();
    }
}
