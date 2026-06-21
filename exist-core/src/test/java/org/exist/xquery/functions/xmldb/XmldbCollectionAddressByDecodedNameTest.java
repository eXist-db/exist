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

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Surface 2 of the resource-naming contract (eXist-db/exist#6463, decision 5), address side: the
 * {@code xmldb:} collection lookup and listing functions must resolve a caller-supplied DECODED or
 * descriptor-derived literal collection path to the percent-encoded key that was actually stored --
 * the same normalization {@code fn:doc()}/{@code fn:collection()} already apply.
 *
 * <p>Regression for the EXPath repo collection naming reported against #6463: {@code Deployment}
 * mints a package's repo collection as {@code abbrev + "-" + version} via {@code XmldbURI.create}
 * (escape=true), so a package whose version contains {@code '{'}/{@code '}'} -- e.g. an unsubstituted
 * {@code "${app.version}"} -- installs under {@code /db/system/repo/<abbrev>-$%7B...%7D}. Before this
 * fix the address-side {@code xmldb:} functions resolved with escape=false
 * ({@code AnyURIValue.toXmldbURI()}), so {@code xmldb:collection-available} returned false and
 * {@code xmldb:get-child-resources} threw for the descriptor-derived literal
 * {@code /db/system/repo/<abbrev>-${app.version}} -- the collection was reachable only by its encoded
 * form. The shared {@code getLocalCollection} now normalizes with escape=true, matching the write
 * codec.</p>
 */
public class XmldbCollectionAddressByDecodedNameTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    /** A collection name whose '{'/'}' get percent-encoded at the write boundary, like an unsubstituted ${...} version. */
    private static final String AWKWARD = "addr-probe-${ver}";

    @Test
    public void collectionAvailableResolvesDecodedLiteralPath() throws XMLDBException {
        existEmbeddedServer.executeQuery(
                "declare variable $n external; xmldb:create-collection('/db', $n)",
                Map.of("n", new StringValue(AWKWARD)));

        // the stored key is percent-encoded ('{' -> %7B, '}' -> %7D, '$' kept literal)
        final String storedKey = URIUtils.encodeForURILenient(AWKWARD);
        assertTrue("precondition: the awkward name must encode to a distinct stored key", storedKey.contains("%7B"));

        // addressing by the encoded stored form always worked
        assertEquals("collection-available by the encoded stored form", "true", available("/db/" + storedKey));
        // addressing by the DECODED, descriptor-derived literal is what this fix restores
        assertEquals("collection-available by the decoded literal path", "true", available("/db/" + AWKWARD));
    }

    @Test
    public void getChildResourcesResolvesDecodedLiteralPath() throws XMLDBException {
        existEmbeddedServer.executeQuery(
                "declare variable $n external; xmldb:create-collection('/db', $n)",
                Map.of("n", new StringValue(AWKWARD)));
        existEmbeddedServer.executeQuery(
                "declare variable $c external; xmldb:store($c, 'doc.xml', document { <d/> })",
                Map.of("c", new StringValue("/db/" + AWKWARD)));

        // list children by the decoded literal path; before the fix this threw an XMLDBException
        final ResourceSet rs = existEmbeddedServer.executeQuery(
                "declare variable $c external; count(xmldb:get-child-resources($c))",
                Map.of("c", new StringValue("/db/" + AWKWARD)));
        assertEquals("1", rs.getResource(0).getContent());
    }

    private static String available(final String path) throws XMLDBException {
        final ResourceSet rs = existEmbeddedServer.executeQuery(
                "declare variable $p external; xmldb:collection-available($p)", Map.of("p", new StringValue(path)));
        return (String) rs.getResource(0).getContent();
    }
}
