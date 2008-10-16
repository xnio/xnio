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
                init();
                return null;
            }
        });
    }

    static native void init();

    static native long read(int fd, ByteBuffer buf) throws IOException;

    static native long read(int fd, ByteBuffer[] buf, int off, int len) throws IOException;

    static native long write(int fd, ByteBuffer buf) throws IOException;

    static native long write(int fd, ByteBuffer[] buf, int off, int len) throws IOException;


    public synchronized static void main(String[] args) throws IOException {
        final ByteBuffer[] bufs = new ByteBuffer[] {
                ByteBuffer.allocate(150),
                ByteBuffer.allocateDirect(150),
                ByteBuffer.allocate(150),
                ByteBuffer.allocateDirect(150),
        };
        long b = read(0, bufs, 0, bufs.length);
        long c = write(2, bufs, 0, bufs.length);
//        final byte[] bytes = new byte[600];
//        bufs[0].get(bytes, 0, 150);
//        bufs[1].get(bytes, 150, 150);
//        bufs[2].get(bytes, 300, 150);
//        bufs[3].get(bytes, 450, 150);
        System.out.println("Read " + b + " bytes, Write " + c + " bytes");
    }
}
