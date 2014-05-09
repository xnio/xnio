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
package org.xnio.ssl.openssl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.xnio.ssl.openssl.Ciphers.SSL_RSA_WITH_RC4_128_MD5;
import static org.xnio.ssl.openssl.Ciphers.SSL_RSA_WITH_RC4_128_SHA;
import static org.xnio.ssl.openssl.Ciphers.TLS_DHE_DSS_WITH_SEED_CBC_SHA;
import static org.xnio.ssl.openssl.Ciphers.TLS_DHE_RSA_WITH_SEED_CBC_SHA;
import static org.xnio.ssl.openssl.Ciphers.TLS_DH_anon_WITH_RC4_128_MD5;
import static org.xnio.ssl.openssl.Ciphers.TLS_DH_anon_WITH_SEED_CBC_SHA;
import static org.xnio.ssl.openssl.Ciphers.TLS_ECDHE_ECDSA_WITH_RC4_128_SHA;
import static org.xnio.ssl.openssl.Ciphers.TLS_ECDHE_RSA_WITH_RC4_128_SHA;
import static org.xnio.ssl.openssl.Ciphers.TLS_ECDH_ECDSA_WITH_RC4_128_SHA;
import static org.xnio.ssl.openssl.Ciphers.TLS_ECDH_RSA_WITH_RC4_128_SHA;
import static org.xnio.ssl.openssl.Ciphers.TLS_ECDH_anon_WITH_RC4_128_SHA;
import static org.xnio.ssl.openssl.Ciphers.TLS_PSK_WITH_RC4_128_SHA;
import static org.xnio.ssl.openssl.Ciphers.TLS_RSA_WITH_SEED_CBC_SHA;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class OpenSSLCipherConfigurationParserTest {

    public OpenSSLCipherConfigurationParserTest() {
    }

    @Test
    public void testMoveToEnd() {
        LinkedHashSet<Ciphers> ciphers = new LinkedHashSet<Ciphers>();
        ciphers.add(Ciphers.SSL_CK_RC4_128_WITH_MD5);
        ciphers.add(Ciphers.SSL_DHE_DSS_EXPORT1024_WITH_RC4_56_SHA);
        ciphers.add(Ciphers.SSL_CK_RC4_128_EXPORT40_WITH_MD5);
        LinkedHashSet<Ciphers> movedCiphers = new LinkedHashSet<Ciphers>();
        movedCiphers.add(Ciphers.SSL_CK_RC4_128_WITH_MD5);
        movedCiphers.add(Ciphers.SSL_CK_RC4_128_EXPORT40_WITH_MD5);
        OpenSSLCipherConfigurationParser.moveToEnd(ciphers, movedCiphers);
        List<Ciphers> list = new ArrayList<Ciphers>(ciphers);
        assertEquals(Ciphers.SSL_DHE_DSS_EXPORT1024_WITH_RC4_56_SHA, list.get(0));
        assertEquals(Ciphers.SSL_CK_RC4_128_WITH_MD5, list.get(1));
        assertEquals(Ciphers.SSL_CK_RC4_128_EXPORT40_WITH_MD5, list.get(2));
    }

    @Test
    public void testMoveRC4ToEnd() {
        LinkedHashSet<Ciphers> ciphers = new LinkedHashSet<Ciphers>();
        ciphers.add(TLS_ECDHE_RSA_WITH_RC4_128_SHA);
        ciphers.add(TLS_ECDH_anon_WITH_RC4_128_SHA);
        ciphers.add(TLS_DHE_RSA_WITH_SEED_CBC_SHA);
        ciphers.add(TLS_ECDH_RSA_WITH_RC4_128_SHA);
        ciphers.add(SSL_RSA_WITH_RC4_128_SHA);
        ciphers.add(TLS_RSA_WITH_SEED_CBC_SHA);
        ciphers.add(TLS_DHE_DSS_WITH_SEED_CBC_SHA);
        ciphers.add(TLS_DH_anon_WITH_SEED_CBC_SHA);
        ciphers.add(TLS_DH_anon_WITH_RC4_128_MD5);
        ciphers.add(TLS_ECDH_ECDSA_WITH_RC4_128_SHA);
        ciphers.add(TLS_ECDHE_ECDSA_WITH_RC4_128_SHA);
        ciphers.add(TLS_PSK_WITH_RC4_128_SHA);
        ciphers.add(SSL_RSA_WITH_RC4_128_MD5);
        assertEquals(13, ciphers.size());
        Set<Ciphers> rc4 = OpenSSLCipherConfigurationParser.filterByEncryption(ciphers, Collections.singleton(Encryption.RC4));
        assertEquals(9, rc4.size());
        OpenSSLCipherConfigurationParser.moveToEnd(ciphers, rc4);
        assertEquals(13, ciphers.size());
        assertEquals("DHE-RSA-SEED-SHA:SEED-SHA:DHE-DSS-SEED-SHA:ADH-SEED-SHA:ECDHE-RSA-RC4-SHA:AECDH-RC4-SHA:ECDH-RSA-RC4-SHA:RC4-SHA:ADH-RC4-MD5:ECDH-ECDSA-RC4-SHA:ECDHE-ECDSA-RC4-SHA:PSK-RC4-SHA:RC4-MD5",
                OpenSSLCipherConfigurationParser.displayResult(ciphers, ":"));
    }

    @Test
    public void testDefaultSort() {
        LinkedHashSet<Ciphers> ciphers = new LinkedHashSet<Ciphers>();
        ciphers.add(TLS_ECDH_anon_WITH_RC4_128_SHA);
        ciphers.add(TLS_ECDHE_RSA_WITH_RC4_128_SHA);
        ciphers.add(TLS_ECDH_RSA_WITH_RC4_128_SHA);
        ciphers.add(TLS_ECDHE_ECDSA_WITH_RC4_128_SHA);
        ciphers.add(TLS_ECDH_ECDSA_WITH_RC4_128_SHA);
        ciphers.add(TLS_DH_anon_WITH_SEED_CBC_SHA);
        ciphers.add(TLS_DHE_RSA_WITH_SEED_CBC_SHA);
        ciphers.add(TLS_DHE_DSS_WITH_SEED_CBC_SHA);
        ciphers.add(TLS_RSA_WITH_SEED_CBC_SHA);
        ciphers.add(TLS_PSK_WITH_RC4_128_SHA);
        ciphers.add(TLS_DH_anon_WITH_RC4_128_MD5);
        ciphers.add(SSL_RSA_WITH_RC4_128_SHA);
        ciphers.add(SSL_RSA_WITH_RC4_128_MD5);
        assertEquals(13, ciphers.size());
        Set<Ciphers> result = OpenSSLCipherConfigurationParser.defaultSort(ciphers);
        assertEquals(13, result.size());
        String expectedResult = "DHE-RSA-SEED-SHA:DHE-DSS-SEED-SHA:ADH-SEED-SHA:SEED-SHA:ECDHE-RSA-RC4-SHA:ECDHE-ECDSA-RC4-SHA:AECDH-RC4-SHA:ADH-RC4-MD5:ECDH-RSA-RC4-SHA:ECDH-ECDSA-RC4-SHA:RC4-SHA:RC4-MD5:PSK-RC4-SHA";
        assertEquals(expectedResult, OpenSSLCipherConfigurationParser.displayResult(result, ":"));
    }
    /**
     * Test of parse method, of class OpenSSLCipherConfigurationParser.
     */
    @Test
    public void testParse() {
        String expectedResult = "DHE-RSA-SEED-SHA:DHE-DSS-SEED-SHA:ADH-SEED-SHA:SEED-SHA:ECDHE-RSA-RC4-SHA:ECDHE-ECDSA-RC4-SHA:AECDH-RC4-SHA:ADH-RC4-MD5:ECDH-RSA-RC4-SHA:ECDH-ECDSA-RC4-SHA:RC4-SHA:RC4-MD5:PSK-RC4-SHA";
        Set<Ciphers> result = OpenSSLCipherConfigurationParser.parse("MEDIUM:!KRB5:!IDEA:!FZA:!SSLv2:!eNULL:!DHE-DSS-RC4-SHA:!DH-DSS-SEED-SHA:!DH-RSA-SEED-SHA");
        assertEquals(13, result.size());
        assertEquals(expectedResult, OpenSSLCipherConfigurationParser.displayResult(result, ":"));
        expectedResult = "ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-SHA384:ECDHE-ECDSA-AES256-SHA384:ECDHE-RSA-AES256-SHA:ECDHE-ECDSA-AES256-SHA:SRP-DSS-AES-256-CBC-SHA:SRP-RSA-AES-256-CBC-SHA:DHE-DSS-AES256-GCM-SHA384:DHE-RSA-AES256-GCM-SHA384:DHE-RSA-AES256-SHA256:DHE-DSS-AES256-SHA256:DHE-RSA-AES256-SHA:DHE-DSS-AES256-SHA:DHE-RSA-CAMELLIA256-SHA:DHE-DSS-CAMELLIA256-SHA:AECDH-AES256-SHA:SRP-AES-256-CBC-SHA:ADH-AES256-GCM-SHA384:ADH-AES256-SHA256:ADH-AES256-SHA:ADH-CAMELLIA256-SHA:ECDH-RSA-AES256-GCM-SHA384:ECDH-ECDSA-AES256-GCM-SHA384:ECDH-RSA-AES256-SHA384:ECDH-ECDSA-AES256-SHA384:ECDH-RSA-AES256-SHA:ECDH-ECDSA-AES256-SHA:AES256-GCM-SHA384:AES256-SHA256:AES256-SHA:CAMELLIA256-SHA:PSK-AES256-CBC-SHA:ECDHE-RSA-DES-CBC3-SHA:ECDHE-ECDSA-DES-CBC3-SHA:SRP-DSS-3DES-EDE-CBC-SHA:SRP-RSA-3DES-EDE-CBC-SHA:EDH-RSA-DES-CBC3-SHA:EDH-DSS-DES-CBC3-SHA:AECDH-DES-CBC3-SHA:SRP-3DES-EDE-CBC-SHA:ADH-DES-CBC3-SHA:ECDH-RSA-DES-CBC3-SHA:ECDH-ECDSA-DES-CBC3-SHA:DES-CBC3-SHA:PSK-3DES-EDE-CBC-SHA:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-SHA256:ECDHE-ECDSA-AES128-SHA256:ECDHE-RSA-AES128-SHA:ECDHE-ECDSA-AES128-SHA:SRP-DSS-AES-128-CBC-SHA:SRP-RSA-AES-128-CBC-SHA:DHE-DSS-AES128-GCM-SHA256:DHE-RSA-AES128-GCM-SHA256:DHE-RSA-AES128-SHA256:DHE-DSS-AES128-SHA256:DHE-RSA-AES128-SHA:DHE-DSS-AES128-SHA:DHE-RSA-CAMELLIA128-SHA:DHE-DSS-CAMELLIA128-SHA:AECDH-AES128-SHA:SRP-AES-128-CBC-SHA:ADH-AES128-GCM-SHA256:ADH-AES128-SHA256:ADH-AES128-SHA:ADH-CAMELLIA128-SHA:ECDH-RSA-AES128-GCM-SHA256:ECDH-ECDSA-AES128-GCM-SHA256:ECDH-RSA-AES128-SHA256:ECDH-ECDSA-AES128-SHA256:ECDH-RSA-AES128-SHA:ECDH-ECDSA-AES128-SHA:AES128-GCM-SHA256:AES128-SHA256:AES128-SHA:CAMELLIA128-SHA:PSK-AES128-CBC-SHA";
        String unsupported = "!DH-DSS-AES256-GCM-SHA384:!DH-RSA-AES256-GCM-SHA384:!DH-RSA-AES256-SHA256:!DH-DSS-AES256-SHA256:!DH-RSA-AES256-SHA:!DH-DSS-AES256-SHA"
                + ":!DH-RSA-CAMELLIA256-SHA:!DH-DSS-CAMELLIA256-SHA:!GOST2001-GOST89-GOST89:!GOST94-GOST89-GOST89:!DH-RSA-DES-CBC3-SHA:!DH-DSS-DES-CBC3-SHA"
                + ":!DH-DSS-AES128-GCM-SHA256:!DH-RSA-AES128-GCM-SHA256:!DH-RSA-AES128-SHA256:!DH-DSS-AES128-SHA256:!DH-RSA-AES128-SHA:!DH-DSS-AES128-SHA:!DH-RSA-CAMELLIA128-SHA"
                + ":!DH-DSS-CAMELLIA128-SHA:";
        result = OpenSSLCipherConfigurationParser.parse("HIGH:!KRB5:!IDEA:!FZA:!SSLv2:!eNULL:" + unsupported);
        assertEquals(79, result.size());
        System.out.println("         Result " + OpenSSLCipherConfigurationParser.displayResult(result, ":"));
        System.out.println("Expected Result " + expectedResult);
        assertEquals(expectedResult, OpenSSLCipherConfigurationParser.displayResult(result, ":"));

    }
}
