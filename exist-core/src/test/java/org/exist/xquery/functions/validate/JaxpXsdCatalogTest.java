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
import org.exist.util.io.InputStreamUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import static org.exist.collections.CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE;
import static org.exist.samples.Samples.SAMPLES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.xmlunit.matchers.EvaluateXPathMatcher.hasXPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;

import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests for the validation:jaxp() function with Catalog (resolvers).
 * 
 * @author dizzzz@exist-db.org
 */
public class JaxpXsdCatalogTest {

    @RegisterExtension
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String noValidation = "<?xml version='1.0'?>" +
            "<collection xmlns='http://exist-db.org/collection-config/1.0'>" +
            "    <validation mode='no'/>" +
            "</collection>";

    @BeforeAll
    public static void prepareResources() throws XMLDBException, IOException {

        // Switch off validation
        try (Collection conf = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "system/config/db/parse")) {
            ExistXmldbEmbeddedServer.storeResource(conf, DEFAULT_COLLECTION_CONFIG_FILE, noValidation.getBytes());
        }

        try (Collection schemasCollection = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "parse/schemas")) {

            try (final InputStream is = SAMPLES.getSample("validation/parse/schemas/MyNameSpace.xsd")) {
                assertNotNull(is);
                ExistXmldbEmbeddedServer.storeResource(schemasCollection, "MyNameSpace.xsd", InputStreamUtil.readAll(is));
            }

            try (final InputStream is = SAMPLES.getSample("validation/parse/schemas/AnotherNamespace.xsd")) {
                assertNotNull(is);
                ExistXmldbEmbeddedServer.storeResource(schemasCollection, "AnotherNamespace.xsd", InputStreamUtil.readAll(is));
            }

            // No schemaLocation hint at all -- directory-search resolves purely by the root
            // element's namespace, same as MyNameSpace.xsd/valid.xml above. xs:assert only exists
            // in XSD 1.1, so this proves the XSD 1.1 retry/up-front pipeline is reachable through
            // SearchResourceResolver (item 6), not just through an explicit schemaLocation hint.
            try (final InputStream is = SAMPLES.getSample("validation/parse/schemas/searched-xsd11.xsd")) {
                assertNotNull(is);
                ExistXmldbEmbeddedServer.storeResource(schemasCollection, "searched-xsd11.xsd", InputStreamUtil.readAll(is));
            }
        }

        try (Collection parseCollection = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "parse")) {
            try (final InputStream is = SAMPLES.getSample("validation/parse/catalog.xml")) {
                assertNotNull(is);
                ExistXmldbEmbeddedServer.storeResource(parseCollection, "catalog.xml", InputStreamUtil.readAll(is));
            }
        }

        try (Collection instanceCollection = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "parse/instance")) {

            try (final InputStream is = SAMPLES.getSample("validation/parse/instance/valid.xml")) {
                assertNotNull(is);
                ExistXmldbEmbeddedServer.storeResource(instanceCollection, "valid.xml", InputStreamUtil.readAll(is));
            }

            try (final InputStream is = SAMPLES.getSample("validation/parse/instance/invalid.xml")) {
                assertNotNull(is);
                ExistXmldbEmbeddedServer.storeResource(instanceCollection, "invalid.xml", InputStreamUtil.readAll(is));
            }

            try (final InputStream is = SAMPLES.getSample("validation/parse/instance/searched-xsd11-valid.xml")) {
                assertNotNull(is);
                ExistXmldbEmbeddedServer.storeResource(instanceCollection, "searched-xsd11-valid.xml", InputStreamUtil.readAll(is));
            }

            try (final InputStream is = SAMPLES.getSample("validation/parse/instance/searched-xsd11-invalid.xml")) {
                assertNotNull(is);
                ExistXmldbEmbeddedServer.storeResource(instanceCollection, "searched-xsd11-invalid.xml", InputStreamUtil.readAll(is));
            }
        }
    }

    @BeforeEach
    public void clearGrammarCache() throws XMLDBException {
        final ResourceSet results = existEmbeddedServer.executeQuery("validation:clear-grammar-cache()");
        results.getResource(0).getContent();
    }

    @org.junit.jupiter.api.Test
    public void xsdStoredCatalogValid() throws XMLDBException, SAXException, IOException {
        final String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/valid.xml'), false()," +
                "doc('/db/parse/catalog.xml') )";
        assertThat(QueryResults.single(existEmbeddedServer, query), hasXPath("//status/text()", equalTo("valid")));
    }

    @org.junit.jupiter.api.Test
    public void xsdStoredCatalogInvalid() throws XMLDBException, SAXException, IOException {
        final String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/invalid.xml'), false()," +
                "doc('/db/parse/catalog.xml') )";
        assertThat(QueryResults.single(existEmbeddedServer, query), hasXPath("//status/text()", equalTo("invalid")));
    }

    @org.junit.jupiter.api.Test
    public void xsdAnyURICatalogValid() throws XMLDBException, SAXException, IOException {
        final String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/valid.xml'), false()," +
                "xs:anyURI('/db/parse/catalog.xml') )";
        assertThat(QueryResults.single(existEmbeddedServer, query), hasXPath("//status/text()", equalTo("valid")));
    }

    @org.junit.jupiter.api.Test
    public void xsdAnyURICatalogInvalid() throws XMLDBException, SAXException, IOException {
        final String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/invalid.xml'), false()," +
                "xs:anyURI('/db/parse/catalog.xml') )";
        assertThat(QueryResults.single(existEmbeddedServer, query), hasXPath("//status/text()", equalTo("invalid")));
    }

    @org.junit.jupiter.api.Test
    public void xsdSearchedValid() throws XMLDBException, SAXException, IOException {
        final String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/valid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";
        assertThat(QueryResults.single(existEmbeddedServer, query), hasXPath("//status/text()", equalTo("valid")));
    }

    @org.junit.jupiter.api.Test
    public void xsdSearchedInvalid() throws XMLDBException, SAXException, IOException {
        final String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/invalid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";
        assertThat(QueryResults.single(existEmbeddedServer, query), hasXPath("//status/text()", equalTo("invalid")));
    }
    
    // test boolean function
    @org.junit.jupiter.api.Test
    public void xsd_searched_valid_boolean() throws XMLDBException {
        final String query = "validation:jaxp( " +
                "doc('/db/parse/instance/valid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";
        assertEquals("true", existEmbeddedServer.executeOneValue(query));
    }
    
    // test boolean function
    @org.junit.jupiter.api.Test
    public void xsd_searched_invalid_boolean() throws XMLDBException {
        final String query = "validation:jaxp( " +
                "doc('/db/parse/instance/invalid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";
        assertEquals("false", existEmbeddedServer.executeOneValue(query));
    }
    
    // test parse function
    @org.junit.jupiter.api.Test
    public void xsdSearchedParseValid() throws XMLDBException {
        final String query = "validation:jaxp-parse( " +
                "doc('/db/parse/instance/valid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";
        final String r = existEmbeddedServer.executeOneValue(query);
        assertThat(r, hasXPath("//Y", equalTo("2006-05-04T18:13:51.0Z")));
    }
    
    // test parse function
    @org.junit.jupiter.api.Test
    public void xsdSearchedParseInvalid() throws XMLDBException {
        final String query = "validation:jaxp-parse( " +
                "doc('/db/parse/instance/invalid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";
        final String r = existEmbeddedServer.executeOneValue(query);
        assertThat(r, hasXPath("//Y", equalTo("2006-05-04T18:13:51.0Z")));
    }

    // Directory-search catalog + XSD 1.1 schema, resolved purely by namespace (no
    // schemaLocation hint on the instance). Proves item 6: SearchResourceResolver's
    // LSResourceResolver support makes directory-search catalogs work with the XSD 1.1
    // validator pipeline too, not just the default SAX pipeline.
    @org.junit.jupiter.api.Test
    public void xsd11SearchedValid() throws XMLDBException, SAXException, IOException {
        final String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/searched-xsd11-valid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";
        assertThat(QueryResults.single(existEmbeddedServer, query), hasXPath("//status/text()", equalTo("valid")));
    }

    @org.junit.jupiter.api.Test
    public void xsd11SearchedInvalid() throws XMLDBException, SAXException, IOException {
        final String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/searched-xsd11-invalid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";
        assertThat(QueryResults.single(existEmbeddedServer, query), hasXPath("//status/text()", equalTo("invalid")));
    }
}
