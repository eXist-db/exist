/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
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
package org.exist.xquery.functions.system;

import org.exist.TestUtils;
import org.exist.management.client.JMXtoXML;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.EXistXQueryService;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.exist.xmldb.XmldbURI.LOCAL_DB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GetJmxTokenTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer dba = new ExistXmldbEmbeddedServer(false, true, true);

    @Test
    public void dbaCanGetToken() throws XMLDBException {
        final ResourceSet result = dba.executeQuery("system:get-jmx-token()");
        assertEquals(1, result.getSize());

        final String token = (String) result.getResource(0).getContent();
        assertFalse(token.isBlank());
    }

    @Test
    public void tokenIsStableAcrossCalls() throws XMLDBException {
        final ResourceSet first = dba.executeQuery("system:get-jmx-token()");
        final ResourceSet second = dba.executeQuery("system:get-jmx-token()");

        assertThat((String) second.getResource(0).getContent(), equalTo((String) first.getResource(0).getContent()));
    }

    @Test
    public void tokenFileChangesAreReflectedOnNextCall() throws XMLDBException, IOException {
        final ResourceSet before = dba.executeQuery("system:get-jmx-token()");
        final String originalToken = (String) before.getResource(0).getContent();

        // Locate the actual on-disk token file the running instance is using, via the same
        // DiskUsage MBean lookup JMXTokenProvider itself relies on.
        final JMXtoXML client = new JMXtoXML();
        client.connect();
        final Path tokenFile = Path.of(client.getDataDir()).resolve("jmxservlet.token");
        assertTrue(Files.exists(tokenFile));

        // Overwrite it with a new token while the instance is still running.
        final String replacementToken = "replacement-" + originalToken;
        try {
            writeToken(tokenFile, replacementToken);

            final ResourceSet after = dba.executeQuery("system:get-jmx-token()");
            final String tokenAfterEdit = (String) after.getResource(0).getContent();

            assertThat(tokenAfterEdit, equalTo(replacementToken));
            assertThat(tokenAfterEdit, not(equalTo(originalToken)));
        } finally {
            // Leave the file as it was so other tests in this class see a consistent token.
            writeToken(tokenFile, originalToken);
        }
    }

    private static void writeToken(final Path tokenFile, final String token) throws IOException {
        final Properties props = new Properties();
        props.setProperty("token", token);
        try (final var os = Files.newOutputStream(tokenFile)) {
            props.store(os, null);
        }
    }

    @Test
    public void guestIsDenied() throws XMLDBException {
        final Collection guestRoot = DatabaseManager.getCollection(LOCAL_DB, TestUtils.GUEST_DB_USER, TestUtils.GUEST_DB_PWD);
        final EXistXQueryService guestQueryService = guestRoot.getService(EXistXQueryService.class);

        final XMLDBException e = assertThrows(XMLDBException.class,
                () -> guestQueryService.query("system:get-jmx-token()"));

        assertThat(e.getMessage(), containsString("DBA"));
    }
}
