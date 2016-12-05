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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.xnio._private.Messages;

/**
 * Class in charge with parsing openSSL expressions to define a list of ciphers.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class OpenSSLCipherConfigurationParser {

    /**
     * System property key to define the DEFAULT ciphers.
     */
    public static final String DEFAULT_EXPRESSION_KEY = "openssl.default.ciphers";

    private static boolean initialized = false;

    private static final String SEPARATOR = ":";
    /**
     * If ! is used then the ciphers are permanently deleted from the list. The ciphers deleted can never reappear in the list
     * even if they are explicitly stated.
     */
    private final static String EXCLUDE = "!";
    /**
     * If - is used then the ciphers are deleted from the list, but some or all of the ciphers can be added again by later
     * options.
     */
    private static final String DELETE = "-";
    /**
     * If + is used then the ciphers are moved to the end of the list. This option doesn't add any new ciphers it just moves
     * matching existing ones.
     */
    private static final String TO_END = "+";
    /**
     * All ciphers by their openssl alias name.
     */
    private static final Map<String, List<Ciphers>> aliases = new LinkedHashMap<String, List<Ciphers>>();

    /**
     * the 'NULL' ciphers that is those offering no encryption. Because these offer no encryption at all and are a security risk
     * they are disabled unless explicitly included.
     */
    private static final String eNULL = "eNULL";
    /**
     * The cipher suites offering no authentication. This is currently the anonymous DH algorithms. T These cipher suites are
     * vulnerable to a 'man in the middle' attack and so their use is normally discouraged.
     */
    private static final String aNULL = "aNULL";

    /**
     * 'high' encryption cipher suites. This currently means those with key lengths larger than 128 bits, and some cipher suites
     * with 128-bit keys.
     */
    private static final String HIGH = "HIGH";
    /**
     * 'medium' encryption cipher suites, currently some of those using 128 bit encryption.
     */
    private static final String MEDIUM = "MEDIUM";
    /**
     * 'low' encryption cipher suites, currently those using 64 or 56 bit encryption algorithms but excluding export cipher
     * suites.
     */
    private static final String LOW = "LOW";
    /**
     * Export encryption algorithms. Including 40 and 56 bits algorithms.
     */
    private static final String EXPORT = "EXPORT";
    /**
     * 40 bit export encryption algorithms.
     */
    private static final String EXPORT40 = "EXPORT40";
    /**
     * 56 bit export encryption algorithms.
     */
    private static final String EXPORT56 = "EXPORT56";
    /**
     * Cipher suites using RSA key exchange.
     */
    private static final String kRSA = "kRSA";
    /**
     * Cipher suites using RSA authentication.
     */
    private static final String aRSA = "aRSA";
    /**
     * Cipher suites using RSA for key exchange or for authentication.
     */
    private static final String RSA = "RSA";
    /**
     * Cipher suites using ephemeral DH key agreement.
     */
    private static final String kEDH = "kEDH";
    /**
     * Cipher suites using ephemeral DH key agreement. equivalent to kEDH:-ADH
     */
    private static final String EDH = "EDH";
    /**
     * Cipher suites using DH key agreement and DH certificates signed by CAs with RSA keys.
     */
    private static final String kDHr = "kDHr";
    /**
     * Cipher suites using DH key agreement and DH certificates signed by CAs with DSS keys.
     */
    private static final String kDHd = "kDHd";
    /**
     * Cipher suites using DH key agreement and DH certificates signed by CAs with RSA or DSS keys.
     */
    private static final String kDH = "kDH";
    /**
     * Cipher suites using DSS authentication, i.e. the certificates carry DSS keys.
     */
    private static final String aDSS = "aDSS";
    /**
     * Cipher suites effectively using DH authentication, i.e. the certificates carry DH keys.
     */
    private static final String aDH = "aDH";
    /**
     * Ciphers suites using FORTEZZA key exchange algorithms.
     */
    private static final String kFZA = "kFZA";
    /**
     * Ciphers suites using FORTEZZA authentication algorithms.
     */
    private static final String aFZA = "aFZA";
    /**
     * Ciphers suites using FORTEZZA encryption algorithms.
     */
    private static final String eFZA = "eFZA";
    /**
     * Ciphers suites using all FORTEZZA algorithms.
     */
    private static final String FZA = "FZA";
    /**
     * TLS v1.2 cipher suites. Note: there are no cipher suites specific to TLS v1.1.
     */
    private static final String TLSv1_2 = "TLSv1_2";
    /**
     * TLS v1.0 cipher suites.
     */
    private static final String TLSv1 = "TLSv1";
    /**
     * SSL v2.0 cipher suites.
     */
    private static final String SSLv2 = "SSLv2";
    /**
     * SSL v3.0 cipher suites.
     */
    private static final String SSLv3 = "SSLv3";
    /**
     * Cipher suites using DH, including anonymous DH, ephemeral DH and fixed DH.
     */
    private static final String DH = "DH";
    /**
     * Anonymous DH cipher suites.
     */
    private static final String ADH = "ADH";
    /**
     * Cipher suites using 128 bit AES.
     */
    private static final String AES128 = "AES128";
    /**
     * Cipher suites using 256 bit AE.
     */
    private static final String AES256 = "AES256";
    /**
     * Cipher suites using either 128 or 256 bit AES.
     */
    private static final String AES = "AES";
    /**
     * AES in Galois Counter Mode (GCM): these cipher suites are only supported in TLS v1.2.
     */
    private static final String AESGCM = "AESGCM";
    /**
     * Cipher suites using 128 bit CAMELLIA.
     */
    private static final String CAMELLIA128 = "CAMELLIA128";
    /**
     * Cipher suites using 256 bit CAMELLIA.
     */
    private static final String CAMELLIA256 = "CAMELLIA256";
    /**
     * Cipher suites using either 128 or 256 bit CAMELLIA.
     */
    private static final String CAMELLIA = "CAMELLIA";
    /**
     * Cipher suites using triple DES.
     */
    private static final String TRIPLE_DES = "3DES";
    /**
     * Cipher suites using DES (not triple DES).
     */
    private static final String DES = "DES";
    /**
     * Cipher suites using RC4.
     */
    private static final String RC4 = "RC4";
    /**
     * Cipher suites using RC2.
     */
    private static final String RC2 = "RC2";
    /**
     * Cipher suites using IDEA.
     */
    private static final String IDEA = "IDEA";
    /**
     * Cipher suites using SEED.
     */
    private static final String SEED = "SEED";
    /**
     * Cipher suites using MD5.
     */
    private static final String MD5 = "MD5";
    /**
     * Cipher suites using SHA1.
     */
    private static final String SHA1 = "SHA1";
    /**
     * Cipher suites using SHA1.
     */
    private static final String SHA = "SHA";
    /**
     * Cipher suites using SHA256.
     */
    private static final String SHA256 = "SHA256";
    /**
     * Cipher suites using SHA384.
     */
    private static final String SHA384 = "SHA384";
    /**
     * Cipher suites using KRB5.
     */
    private static final String KRB5 = "KRB5";
    /**
     * Cipher suites using GOST R 34.10 (either 2001 or 94) for authentication.
     */
    private static final String aGOST = "aGOST";
    /**
     * Cipher suites using GOST R 34.10-2001 for authentication.
     */
    private static final String aGOST01 = "aGOST01";
    /**
     * Cipher suites using GOST R 34.10-94 authentication (note that R 34.10-94 standard has been expired so use GOST R
     * 34.10-2001)
     */
    private static final String aGOST94 = "aGOST94";
    /**
     * Cipher suites using using VKO 34.10 key exchange, specified in the RFC 4357.
     */
    private static final String kGOST = "kGOST";
    /**
     * Cipher suites, using HMAC based on GOST R 34.11-94.
     */
    private static final String GOST94 = "GOST94";
    /**
     * Cipher suites using GOST 28147-89 MAC instead of HMAC.
     */
    private static final String GOST89MAC = "GOST89MAC";
    /**
     * Cipher suites using pre-shared keys (PSK).
     */
    private static final String PSK = "PSK";

    private static final String DEFAULT = "DEFAULT";
    private static final String COMPLEMENTOFDEFAULT = "COMPLEMENTOFDEFAULT";

    private static final String ALL = "ALL";
    private static final String COMPLEMENTOFALL = "COMPLEMENTOFALL";

    private static final void init() {

        for (Ciphers cipher : Ciphers.values()) {
            String alias = cipher.getOpenSSLAlias();
            if (aliases.containsKey(alias)) {
                aliases.get(alias).add(cipher);
            } else {
                List<Ciphers> list = new ArrayList<>();
                list.add(cipher);
                aliases.put(alias, list);
            }
            aliases.put(cipher.name(), Collections.singletonList(cipher));
        }
        List<Ciphers> allCiphers = Arrays.asList(Ciphers.values());
        Collections.reverse(allCiphers);
        LinkedHashSet<Ciphers> all = defaultSort(new LinkedHashSet<Ciphers>(allCiphers));
        addListAlias(ALL, all);
        addListAlias(HIGH, filterByEncryptionLevel(all, Collections.singleton(EncryptionLevel.HIGH)));
        addListAlias(MEDIUM, filterByEncryptionLevel(all, Collections.singleton(EncryptionLevel.MEDIUM)));
        addListAlias(LOW, filterByEncryptionLevel(all, Collections.singleton(EncryptionLevel.LOW)));
        addListAlias(EXPORT, filterByEncryptionLevel(all, new HashSet<EncryptionLevel>(Arrays.asList(EncryptionLevel.EXP40, EncryptionLevel.EXP56))));
        aliases.put("EXP", aliases.get(EXPORT));
        addListAlias(EXPORT40, filterByEncryptionLevel(all, Collections.singleton(EncryptionLevel.EXP40)));
        addListAlias(EXPORT56, filterByEncryptionLevel(all, Collections.singleton(EncryptionLevel.EXP56)));
        addListAlias(eNULL, filterByEncryption(all, Collections.singleton(Encryption.eNULL)));
        aliases.put("NULL", aliases.get(eNULL));
        aliases.put(COMPLEMENTOFALL, aliases.get(eNULL));
        addListAlias(aNULL, filterByAuthentication(all, Collections.singleton(Authentication.aNULL)));
        addListAlias(kRSA, filterByKeyExchange(all, Collections.singleton(KeyExchange.RSA)));
        addListAlias(aRSA, filterByAuthentication(all, Collections.singleton(Authentication.RSA)));
        addListAlias(RSA, filter(all, null, Collections.singleton(KeyExchange.RSA), Collections.singleton(Authentication.RSA), null, null, null));
        addListAlias(kEDH, filterByKeyExchange(all, Collections.singleton(KeyExchange.EDH)));
        Set<Ciphers> edh = filterByKeyExchange(all, Collections.singleton(KeyExchange.EDH));
        edh.removeAll(filterByAuthentication(all, Collections.singleton(Authentication.DH)));
        addListAlias(EDH, edh);
        addListAlias(kDHr, filterByKeyExchange(all, Collections.singleton(KeyExchange.DHr)));
        addListAlias(kDHd, filterByKeyExchange(all, Collections.singleton(KeyExchange.DHd)));
        addListAlias(kDH, filterByKeyExchange(all, new HashSet<KeyExchange>(Arrays.asList(KeyExchange.DHr, KeyExchange.DHd))));
        addListAlias(aDSS, filterByAuthentication(all, Collections.singleton(Authentication.DSS)));
        aliases.put("DSS", aliases.get(aDSS));
        addListAlias(aDH, filterByAuthentication(all, Collections.singleton(Authentication.DH)));
        addListAlias(kFZA, filterByKeyExchange(all, Collections.singleton(KeyExchange.FZA)));
        addListAlias(aFZA, filterByAuthentication(all, Collections.singleton(Authentication.FZA)));
        addListAlias(eFZA, filterByEncryption(all, Collections.singleton(Encryption.FZA)));
        addListAlias(FZA, filter(all, null, Collections.singleton(KeyExchange.FZA), Collections.singleton(Authentication.FZA), Collections.singleton(Encryption.FZA), null, null));
        addListAlias(TLSv1_2, filterByProtocol(all, Collections.singleton(Protocol.TLSv1_2)));
        addListAlias("TLSv1.1", filterByProtocol(all, Collections.singleton(Protocol.SSLv3)));
        addListAlias(TLSv1, filterByProtocol(all, Collections.singleton(Protocol.TLSv1)));
        addListAlias(SSLv3, filterByProtocol(all, Collections.singleton(Protocol.SSLv3)));
        addListAlias(SSLv2, filterByProtocol(all, Collections.singleton(Protocol.SSLv2)));
        addListAlias(DH, filterByKeyExchange(all, new HashSet<KeyExchange>(Arrays.asList(KeyExchange.DHr, KeyExchange.DHd, KeyExchange.EDH))));
        Set<Ciphers> adh = filterByKeyExchange(all, Collections.singleton(KeyExchange.EDH));
        adh.retainAll(filterByAuthentication(all, Collections.singleton(Authentication.aNULL)));
        addListAlias(ADH, adh);
        addListAlias(AES128, filterByEncryption(all, new HashSet<Encryption>(Arrays.asList(Encryption.AES128, Encryption.AES128GCM))));
        addListAlias(AES256, filterByEncryption(all, new HashSet<Encryption>(Arrays.asList(Encryption.AES256, Encryption.AES256GCM))));
        addListAlias(AES, filterByEncryption(all, new HashSet<Encryption>(Arrays.asList(Encryption.AES128, Encryption.AES128GCM, Encryption.AES256, Encryption.AES256GCM))));
        addListAlias(AESGCM, filterByEncryption(all, new HashSet<Encryption>(Arrays.asList(Encryption.AES128GCM, Encryption.AES256GCM))));
        addListAlias(CAMELLIA, filterByEncryption(all, new HashSet<Encryption>(Arrays.asList(Encryption.CAMELLIA128, Encryption.CAMELLIA256))));
        addListAlias(CAMELLIA128, filterByEncryption(all, Collections.singleton(Encryption.CAMELLIA128)));
        addListAlias(CAMELLIA256, filterByEncryption(all, Collections.singleton(Encryption.CAMELLIA256)));
        addListAlias(TRIPLE_DES, filterByEncryption(all, Collections.singleton(Encryption.TRIPLE_DES)));
        addListAlias(DES, filterByEncryption(all, Collections.singleton(Encryption.DES)));
        addListAlias(RC4, filterByEncryption(all, Collections.singleton(Encryption.RC4)));
        addListAlias(RC2, filterByEncryption(all, Collections.singleton(Encryption.RC2)));
        addListAlias(IDEA, filterByEncryption(all, Collections.singleton(Encryption.IDEA)));
        addListAlias(SEED, filterByEncryption(all, Collections.singleton(Encryption.SEED)));
        addListAlias(MD5, filterByMessageDigest(all, Collections.singleton(MessageDigest.MD5)));
        addListAlias(SHA1, filterByMessageDigest(all, Collections.singleton(MessageDigest.SHA1)));
        aliases.put(SHA, aliases.get(SHA1));
        addListAlias(SHA256, filterByMessageDigest(all, Collections.singleton(MessageDigest.SHA256)));
        addListAlias(SHA384, filterByMessageDigest(all, Collections.singleton(MessageDigest.SHA384)));
        addListAlias(aGOST, filterByAuthentication(all, new HashSet<Authentication>(Arrays.asList(Authentication.GOST01, Authentication.GOST94))));
        addListAlias(aGOST01, filterByAuthentication(all, Collections.singleton(Authentication.GOST01)));
        addListAlias(aGOST94, filterByAuthentication(all, Collections.singleton(Authentication.GOST94)));
        addListAlias(kGOST, filterByKeyExchange(all, Collections.singleton(KeyExchange.GOST)));
        addListAlias(GOST94, filterByMessageDigest(all, Collections.singleton(MessageDigest.GOST94)));
        addListAlias(GOST89MAC, filterByMessageDigest(all, Collections.singleton(MessageDigest.GOST89MAC)));
        addListAlias(PSK, filter(all, null, Collections.singleton(KeyExchange.PSK), Collections.singleton(Authentication.PSK), null, null, null));
        addListAlias(KRB5, filter(all, null, Collections.singleton(KeyExchange.KRB5), Collections.singleton(Authentication.KRB5), null, null, null));
        initialized = true;
        String defaultExpression = System.getProperty(DEFAULT_EXPRESSION_KEY, "ALL:!eNULL:!aNULL");
        addListAlias(DEFAULT, parse(defaultExpression));
        LinkedHashSet<Ciphers> complementOfDefault = new LinkedHashSet<Ciphers>(all);
        complementOfDefault.removeAll(aliases.get(DEFAULT));
        addListAlias(COMPLEMENTOFDEFAULT, complementOfDefault);
    }

    static void addListAlias(String alias, Set<Ciphers> ciphers) {
        aliases.put(alias, new ArrayList<Ciphers>(ciphers));
    }

    static void moveToEnd(final LinkedHashSet<Ciphers> ciphers, final String alias) {
        moveToEnd(ciphers, aliases.get(alias));
    }

    static void moveToEnd(final LinkedHashSet<Ciphers> ciphers, final Collection<Ciphers> toBeMovedCiphers) {
        ciphers.removeAll(toBeMovedCiphers);
        ciphers.addAll(toBeMovedCiphers);
    }

    static void add(final LinkedHashSet<Ciphers> ciphers, final String alias) {
        ciphers.addAll(aliases.get(alias));
    }

    static void remove(final LinkedHashSet<Ciphers> ciphers, final String alias) {
        ciphers.removeAll(aliases.get(alias));
    }

    static LinkedHashSet<Ciphers> strengthSort(final LinkedHashSet<Ciphers> ciphers) {
        /*
         * This routine sorts the ciphers with descending strength. The sorting
         * must keep the pre-sorted sequence, so we apply the normal sorting
         * routine as '+' movement to the end of the list.
         */
        Set<Integer> keySizes = new HashSet<Integer>();
        for (Ciphers cipher : ciphers) {
            keySizes.add(cipher.getStrength_bits());
        }
        List<Integer> strength_bits = new ArrayList<Integer>(keySizes);
        Collections.sort(strength_bits);
        Collections.reverse(strength_bits);
        final LinkedHashSet<Ciphers> result = new LinkedHashSet<Ciphers>(ciphers);
        for (int strength : strength_bits) {
            moveToEnd(result, filterByStrengthBits(ciphers, strength));
        }
        return result;
    }

    static LinkedHashSet<Ciphers> defaultSort(final LinkedHashSet<Ciphers> ciphers) {
        final LinkedHashSet<Ciphers> result = new LinkedHashSet<Ciphers>(ciphers.size());
        /* Now arrange all ciphers by preference: */

        /* Everything else being equal, prefer ephemeral ECDH over other key exchange mechanisms */
        result.addAll(filterByKeyExchange(ciphers, Collections.singleton(KeyExchange.EECDH)));
        /* AES is our preferred symmetric cipher */
        result.addAll(filterByEncryption(ciphers, new HashSet<Encryption>(Arrays.asList(Encryption.AES128, Encryption.AES128GCM,
                Encryption.AES256, Encryption.AES256GCM))));
        /* Temporarily enable everything else for sorting */
        result.addAll(ciphers);


        /* Low priority for MD5 */
        moveToEnd(result, filterByMessageDigest(result, Collections.singleton(MessageDigest.MD5)));

        /* Move anonymous ciphers to the end.  Usually, these will remain disabled.
         * (For applications that allow them, they aren't too bad, but we prefer
         * authenticated ciphers.) */
        moveToEnd(result, filterByAuthentication(result, Collections.singleton(Authentication.aNULL)));

        /* Move ciphers without forward secrecy to the end */
        moveToEnd(result, filterByAuthentication(result, Collections.singleton(Authentication.ECDH)));
        moveToEnd(result, filterByKeyExchange(result, Collections.singleton(KeyExchange.RSA)));
        moveToEnd(result, filterByKeyExchange(result, Collections.singleton(KeyExchange.PSK)));
        moveToEnd(result, filterByKeyExchange(result, Collections.singleton(KeyExchange.KRB5)));
        /* RC4 is sort-of broken -- move the the end */
        moveToEnd(result, filterByEncryption(result, Collections.singleton(Encryption.RC4)));
        return strengthSort(result);
    }

    static Set<Ciphers> filterByStrengthBits(Set<Ciphers> ciphers, int strength_bits) {
        Set<Ciphers> result = new LinkedHashSet<Ciphers>(ciphers.size());
        for (Ciphers cipher : ciphers) {
            if (cipher.getStrength_bits() == strength_bits) {
                result.add(cipher);
            }
        }
        return result;
    }

    static Set<Ciphers> filterByProtocol(Set<Ciphers> ciphers, Set<Protocol> protocol) {
        return filter(ciphers, protocol, null, null, null, null, null);
    }

    static Set<Ciphers> filterByKeyExchange(Set<Ciphers> ciphers, Set<KeyExchange> kx) {
        return filter(ciphers, null, kx, null, null, null, null);
    }

    static Set<Ciphers> filterByAuthentication(Set<Ciphers> ciphers, Set<Authentication> au) {
        return filter(ciphers, null, null, au, null, null, null);
    }

    static Set<Ciphers> filterByEncryption(Set<Ciphers> ciphers, Set<Encryption> enc) {
        return filter(ciphers, null, null, null, enc, null, null);
    }

    static Set<Ciphers> filterByEncryptionLevel(Set<Ciphers> ciphers, Set<EncryptionLevel> level) {
        return filter(ciphers, null, null, null, null, level, null);
    }

    static Set<Ciphers> filterByMessageDigest(Set<Ciphers> ciphers, Set<MessageDigest> mac) {
        return filter(ciphers, null, null, null, null, null, mac);
    }

    static Set<Ciphers> filter(Set<Ciphers> ciphers, Set<Protocol> protocol, Set<KeyExchange> kx,
            Set<Authentication> au, Set<Encryption> enc, Set<EncryptionLevel> level, Set<MessageDigest> mac) {
        Set<Ciphers> result = new LinkedHashSet<Ciphers>(ciphers.size());
        for (Ciphers cipher : ciphers) {
            if (protocol != null && protocol.contains(cipher.getProtocol())) {
                result.add(cipher);
            }
            if (kx != null && kx.contains(cipher.getKx())) {
                result.add(cipher);
            }
            if (au != null && au.contains(cipher.getAu())) {
                result.add(cipher);
            }
            if (enc != null && enc.contains(cipher.getEnc())) {
                result.add(cipher);
            }
            if (level != null && level.contains(cipher.getLevel())) {
                result.add(cipher);
            }
            if (mac != null && mac.contains(cipher.getMac())) {
                result.add(cipher);
            }
        }
        return result;
    }

    static LinkedHashSet<Ciphers> parse(String expression) {
        if (!initialized) {
            init();
        }
        String[] elements = expression.split(SEPARATOR);
        LinkedHashSet<Ciphers> ciphers = new LinkedHashSet<>();
        Set<Ciphers> removedCiphers = new HashSet<>();
        for (String element : elements) {
            if (element.startsWith(DELETE)) {
                String alias = element.substring(1);
                if (aliases.containsKey(alias)) {
                    remove(ciphers, alias);
                }
            } else if (element.startsWith(EXCLUDE)) {
                String alias = element.substring(1);
                if (aliases.containsKey(alias)) {
                    removedCiphers.addAll(aliases.get(alias));
                } else {
                    Messages.msg.unknowElementFoundDuringParsing(alias, expression);
                }
            } else if (element.startsWith(TO_END)) {
                String alias = element.substring(1);
                if (aliases.containsKey(alias)) {
                    moveToEnd(ciphers, alias);
                }
            } else if ("@STRENGTH".equals(element)) {
                strengthSort(ciphers);
                break;
            } else if (aliases.containsKey(element)) {
                add(ciphers, element);
            }
        }
        ciphers.removeAll(removedCiphers);
        return defaultSort(ciphers);
    }

    static List<String> convertForJSSE(Collection<Ciphers> ciphers) {
        List<String> result = new ArrayList<>(ciphers.size());
        for (Ciphers cipher : ciphers) {
            result.add(cipher.name());
        }
        return result;
    }

    /**
     * Parse the specified expression according to the OpenSSL syntax and returns a list of standard cipher names.
     * @param expression: the openssl expression to define a list of cipher.
     * @return the corresponding list of ciphers.
     */
    public static List<String> parseExpression(String expression) {
        return convertForJSSE(parse(expression));
    }

    static String displayResult(Set<Ciphers> ciphers, String separator) {
        if (ciphers.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(ciphers.size() * 16);
        for (Ciphers cipher : ciphers) {
            builder.append(cipher.getOpenSSLAlias());
            builder.append(separator);
        }
        return builder.toString().substring(0, builder.length() - 1);
    }
}
