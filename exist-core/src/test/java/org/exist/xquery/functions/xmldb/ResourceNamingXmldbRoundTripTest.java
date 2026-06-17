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
package org.exist.xquery.functions.xmldb;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xquery.util.URIUtils;
import org.exist.xquery.value.StringValue;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Live round-trip test for surface 1 of the resource-naming contract prototype
 * (eXist-db/exist#6463): the {@code xmldb:} write boundary. It stores an awkward-name corpus
 * through the real {@code xmldb:store} / {@code xmldb:create-collection}, reads the stored keys
 * back via {@code xmldb:get-child-resources} / {@code -collections}, and asserts each stored key is
 * exactly the canonical lenient form {@link URIUtils#encodeForURILenient(String)} produces and that
 * it decodes back to the original display name. Distinct names must not collide, and a raw space
 * must now be accepted (decision 3).
 *
 * <p>This complements the pure-codec {@code ResourceNameCodecTest}: that proves the codec; this
 * proves the {@code xmldb:} functions actually apply it against a real database.</p>
 */
public class ResourceNamingXmldbRoundTripTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    /**
     * Decoded display names the user types. Each must round-trip; none may collide.
     */
    private static final String[] NAMES = {
            "plain.xml",
            "hello world.xml",       // raw space (decision 3)
            "café.xml",
            "naïve.xml",
            "it's.xml",              // sub-delimiter, kept literal
            "a+b.xml",
            "Jack&Jill.xml",
            "report(2026).xml",
            "50%.xml",               // literal percent (decision 2)
            "a%20b.xml",             // literal percent before hex
            "%25.xml",
            "100%done%.xml"
    };

    @Test
    public void storeRoundTripsEveryName() throws XMLDBException {
        final String col = "/db/naming-rt-resources";
        existEmbeddedServer.executeQuery("xmldb:create-collection('/db', 'naming-rt-resources')");

        for (final String name : NAMES) {
            // pass the DECODED name; the store boundary must encode it exactly once
            existEmbeddedServer.executeQuery(
                    "declare variable $name external; declare variable $col external; "
                            + "xmldb:store($col, $name, document { <doc/> })",
                    // wrap in StringValue so the harness does not entity-expand '&' in the name
                    Map.of("name", new StringValue(name), "col", col));
        }

        final Set<String> storedKeys = childNames(existEmbeddedServer.executeQuery(
                "declare variable $col external; xmldb:get-child-resources($col)", Map.of("col", col)));

        assertCorpusRoundTrips(storedKeys, "resource");
    }

    @Test
    public void createCollectionRoundTripsEveryName() throws XMLDBException {
        final String parent = "/db/naming-rt-collections";
        existEmbeddedServer.executeQuery("xmldb:create-collection('/db', 'naming-rt-collections')");

        for (final String name : NAMES) {
            existEmbeddedServer.executeQuery(
                    "declare variable $name external; declare variable $parent external; "
                            + "xmldb:create-collection($parent, $name)",
                    Map.of("name", new StringValue(name), "parent", parent));
        }

        final Set<String> storedKeys = childNames(existEmbeddedServer.executeQuery(
                "declare variable $parent external; xmldb:get-child-collections($parent)", Map.of("parent", parent)));

        assertCorpusRoundTrips(storedKeys, "collection");
    }

    private static void assertCorpusRoundTrips(final Set<String> storedKeys, final String kind) {
        // every name produced exactly the canonical lenient stored form
        for (final String name : NAMES) {
            final String expectedStored = URIUtils.encodeForURILenient(name);
            assertTrue("missing canonical stored " + kind + " " + expectedStored + " for name " + name,
                    storedKeys.contains(expectedStored));
        }
        // no collisions: distinct names -> distinct stored keys
        assertEquals("distinct " + kind + " names must not collide in storage", NAMES.length, storedKeys.size());
        // every stored key decodes back to one of the original display names
        for (final String key : storedKeys) {
            final String display = URIUtils.decodeForURI(key);
            assertTrue("stored " + kind + " key " + key + " did not decode to a known name (got " + display + ")",
                    contains(display));
        }
    }

    private static Set<String> childNames(final ResourceSet rs) throws XMLDBException {
        final Set<String> names = new LinkedHashSet<>();
        for (long i = 0; i < rs.getSize(); i++) {
            names.add((String) rs.getResource(i).getContent());
        }
        return names;
    }

    private static boolean contains(final String display) {
        for (final String name : NAMES) {
            if (name.equals(display)) {
                return true;
            }
        }
        return false;
    }
}
