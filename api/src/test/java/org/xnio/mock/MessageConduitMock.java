/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc. and/or its affiliates, and individual
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

package org.xnio.mock;

import org.xnio.Buffers;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.conduits.MessageSinkConduit;
import org.xnio.conduits.MessageSourceConduit;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Mock for {@code MessageSinkConduit} and {@code MessageSourceConduit}.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public class MessageConduitMock extends ConduitMock implements MessageSinkConduit, MessageSourceConduit {


    public MessageConduitMock(XnioWorker worker, XnioIoThread xnioIoThread) {
        super(worker, xnioIoThread);
    }

    @Override
    public boolean send(ByteBuffer src) throws IOException {
        return src.remaining() == write(src);
    }

    @Override
    public boolean send(ByteBuffer[] srcs, int offs, int len) throws IOException {
        return Buffers.remaining(srcs, offs, len) == write(srcs, offs, len);
    }

    @Override
    public boolean sendFinal(ByteBuffer src) throws IOException {
        boolean sent = send(src);
        terminateWrites();
        return sent;
    }

    @Override
    public boolean sendFinal(ByteBuffer[] srcs, int offs, int len) throws IOException {
        boolean sent = send(srcs, offs, len);
        terminateWrites();
        return sent;
    }

    @Override
    public int receive(ByteBuffer dst) throws IOException {
        return read(dst);
    }

    @Override
    public long receive(ByteBuffer[] dsts, int offs, int len) throws IOException {
        return read(dsts, offs, len);
    }
}