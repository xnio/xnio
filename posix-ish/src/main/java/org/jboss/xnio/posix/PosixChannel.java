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

package org.jboss.xnio.posix;

import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.ByteBuffer;
import java.io.IOException;

/**
 *
 */
public class PosixChannel implements Channel {
    private int fd;
    private boolean close;
    private int refcnt;

    private final Object fdlock = new Object();

    PosixChannel(int fd) {
        this.fd = fd;
    }

    public boolean isOpen() {
        return fd != -1;
    }

    public void close() throws IOException {
        synchronized (fdlock) {
            if (close || fd == -1) {
                return;
            } else if (refcnt == 0) try {
                Posix.close(fd);
            } finally {
                fd = -1;
            } else try {
                Posix.preClose(fd);
            } finally {
                close = true;
            }
        }
    }

    protected int getFd() throws IOException {
        synchronized (fdlock) {
            if (close || fd == -1) {
                throw new ClosedChannelException();
            }
            refcnt ++;
            return fd;
        }
    }

    protected void releaseFd() throws IOException {
        synchronized (fdlock) {
            if (--refcnt == 0 && close) {
                try {
                    Posix.close(fd);
                    return;
                } finally {
                    fd = -1;
                }
            }
        }
    }

    protected void safeReleaseFd() {
        try {
            releaseFd();
        } catch (IOException e) {
            // todo log it
        }
    }

    protected int doRead(ByteBuffer buffer) throws IOException {
        final int fd = getFd();
        try {
            long cnt = Posix.read(fd, buffer);
            buffer.position(buffer.position() + (int) cnt);
            return (int) cnt;
        } finally {
            safeReleaseFd();
        }
    }

    protected int doWrite(ByteBuffer buffer) throws IOException {
        final int fd = getFd();
        try {
            long cnt = Posix.write(fd, buffer);
            buffer.position(buffer.position() + (int) cnt);
            return (int) cnt;
        } finally {
            safeReleaseFd();
        }
    }
}
