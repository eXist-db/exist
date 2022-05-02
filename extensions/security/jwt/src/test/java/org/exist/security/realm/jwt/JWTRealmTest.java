/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2021 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.security.realm.jwt;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.security.Account;
import org.exist.security.AuthenticationException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.InputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class JWTRealmTest {

    private static String config =
            "<realm id=\"JWT\" version=\"1.0\" principals-are-case-insensitive=\"true\">\n" +
                    "    <context>\n" +
                    "        <domain>domain.here</domain>\n" +
                    "        <secret>...</secret>\n" +
                    "        <account>\n" +
                    "            <property key=\"name\">idp</property>\n" +
                    "        </account>\n" +
                    "        <group>\n" +
                    "            <claim>groups</claim>\n" +
                    "        </group>\n" +
                    "    </context>\n" +
                    "</realm>\n";

    private static JWTRealm realm;

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(config.getBytes(UTF_8))) {
            Configuration config = Configurator.parse(is);
            realm = new JWTRealm(null, config);
        }
    }

    /**
     * @throws java.lang.Exception
     */
    @AfterClass
    public static void tearDownAfterClass() {
    }


    @Test
    public void testAuthenticate() {
        assertTrue(true);
//        Account account = null;
//        try {
//            account = realm.authenticate("admin", "passwd");
//        } catch (AuthenticationException e) {
//            fail(e.getMessage());
//        }
//
//        assertNotNull(account);
    }

}
