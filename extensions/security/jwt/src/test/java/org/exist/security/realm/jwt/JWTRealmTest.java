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
                    "        <domain>test</domain>\n" +
                    "        <search>\n" +
                    "            <account>\n" +
                    "                <name>$.sub</name>\n" +
                    "                <metadata-search-attribute key=\"http://axschema.org/namePerson\">$.name</metadata-search-attribute>\n" +
                    "                <metadata-search-attribute key=\"http://axschema.org/namePerson/friendly\">$.nickname</metadata-search-attribute>\n" +
                    "                <metadata-search-attribute key=\"http://axschema.org/contact/email\">$.email</metadata-search-attribute>\n" +
                    "            </account>\n" +
                    "            <group>\n" +
                    "                <name>$.['https://example.com/auth'].groups[*]</name>\n" +
                    "            </group>\n" +
                    "        </search>\n" +
                    "        <transformation>\n" +
                    "            <add-group>guest</add-group>\n" +
                    "        </transformation>\n" +
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
        final String jwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InVvUGlBY1o4RExSRkRVZFBnVThZZCJ9.eyJodHRwczovL2V4YW1wbGUuY29tL2F1dGgiOnsiZ3JvdXBzIjpbIkNETyJdLCJyb2xlcyI6WyJFZGl0b3IiXX0sIm5pY2tuYW1lIjoibG9yZW4uY2FobGFuZGVyIiwibmFtZSI6IkxvcmVuIENhaGxhbmRlciIsInBpY3R1cmUiOiJodHRwczovL3MuZ3JhdmF0YXIuY29tL2F2YXRhci9hNzA1YWRiM2Q1ZDg1MzBjMzVjNDFhOWRlMjYwY2QzYz9zPTQ4MCZyPXBnJmQ9aHR0cHMlM0ElMkYlMkZjZG4uYXV0aDAuY29tJTJGYXZhdGFycyUyRmxvLnBuZyIsInVwZGF0ZWRfYXQiOiIyMDIyLTA1LTA0VDE4OjE0OjI2LjQ4MloiLCJlbWFpbCI6ImxvcmVuLmNhaGxhbmRlckBlYXN5bWV0YWh1Yi5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiaXNzIjoiaHR0cHM6Ly9kZXYtMW5yYWJ2b3kudXMuYXV0aDAuY29tLyIsInN1YiI6ImF1dGgwfDYyNjAyOWRhYjU5OTViMDA2ODU0MDk1MyIsImF1ZCI6InNSWDV6WDB5bFdnSFo1T3RScjEzN3g1Ulh5Q0NPMTlMIiwiaWF0IjoxNjUxNzcwNDY1LCJleHAiOjE2NTE4MDY0NjUsIm5vbmNlIjoiVlpQME5JMUxtdzhZQ3QtcmFkUWR6ZEtzeFF0NUt0dWxvQUJVQzVRYW43ZyJ9.q_0PQEz_QF8IwFdarvsp9SVchPNWXE5cCBmN6BajimWiZnVfVZCMUQzAzuoCW23QdD7WnUrg9gBxB8d33OXa_l1y6Y2titCqUwG_VIXyVmEkSruIi6Sp5dLWkE5-VNgiR8K69YazJqSYc0_rFzKhigbbMddJKdmBl8MR0H0QjsSdllHY9mocasvX0GBO10pfoSCIp58rCdH27p5jlZw9u3dZaD7mTxqnxaQ5pGHWfguwNIyA7lWtz19HSg-Fhf0an55qKr84sUm1sAgnsVwczNb76RjCWeSXVKJex6parkl04i3SObj6jzE-i9VnZ8ddlP5q1N1Y74mDDi0fUpsg6w@test";
        Account account = null;
        try {
            account = realm.authenticate(jwt, jwt);
        } catch (AuthenticationException e) {
            fail(e.getMessage());
        }

        assertNotNull(account);
    }

}
