/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.xquery.functions.validate;

import org.custommonkey.xmlunit.exceptions.XpathException;
import org.junit.*;
import static org.junit.Assert.*;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import org.exist.test.EmbeddedExistTester;

import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 * Tests for the validation:jaxp() function with Catalog (resolvers).
 * 
 * @author dizzzz@exist-db.org
 */
public class JaxpXsdCatalogTest extends EmbeddedExistTester {

    private static final String noValidation = "<?xml version='1.0'?>" +
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "<validation mode=\"no\"/>" +
            "</collection>";

    @BeforeClass
    public static void prepareResources() throws XMLDBException, IOException {

        // Switch off validation
        Collection conf = createCollection(rootCollection, "system/config/db/parse");
        storeResource(conf, "collection.xconf", noValidation.getBytes());

        // Create filter
        FilenameFilter filter = new FilenameFilter() {

            public boolean accept(File dir, String name) {
                return (name.endsWith(".xsd"));
            }
        };

        Collection schemasCollection = createCollection(rootCollection, "parse/schemas");
        File schemas = new File("samples/validation/parse/schemas");

        for (File file : schemas.listFiles(filter)) {
            byte[] data = readFile(schemas, file.getName());
            storeResource(schemasCollection, file.getName(), data);
        }

        File catalog = new File("samples/validation/parse");
        Collection parseCollection = createCollection(rootCollection, "parse");
        byte[] data = readFile(catalog, "catalog.xml");
        storeResource(parseCollection, "catalog.xml", data);

        File instance = new File("samples/validation/parse/instance");
        Collection instanceCollection = createCollection(rootCollection, "parse/instance");

        byte[] valid = readFile(instance, "valid.xml");
        storeResource(instanceCollection, "valid.xml", valid);

        byte[] invalid = readFile(instance, "invalid.xml");
        storeResource(instanceCollection, "invalid.xml", invalid);
    }

    @Before
    public void clearGrammarCache() throws XMLDBException {
        ResourceSet results = executeQuery("validation:clear-grammar-cache()");
        String r = (String) results.getResource(0).getContent();
    }

    /*
     * ***********************************************************************************
     */
    
    @Test
    public void xsd_stored_catalog_valid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/valid.xml'), false()," +
                "doc('/db/parse/catalog.xml') )";

        executeAndEvaluate(query,"valid");
    }

    @Test
    public void xsd_stored_catalog_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/invalid.xml'), false()," +
                "doc('/db/parse/catalog.xml') )";

        executeAndEvaluate(query,"invalid");
    }

    /*
     * ***********************************************************************************
     */
    @Test
    public void xsd_anyURI_catalog_valid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/valid.xml'), false()," +
                "xs:anyURI('/db/parse/catalog.xml') )";

        executeAndEvaluate(query,"valid");
    }

    @Test
    public void xsd_anyURI_catalog_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/invalid.xml'), false()," +
                "xs:anyURI('/db/parse/catalog.xml') )";

        executeAndEvaluate(query,"invalid");
    }

    /*
     * ***********************************************************************************
     */
    
    @Test
    public void xsd_searched_valid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/valid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";

        executeAndEvaluate(query,"valid");
    }

    @Test
    public void xsd_searched_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/invalid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";

        executeAndEvaluate(query,"invalid");
    }
    
    // test boolean function
    @Test
    public void xsd_searched_valid_boolean() throws XMLDBException {
        String query = "validation:jaxp( " +
                "doc('/db/parse/instance/valid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";

        assertEquals("true", executeOneValue(query));
    }
    
    // test boolean function
    @Test
    public void xsd_searched_invalid_boolean() throws XMLDBException {
        String query = "validation:jaxp( " +
                "doc('/db/parse/instance/invalid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";

        assertEquals("false", executeOneValue(query));
    }
    
    // test parse function
    @Test
    public void xsd_searched_parse_valid() throws SAXException, IOException, XpathException, XMLDBException {
        String query = "validation:jaxp-parse( " +
                "doc('/db/parse/instance/valid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";

        String r = executeOneValue(query);
        assertXpathEvaluatesTo("2006-05-04T18:13:51.0Z", "//Y", r);
    }
    
    // test parse function
    @Test
    public void xsd_searched_parse_invalid() throws SAXException, IOException, XpathException, XMLDBException {
        String query = "validation:jaxp-parse( " +
                "doc('/db/parse/instance/invalid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";

        String r = executeOneValue(query);
        assertXpathEvaluatesTo("2006-05-04T18:13:51.0Z", "//Y", r);
    }

    private void executeAndEvaluate(String query, String expectedValue) throws XMLDBException, SAXException, IOException, XpathException {
        ResourceSet results = executeQuery(query);
        assertEquals(1, results.getSize());

        String r = (String) results.getResource(0).getContent();

        assertXpathEvaluatesTo(expectedValue, "//status/text()", r);
    }
}
