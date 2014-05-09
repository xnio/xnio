/*
 * Copyright (C) 2014 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.xnio.ssl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.hamcrest.collection.IsCollectionContaining.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.xnio.Sequence;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class JsseSslUtilsTest {

    public JsseSslUtilsTest() {
    }

    /**
     * Test of resolveEnabledCipherSuite method, of class JsseSslUtils.
     */
    @Test
    public void testResolveEnabledCipherSuite() {
        Sequence<String> cipherSuites = Sequence.of(new String[]{"ALL:!MD5:!DHA"});
        Set<String> supportedCiphers = new HashSet<String>(Arrays.asList("MD5", "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA", "SSL_RSA_WITH_RC4_128_SHA"));
        List<String> result = JsseSslUtils.resolveEnabledCipherSuite(cipherSuites, supportedCiphers);
        assertThat(result, is(notNullValue()));
        assertThat(result.size(), is(2));
        assertThat(result, hasItems("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA", "SSL_RSA_WITH_RC4_128_SHA"));
    }

    /**
     * Test of resolveEnabledCipherSuite method, of class JsseSslUtils.
     */
    @Test
    public void testResolveAllEnabledCipherSuite() {
        Sequence<String> cipherSuites = Sequence.of(new String[]{"ALL"});
        Set<String> supportedCiphers = new HashSet<String>(Arrays.asList("MD5", "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA", "SSL_RSA_WITH_RC4_128_SHA"));
        List<String> result = JsseSslUtils.resolveEnabledCipherSuite(cipherSuites, supportedCiphers);
        assertThat(result, is(notNullValue()));
        assertThat(result.size(), is(3));
        assertThat(result, hasItems("MD5", "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA", "SSL_RSA_WITH_RC4_128_SHA"));
    }

}
