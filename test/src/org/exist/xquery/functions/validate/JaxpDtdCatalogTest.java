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
 * $Id: JingSchematronTest.java 9705 2009-08-08 13:52:37Z dizzzz $
 */
package org.exist.xquery.functions.validate;

import org.custommonkey.xmlunit.exceptions.XpathException;
import org.exist.util.FileUtils;
import org.junit.*;
import static org.junit.Assert.*;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;

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
public class JaxpDtdCatalogTest extends EmbeddedExistTester {

    private static final String noValidation = "<?xml version='1.0'?>" +
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "<validation mode=\"no\"/>" +
            "</collection>";

    @BeforeClass
    public static void prepareResources() throws Exception {

        // Switch off validation
        Collection conf = createCollection(rootCollection, "system/config/db/parse");
        storeResource(conf, "collection.xconf", noValidation.getBytes());

        // Create filter
        final Predicate<Path> filter = path -> (FileUtils.fileName(path).endsWith(".dtd"));

        final Collection dtdsCollection = createCollection(rootCollection, "parse/dtds");
        final Path schemas = Paths.get("samples/validation/parse/dtds");

        for (final Path file : FileUtils.list(schemas, filter)) {
            final byte[] data = readFile(file);
            storeResource(dtdsCollection, FileUtils.fileName(file), data);
        }

        final Path catalog = Paths.get("samples/validation/parse");
        final Collection parseCollection = createCollection(rootCollection, "parse");
        final byte[] data = readFile(catalog, "catalog.xml");
        storeResource(parseCollection, "catalog.xml", data);

        final Path instance = Paths.get("samples/validation/parse/instance");
        final Collection instanceCollection = createCollection(rootCollection, "parse/instance");

        final byte[] valid = readFile(instance, "valid-dtd.xml");
        storeResource(instanceCollection, "valid-dtd.xml", valid);

        final byte[] invalid = readFile(instance, "invalid-dtd.xml");
        storeResource(instanceCollection, "invalid-dtd.xml", invalid);
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
    public void dtd_stored_catalog_valid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/valid-dtd.xml'), false()," +
                "doc('/db/parse/catalog.xml') )";

        executeAndEvaluate(query,"valid");
    }

    @Test
    public void dtd_stored_catalog_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/invalid-dtd.xml'), false()," +
                "doc('/db/parse/catalog.xml') )";

        executeAndEvaluate(query,"invalid");
    }

    /*
     * ***********************************************************************************
     */
    @Test
    public void dtd_anyURI_catalog_valid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/valid-dtd.xml'), false()," +
                "xs:anyURI('/db/parse/catalog.xml') )";

        executeAndEvaluate(query,"valid");
    }

    @Test
    public void dtd_anyURI_catalog_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/invalid-dtd.xml'), false()," +
                "xs:anyURI('/db/parse/catalog.xml') )";

       executeAndEvaluate(query,"invalid");
    }

    /*
     * ***********************************************************************************
     *
     * DIZZZZ: doc('/db/parse/instance/valid-dtd.xml') does not work xs:anyURI does
     *
     */
    @Test
    public void dtd_searched_valid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/valid-dtd.xml'), false()," +
                "xs:anyURI('/db/parse/') )";

        executeAndEvaluate(query,"valid");
    }

    @Test
    public void dtd_searched_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/invalid-dtd.xml'), false()," +
                "xs:anyURI('/db/parse/') )";

        executeAndEvaluate(query,"invalid");
    }

    private void executeAndEvaluate(String query, String expectedValue) throws XMLDBException, SAXException, IOException, XpathException {
        ResourceSet results = executeQuery(query);
        assertEquals(1, results.getSize());

        String r = (String) results.getResource(0).getContent();

        assertXpathEvaluatesTo(expectedValue, "//status/text()", r);
    }
}
