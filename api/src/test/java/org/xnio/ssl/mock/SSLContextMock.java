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
package org.xnio.ssl.mock;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

/**
 * A {@code SSLContext} mock.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class SSLContextMock extends SSLContext {

    public SSLContextMock(final SSLEngineMock sslEngine) throws NoSuchAlgorithmException {
        super(new SSLContextSpiMock(sslEngine), SSLContext.getDefault().getProvider(), SSLContext.getDefault().getProtocol());
    }

    private static class SSLContextSpiMock extends SSLContextSpi {
        private final SSLEngineMock engineMock;
    
        public SSLContextSpiMock(SSLEngineMock sslEngine) {
            engineMock = sslEngine;
        }

        @Override
        protected SSLEngine engineCreateSSLEngine() {
            return engineMock;
        }
    
        @Override
        protected SSLEngine engineCreateSSLEngine(String host, int port) {
            return engineMock;
        }
    
        @Override
        protected SSLSessionContext engineGetClientSessionContext() {
            throw new RuntimeException("not implemented");
        }
    
        @Override
        protected SSLSessionContext engineGetServerSessionContext() {
            throw new RuntimeException("not implemented");
        }
    
        @Override
        protected SSLServerSocketFactory engineGetServerSocketFactory() {
            throw new RuntimeException("not implemented");
        }
    
        @Override
        protected SSLSocketFactory engineGetSocketFactory() {
            throw new RuntimeException("not implemented");
        }
    
        @Override
        protected void engineInit(KeyManager[] km, TrustManager[] tm, SecureRandom sr) throws KeyManagementException {
            throw new RuntimeException("not implemented");
        }
    }

}
