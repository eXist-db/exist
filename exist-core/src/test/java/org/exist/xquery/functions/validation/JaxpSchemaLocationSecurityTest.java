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
package org.exist.xquery.functions.validation;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Targeted white-box test for {@code Jaxp.isXsd11Schema(String, String, String)}'s same-origin
 * (scheme + authority) restriction -- the up-front XSD 1.1 detection peek must only ever follow
 * a {@code schemaLocation} hint that resolves to the exact same origin as the instance document's
 * own base URI, since the hint is document-author-controlled and the peek runs unconditionally,
 * before any catalog/permission check would otherwise govern the fetch.
 *
 * <p>Tests {@code Jaxp.isXsd11Schema} directly rather than through the full {@code
 * validation:jaxp()} pipeline: going through the pipeline would also
 * exercise the (separate, pre-existing, unrelated) default Xerces resolution that runs when the
 * peek declines and falls through -- which would independently attempt the same kind of fetch,
 * confounding any attempt to observe whether the peek itself made a network call.</p>
 */
public class JaxpSchemaLocationSecurityTest {

    private static Path tempDir;
    private static String fileBaseUri;

    private static final String XSD_1_1_SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning" vc:minVersion="1.1"/>""";

    private static final String XSD_1_0_SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>""";

    @BeforeClass
    public static void setup() throws Exception {
        tempDir = Files.createTempDirectory("jaxp-security-test");
        final Path instance = tempDir.resolve("instance.xml");
        Files.writeString(instance, "<root/>");
        Files.writeString(tempDir.resolve("schema11.xsd"), XSD_1_1_SCHEMA);
        Files.writeString(tempDir.resolve("schema10.xsd"), XSD_1_0_SCHEMA);
        fileBaseUri = instance.toUri().toString();
    }

    @AfterClass
    public static void teardown() throws Exception {
        Files.deleteIfExists(tempDir.resolve("instance.xml"));
        Files.deleteIfExists(tempDir.resolve("schema11.xsd"));
        Files.deleteIfExists(tempDir.resolve("schema10.xsd"));
        Files.deleteIfExists(tempDir);
    }

    @Test
    public void sameOriginRelativeXsd11SchemaIsDetected() {
        assertTrue(Jaxp.isXsd11Schema("test-subject", fileBaseUri, "schema11.xsd"));
    }

    @Test
    public void sameOriginRelativeXsd10SchemaIsNotDetectedAsXsd11() {
        assertFalse(Jaxp.isXsd11Schema("test-subject", fileBaseUri, "schema10.xsd"));
    }

    @Test(timeout = 5000)
    public void crossOriginHttpLocationIsRefused() {
        // A real instance would never have a `file://` base URI reachable from an unprivileged
        // caller (only Java-object-backed items do, which already requires elevated capability) --
        // this is just the most convenient same-scheme baseline to contrast against. The case that
        // matters is: whatever the base URI's origin, an absolute, different-origin location must
        // never be followed.
        assertFalse(Jaxp.isXsd11Schema("test-subject", fileBaseUri, "http://203.0.113.1:1/evil.xsd"));
    }

    @Test(timeout = 5000)
    public void crossOriginHttpLocationIsRefusedForDatabaseBaseUri() {
        // The realistic, unprivileged case: a document stored in the database (xmldb:// base URI)
        // with an absolute http:// schemaLocation hint pointing out to an attacker-controlled host.
        assertFalse(Jaxp.isXsd11Schema("test-subject", "xmldb://db/test/instance.xml", "http://203.0.113.1:1/evil.xsd"));
    }

    @Test(timeout = 5000)
    public void crossHostXmldbLocationIsRefused() {
        // An absolute xmldb:// location naming a different host is not "same scheme" enough --
        // XmldbURL.isEmbedded() treats a non-empty host as a remote XML-RPC target
        // (EmbeddedURLConnection -> XmlrpcInputStream), so this must be refused too.
        assertFalse(Jaxp.isXsd11Schema("test-subject", "xmldb://db/test/instance.xml", "xmldb://203.0.113.1:1/db/evil.xsd"));
    }
}
