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

import org.custommonkey.xmlunit.exceptions.XpathException;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.io.InputStreamUtil;
import org.junit.*;

import static org.exist.collections.CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE;
import static org.junit.Assert.*;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;
import static org.exist.samples.Samples.SAMPLES;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 * Tests for the validation:jaxp() function with Catalog (resolvers).
 * 
 * @author dizzzz@exist-db.org
 */
public class JaxpXsdCatalogTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String noValidation = "<?xml version='1.0'?>" +
            "<collection xmlns='http://exist-db.org/collection-config/1.0'>" +
            "    <validation mode='no'/>" +
            "</collection>";

    // No schemaLocation hint at all -- directory-search resolves purely by the root
    // element's namespace, same as MyNameSpace.xsd/valid.xml above. xs:assert only exists
    // in XSD 1.1, so this proves the XSD 1.1 retry/up-front pipeline is reachable through
    // SearchResourceResolver (item 6), not just through an explicit schemaLocation hint.
    private static final String xsd11SearchedSchema =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' " +
            "xmlns:vc='http://www.w3.org/2007/XMLSchema-versioning' vc:minVersion='1.1' " +
            "xmlns='urn:jaxp-test:searched-xsd11' targetNamespace='urn:jaxp-test:searched-xsd11' " +
            // xs:assert's XPath defaults unprefixed names to NO namespace unless told otherwise --
            // needed here since elementFormDefault='qualified' puts value1/value2 in the target namespace.
            "elementFormDefault='qualified' xpathDefaultNamespace='##targetNamespace'>" +
            "<xs:element name='root'>" +
            "<xs:complexType>" +
            "<xs:sequence>" +
            "<xs:element name='value1' type='xs:integer'/>" +
            "<xs:element name='value2' type='xs:integer'/>" +
            "</xs:sequence>" +
            "<xs:assert test='value2 gt value1'/>" +
            "</xs:complexType>" +
            "</xs:element>" +
            "</xs:schema>";

    private static final String xsd11SearchedValidInstance =
            "<root xmlns='urn:jaxp-test:searched-xsd11'><value1>20</value1><value2>30</value2></root>";

    private static final String xsd11SearchedInvalidInstance =
            "<root xmlns='urn:jaxp-test:searched-xsd11'><value1>30</value1><value2>20</value2></root>";

    @BeforeClass
    public static void prepareResources() throws XMLDBException, IOException, URISyntaxException {

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

            ExistXmldbEmbeddedServer.storeResource(schemasCollection, "searched-xsd11.xsd", xsd11SearchedSchema.getBytes());
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

            ExistXmldbEmbeddedServer.storeResource(instanceCollection, "searched-xsd11-valid.xml", xsd11SearchedValidInstance.getBytes());
            ExistXmldbEmbeddedServer.storeResource(instanceCollection, "searched-xsd11-invalid.xml", xsd11SearchedInvalidInstance.getBytes());
        }
    }

    @Before
    public void clearGrammarCache() throws XMLDBException {
        final ResourceSet results = existEmbeddedServer.executeQuery("validation:clear-grammar-cache()");
        results.getResource(0).getContent();
    }

    @Test
    public void xsd_stored_catalog_valid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/valid.xml'), false()," +
                "doc('/db/parse/catalog.xml') )";
        executeAndEvaluate(query,"valid");
    }

    @Test
    public void xsd_stored_catalog_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/invalid.xml'), false()," +
                "doc('/db/parse/catalog.xml') )";
        executeAndEvaluate(query,"invalid");
    }

    @Test
    public void xsd_anyURI_catalog_valid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/valid.xml'), false()," +
                "xs:anyURI('/db/parse/catalog.xml') )";
        executeAndEvaluate(query,"valid");
    }

    @Test
    public void xsd_anyURI_catalog_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/invalid.xml'), false()," +
                "xs:anyURI('/db/parse/catalog.xml') )";
        executeAndEvaluate(query,"invalid");
    }

    @Test
    public void xsd_searched_valid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/valid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";
        executeAndEvaluate(query,"valid");
    }

    @Test
    public void xsd_searched_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/invalid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";
        executeAndEvaluate(query,"invalid");
    }
    
    // test boolean function
    @Test
    public void xsd_searched_valid_boolean() throws XMLDBException {
        final String query = "validation:jaxp( " +
                "doc('/db/parse/instance/valid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";
        assertEquals("true", existEmbeddedServer.executeOneValue(query));
    }
    
    // test boolean function
    @Test
    public void xsd_searched_invalid_boolean() throws XMLDBException {
        final String query = "validation:jaxp( " +
                "doc('/db/parse/instance/invalid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";
        assertEquals("false", existEmbeddedServer.executeOneValue(query));
    }
    
    // test parse function
    @Test
    public void xsd_searched_parse_valid() throws SAXException, IOException, XpathException, XMLDBException {
        final String query = "validation:jaxp-parse( " +
                "doc('/db/parse/instance/valid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";
        final String r = existEmbeddedServer.executeOneValue(query);
        assertXpathEvaluatesTo("2006-05-04T18:13:51.0Z", "//Y", r);
    }
    
    // test parse function
    @Test
    public void xsd_searched_parse_invalid() throws SAXException, IOException, XpathException, XMLDBException {
        final String query = "validation:jaxp-parse( " +
                "doc('/db/parse/instance/invalid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";
        final String r = existEmbeddedServer.executeOneValue(query);
        assertXpathEvaluatesTo("2006-05-04T18:13:51.0Z", "//Y", r);
    }

    // Directory-search catalog + XSD 1.1 schema, resolved purely by namespace (no
    // schemaLocation hint on the instance). Proves item 6: SearchResourceResolver's
    // LSResourceResolver support makes directory-search catalogs work with the XSD 1.1
    // validator pipeline too, not just the default SAX pipeline.
    @Test
    public void xsd11SearchedValid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/searched-xsd11-valid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";
        executeAndEvaluate(query, "valid");
    }

    @Test
    public void xsd11SearchedInvalid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/searched-xsd11-invalid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";
        executeAndEvaluate(query, "invalid");
    }

    private void executeAndEvaluate(final String query, final String expectedValue) throws XMLDBException, SAXException, IOException, XpathException {
        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());
        final String r = (String) results.getResource(0).getContent();
        assertXpathEvaluatesTo(expectedValue, "//status/text()", r);
    }
}
