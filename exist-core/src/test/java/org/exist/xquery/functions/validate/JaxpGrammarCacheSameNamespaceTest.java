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
package org.exist.xquery.functions.validate;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two schemas that share a target namespace must not be mixed up by the grammar cache.
 */
public class JaxpGrammarCacheSameNamespaceTest {

    @RegisterExtension
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private static String schema(final String type) {
        return """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:test:same-ns"
                           xmlns="urn:test:same-ns" elementFormDefault="qualified">
                    <xs:element name="root" type="xs:%s"/>
                </xs:schema>""".formatted(type);
    }

    private static String instance(final String schemaLocation) {
        return """
                <root xmlns="urn:test:same-ns" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:schemaLocation="urn:test:same-ns %s">abc</root>""".formatted(schemaLocation);
    }

    @BeforeAll
    public static void prepareResources() throws Exception {
        try (final Collection collection = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "same_namespace")) {
            ExistXmldbEmbeddedServer.storeResource(collection, "as-string.xsd", schema("string").getBytes(UTF_8));
            ExistXmldbEmbeddedServer.storeResource(collection, "as-integer.xsd", schema("integer").getBytes(UTF_8));
            ExistXmldbEmbeddedServer.storeResource(collection, "string.xml", instance("as-string.xsd").getBytes(UTF_8));
            ExistXmldbEmbeddedServer.storeResource(collection, "integer.xml", instance("as-integer.xsd").getBytes(UTF_8));
        }
    }

    @BeforeEach
    public void clearGrammarCache() throws XMLDBException {
        existEmbeddedServer.executeQuery("validation:clear-grammar-cache()");
    }

    private String status(final String document) throws XMLDBException {
        final ResourceSet results = existEmbeddedServer.executeQuery(
                "string(validation:jaxp-report(xs:anyURI('/db/same_namespace/" + document + "'), true())/status)");
        assertEquals(1, results.getSize());
        return (String) results.getResource(0).getContent();
    }

    @Test
    public void secondSchemaIsNotReplacedByTheFirstOneInTheCache() throws XMLDBException {
        assertEquals("valid", status("string.xml"));
        // 'abc' is not an xs:integer, even though a schema for the same namespace is already cached
        assertEquals("invalid", status("integer.xml"));
    }

    @Test
    public void sameSchemaIsStillServedFromTheCache() throws XMLDBException {
        assertEquals("valid", status("string.xml"));
        assertEquals("valid", status("string.xml"));
    }
}
