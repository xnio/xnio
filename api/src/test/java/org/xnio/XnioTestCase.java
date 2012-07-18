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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.xnio.AssertReadWrite.assertReadMessage;
import static org.xnio.AssertReadWrite.assertWrittenMessage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.NonWritableChannelException;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.util.ServiceConfigurationError;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.junit.Test;
import org.xnio.channels.Channels;
import org.xnio.mock.ConnectedStreamChannelMock;
import org.xnio.ssl.XnioSsl;

/**
 * Test for {@link Xnio}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class XnioTestCase {

    static {
        String securityPolicyFile = XnioTestCase.class.getClassLoader().getResource("security.policy").getFile();
        AccessController.doPrivileged(new SetSecurityPolicyAction(securityPolicyFile));
    }

    private static final String DEFAULT_KEY_STORE = "keystore.jks";
    private static final String DEFAULT_KEY_STORE_PASSWORD = "apiTest";

    @Test
    public void allowBlocking() {
        assertTrue(Xnio.isBlockingAllowed());
        Xnio.checkBlockingAllowed();
        Xnio.allowBlocking(true);
        assertTrue(Xnio.isBlockingAllowed());
        Xnio.checkBlockingAllowed();

        Xnio.allowBlocking(false);
        assertFalse(Xnio.isBlockingAllowed());
        IllegalStateException expected = null;
        try {
            Xnio.checkBlockingAllowed();
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);

        Xnio.allowBlocking(false);
        assertFalse(Xnio.isBlockingAllowed());
        expected = null;
        try {
            Xnio.checkBlockingAllowed();
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);

        Xnio.allowBlocking(true);
        assertTrue(Xnio.isBlockingAllowed());
        Xnio.checkBlockingAllowed();
        Xnio.allowBlocking(true);
        assertTrue(Xnio.isBlockingAllowed());
        Xnio.checkBlockingAllowed();
    }

    @Test
    public void allowBlockingWithSecurity() {
        final SecurityManager securityManager = new SecurityManager();
        System.setSecurityManager(securityManager);
        assertTrue(Xnio.isBlockingAllowed());
        Xnio.checkBlockingAllowed();

        AccessControlException expected = null;
        try {
            Xnio.allowBlocking(true);
        } catch (AccessControlException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            Xnio.allowBlocking(false);
        } catch (AccessControlException e) {
            expected = e;
        }
        assertNotNull(expected);
        AccessController.doPrivileged(new GrantAllPermissionsAction());
        allowBlocking();
        AccessController.doPrivileged(new ResetSecurityManagerAction());
    }

    @Test
    public void retrieveInstance() {
        final Xnio xnio = Xnio.getInstance();
        assertNotNull(xnio);
        assertSame(xnio, Xnio.getInstance(getClass().getClassLoader()));

        ServiceConfigurationError expectedError = null;
        try {
            Xnio.getInstance((ClassLoader) null);
        } catch (ServiceConfigurationError e) {
            expectedError = e;
        }
        assertNotNull(expectedError);

        assertSame(xnio, Xnio.getInstance("xnio-mock"));

        IllegalArgumentException expectedException = null;
        try {
            Xnio.getInstance("xnio");
        } catch (IllegalArgumentException e) {
            expectedException = e;
        }
        assertNotNull(expectedException);

        assertNotNull(xnio.toString());
        assertEquals("xnio-mock", xnio.getName());
    }

    @Test
    public void retrieveSslProvider() throws GeneralSecurityException {
        final Xnio xnio = Xnio.getInstance();
        final OptionMap optionMap = OptionMap.create(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.REQUIRED, Options.SSL_STARTTLS, true);
        final XnioSsl sslProvider = xnio.getSslProvider(optionMap);
        assertNotNull(sslProvider);
    }

    @Test
    public void retrieveSslProviderWithTrustAndKeyManagers() throws GeneralSecurityException, FileNotFoundException, IOException {
        final Xnio xnio = Xnio.getInstance();
        final OptionMap optionMap = OptionMap.create(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.REQUIRED, Options.SSL_STARTTLS, true);

        final KeyStore keyStore = KeyStore.getInstance("JKS");
        final String keyStorePath = XnioTestCase.class.getClassLoader().getResource(DEFAULT_KEY_STORE).getFile();
        keyStore.load(new FileInputStream(keyStorePath),  DEFAULT_KEY_STORE_PASSWORD.toCharArray());
        final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, DEFAULT_KEY_STORE_PASSWORD.toCharArray());
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);

        final XnioSsl sslProvider = xnio.getSslProvider(keyManagerFactory.getKeyManagers(),
                trustManagerFactory.getTrustManagers(), optionMap);
        assertNotNull(sslProvider);
    }

    private File createTempFile() throws IOException {
        final File file = File.createTempFile("test", ".txt");
        file.deleteOnExit();
        return file;
    }

    private void checkReadOnlyFileChannel(FileChannel fileChannel) throws IOException {
        try {
            assertNotNull(fileChannel);
            NonWritableChannelException expected = null;
            try {
                fileChannel.write(ByteBuffer.allocate(10));
            } catch (NonWritableChannelException e) {
                expected = e;
            }
            assertNotNull(expected);
            fileChannel.position(0);
            ByteBuffer buffer = ByteBuffer.allocate(10);
            fileChannel.read(buffer);
            assertReadMessage(buffer);
        } finally {
            fileChannel.close();
        }
    }

    private void checkReadWriteFileChannel(FileChannel fileChannel) throws IOException {
        try {
            final ConnectedStreamChannelMock channelMock = new ConnectedStreamChannelMock();
            channelMock.setReadData("test");
            channelMock.enableRead(true);
            assertNotNull(fileChannel);
            final ByteBuffer buffer = ByteBuffer.allocate(10);
            buffer.put("test".getBytes("UTF-8")).flip();
            assertEquals(4, fileChannel.write(buffer));
            fileChannel.position(0);
            Channels.transferBlocking(channelMock, fileChannel, 0, 4);
            assertWrittenMessage(channelMock, "test");
        } finally {
            fileChannel.close();
        }
    }

    @Test
    public void openFileWithReadOnlyOption() throws IOException {
        final File file = createTempFile();
        final OptionMap optionMap = OptionMap.create(Options.FILE_ACCESS, FileAccess.READ_ONLY);
        checkReadOnlyFileChannel(Xnio.getInstance().openFile(file, optionMap));
    }

    @Test
    public void openFileWithReadWriteOption() throws IOException {
        final File file = createTempFile();
        final OptionMap optionMap = OptionMap.create(Options.FILE_ACCESS, FileAccess.READ_WRITE);
        checkReadWriteFileChannel(Xnio.getInstance().openFile(file, optionMap));
    }

    @Test
    public void openFileWithEmptyOptionMap() throws IOException {
        final File file = createTempFile();
        checkReadWriteFileChannel(Xnio.getInstance().openFile(file, OptionMap.EMPTY));
    }

    @Test
    public void openFileNameWithReadOnlyOption() throws IOException {
        final File file = createTempFile();
        final OptionMap optionMap = OptionMap.create(Options.FILE_ACCESS, FileAccess.READ_ONLY);
        checkReadOnlyFileChannel(Xnio.getInstance().openFile(file.getAbsolutePath(), optionMap));
    }

    @Test
    public void openFileNameWithReadWriteOption() throws IOException {
        final File file = createTempFile();
        final OptionMap optionMap = OptionMap.create(Options.FILE_ACCESS, FileAccess.READ_WRITE);
        checkReadWriteFileChannel(Xnio.getInstance().openFile(file.getAbsolutePath(), optionMap));
    }

    @Test
    public void openFileNameWithEmptyOptionMap() throws IOException {
        final File file = createTempFile();
        checkReadWriteFileChannel(Xnio.getInstance().openFile(file.getAbsolutePath(), OptionMap.EMPTY));
    }

    @Test
    public void openFileWithReadOnlyFileAccess() throws IOException {
        final File file = createTempFile();
        checkReadOnlyFileChannel(Xnio.getInstance().openFile(file, FileAccess.READ_ONLY));
    }

    @Test
    public void openFileWithReadWriteFileAccess() throws IOException {
        final File file = createTempFile();
        checkReadWriteFileChannel(Xnio.getInstance().openFile(file, FileAccess.READ_WRITE));
    }

    @Test
    public void openFileNameWithReadOnlyFileAccess() throws IOException {
        final File file = createTempFile();
        checkReadOnlyFileChannel(Xnio.getInstance().openFile(file.getAbsolutePath(), FileAccess.READ_ONLY));
    }

    @Test
    public void openFileNameWithReadWriteFileAccess() throws IOException {
        final File file = createTempFile();
        checkReadWriteFileChannel(Xnio.getInstance().openFile(file.getAbsolutePath(), FileAccess.READ_WRITE));
    }

    @Test
    public void invalidOpenFileName() throws IOException {
        final File file = createTempFile();
        final Xnio xnio = Xnio.getInstance();
        Exception expected = null;
        try {
            xnio.openFile(file.getAbsolutePath(), (FileAccess) null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnio.openFile(file.getAbsolutePath(), (OptionMap) null);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnio.openFile((String) null, FileAccess.READ_WRITE);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnio.openFile((String) null, OptionMap.EMPTY);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnio.openFile(file, (FileAccess) null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnio.openFile(file, (OptionMap) null);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnio.openFile((File) null, FileAccess.READ_WRITE);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnio.openFile((File) null, OptionMap.EMPTY);
        } catch (NullPointerException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void createWorker() throws IllegalArgumentException, IOException {
        final Xnio xnio = Xnio.getInstance();
        final XnioWorker xnioWorker1 = xnio.createWorker(OptionMap.EMPTY);
        assertNotNull(xnioWorker1);
        assertSame(xnio, xnioWorker1.getXnio());
        assertNull(xnioWorker1.getTerminationTask());
        final XnioWorker xnioWorker2 = xnio.createWorker(Thread.currentThread().getThreadGroup(), OptionMap.EMPTY);
        assertNotNull(xnioWorker2);
        assertSame(xnio, xnioWorker2.getXnio());
        assertNull(xnioWorker2.getTerminationTask());
    }

    @Test
    public void propertiesRetrieval() {
        final Xnio xnio = Xnio.getInstance();
        assertNull(xnio.getProperty("xnio.test.prop"));
        assertEquals("foo", xnio.getProperty("xnio.test.prop", "foo"));
        System.setProperty("xnio.test.prop", "foo2");
        assertEquals("foo2", xnio.getProperty("xnio.test.prop"));
        assertEquals("foo2", xnio.getProperty("xnio.test.prop"));
        AccessController.doPrivileged(new GrantAllPermissionsAction());
        assertNull(xnio.getProperty("xnio.prop.test"));
        assertEquals("aaa", xnio.getProperty("xnio.prop.test", "aaa"));
        System.setProperty("xnio.prop.test", "bbb");
        assertEquals("bbb", xnio.getProperty("xnio.prop.test"));
        assertEquals("bbb", xnio.getProperty("xnio.prop.test"));
        AccessController.doPrivileged(new ResetSecurityManagerAction());
    }

    @Test
    public void illegalPropertiesRetrieval() {
        System.setSecurityManager(null);
        final Xnio xnio = Xnio.getInstance();
        
        SecurityException expected = null;
        try {
            xnio.getProperty("any prop that does not start with xnio.");
        } catch (SecurityException e) {
            expected = e;
        }
        assertNotNull(expected);
        expected = null;
        try {
            xnio.getProperty("xnio - any prop that does not start with xnio.", "default");
        } catch (SecurityException e) {
            expected = e;
        }
        assertNotNull(expected);
        
    }

    private static class SetSecurityPolicyAction implements PrivilegedAction<Void> {

        private final String propertyValue;

        public SetSecurityPolicyAction(String fileName) {
            propertyValue = fileName;
        }

        @Override
        public Void run() {
            System.setProperty("java.security.policy", propertyValue);
            return null;
        }
        
    }

    private static class GrantAllPermissionsAction implements PrivilegedAction<Void> {

        @Override
        public Void run() {
            System.setSecurityManager(new SecurityManager() {
                @Override
                public void checkPermission(Permission permission, Object context) {}
                @Override
                public void checkPermission(Permission permission) {}
            });
            return null;
        }
        
    }

    private static class ResetSecurityManagerAction implements PrivilegedAction<Void> {

        @Override
        public Void run() {
            System.setSecurityManager(null);
            return null;
        }
        
    }
}
