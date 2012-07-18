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
