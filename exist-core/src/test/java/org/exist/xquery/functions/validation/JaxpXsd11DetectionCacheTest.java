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

import org.junit.After;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests that {@code Jaxp.isXsd11Schema}'s result cache (a) actually caches -- a second call for
 * the same Subject and resolved schema URI doesn't re-read the (changed) underlying content --
 * (b) is fully cleared by {@code Jaxp.clearXsd11DetectionCache()}, the hook {@code
 * validation:clear-grammar-cache()} calls (see {@link GrammarTooling}), and (c) is scoped per
 * Subject, so a different Subject querying the same resolved URI doesn't observe a cached answer
 * populated by someone else's fetch.
 */
public class JaxpXsd11DetectionCacheTest {

    private static final String XSD_1_1_SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning" vc:minVersion="1.1"/>""";

    private static final String XSD_1_0_SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>""";

    @After
    public void clearCache() {
        Jaxp.clearXsd11DetectionCache();
    }

    @Test
    public void cachesResultAcrossCallsAndClearCacheInvalidatesIt() throws Exception {
        final Path tempDir = Files.createTempDirectory("jaxp-xsd11-cache-test");
        try {
            final Path instance = tempDir.resolve("instance.xml");
            Files.writeString(instance, "<root/>");
            final Path schema = tempDir.resolve("schema.xsd");
            Files.writeString(schema, XSD_1_1_SCHEMA);

            final String baseUri = instance.toUri().toString();

            // First call: reads the real (XSD 1.1) content from disk.
            assertTrue(Jaxp.isXsd11Schema("subject-a", baseUri, "schema.xsd"));

            // Flip the on-disk content to XSD 1.0 without going through the cache -- if the second
            // call is actually served from cache, it must still report the stale (cached) "true",
            // not re-read this new content.
            Files.writeString(schema, XSD_1_0_SCHEMA);
            assertTrue("second call should be served from cache, not re-read the changed file",
                    Jaxp.isXsd11Schema("subject-a", baseUri, "schema.xsd"));

            // Clearing the cache (what validation:clear-grammar-cache() does) must make the next
            // call re-read the file and observe the now-current (XSD 1.0) content.
            Jaxp.clearXsd11DetectionCache();
            assertFalse("after clearing the cache, the now-current XSD 1.0 content must be observed",
                    Jaxp.isXsd11Schema("subject-a", baseUri, "schema.xsd"));
        } finally {
            Files.deleteIfExists(tempDir.resolve("instance.xml"));
            Files.deleteIfExists(tempDir.resolve("schema.xsd"));
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void cacheIsScopedPerSubject() throws Exception {
        final Path tempDir = Files.createTempDirectory("jaxp-xsd11-cache-subject-test");
        try {
            final Path instance = tempDir.resolve("instance.xml");
            Files.writeString(instance, "<root/>");
            final Path schema = tempDir.resolve("schema.xsd");
            Files.writeString(schema, XSD_1_1_SCHEMA);

            final String baseUri = instance.toUri().toString();

            // subject-a's call populates the cache for this resolved URI.
            assertTrue(Jaxp.isXsd11Schema("subject-a", baseUri, "schema.xsd"));

            // Delete the underlying file so any call that actually has to read it (i.e. a cache
            // miss) fails/returns false -- if subject-b's call were wrongly served from
            // subject-a's cache entry, it would still report "true" despite never reading anything.
            Files.delete(schema);
            assertFalse("a different Subject must not observe a cache entry populated by another Subject's fetch",
                    Jaxp.isXsd11Schema("subject-b", baseUri, "schema.xsd"));
        } finally {
            Files.deleteIfExists(tempDir.resolve("instance.xml"));
            Files.deleteIfExists(tempDir.resolve("schema.xsd"));
            Files.deleteIfExists(tempDir);
        }
    }
}
