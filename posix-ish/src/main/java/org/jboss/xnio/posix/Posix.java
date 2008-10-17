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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.IOError;
import static java.lang.Thread.sleep;

/**
 *
 */
public final class Posix {
    private Posix() {
    }

    static {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                System.loadLibrary("xnio");
                try {
                    init();
                } catch (IOException e) {
                    throw new IOError(e);
                }
                return null;
            }
        });
    }

    static native void init() throws IOException;

    static native long read(int fd, ByteBuffer buf) throws IOException;

    static native long readv(int fd, ByteBuffer[] buf, int off, int len) throws IOException;

    static native long write(int fd, ByteBuffer buf) throws IOException;

    static native long writev(int fd, ByteBuffer[] buf, int off, int len) throws IOException;

    static native void preClose(int fd) throws IOException;

    static native void close(int fd) throws IOException;

    static native void shutdown(int fd, int mode) throws IOException;

    static native int tcpSocket() throws IOException;

    static native int udpSocket() throws IOException;

    static native int unixStreamSocket() throws IOException;

    static native int unixDatagramSocket() throws IOException;

    static native void unixBind(int fd, String path) throws IOException;

    static native void block(int fd, boolean reads, boolean writes) throws IOException;

    static native void listen(int fd, int backlog) throws IOException;

    static native void unblockThread(long threadId);

    static void safeClose(int fd) {
        try {
            close(fd);
        } catch (IOException e) {
            // todo log?
        }
    }

    public static void main(String[] args) {
        try {
            final ByteBuffer[] bufs = new ByteBuffer[] {
                    ByteBuffer.allocate(150),
                    ByteBuffer.allocateDirect(150),
                    ByteBuffer.allocate(150),
                    ByteBuffer.allocateDirect(150),
            };
            final Thread t = Thread.currentThread();
            new Thread(new Runnable() {
                public void run() {
                    try {
                        sleep(1000L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    try {
                        System.out.println("Closing!");
                        close(0);
                        System.out.println("Closed!");
                        t.interrupt();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
            block(0, true, true);
            long b = readv(0, bufs, 0, bufs.length);
            long c = writev(2, bufs, 0, bufs.length);
//            preClose(2);
            long d = 0;
//            d = write(2, bufs, 0, bufs.length);
            System.out.println("Read " + b + " bytes, Write " + c + " bytes, fail " + d);
        } catch (Throwable e) {
            e.printStackTrace(System.out);
        }
    }
}
