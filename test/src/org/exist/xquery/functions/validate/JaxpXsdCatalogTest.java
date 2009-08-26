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

import org.junit.*;
import static org.junit.Assert.*;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;

import java.io.File;
import java.io.FilenameFilter;

import org.exist.test.EmbeddedExistTester;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;

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
    public static void prepareResources() throws Exception {

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
            LOG.info("Storing " + file.getAbsolutePath());
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
    public void clearGrammarCache() {
        LOG.info("Clearing grammar cache");
        ResourceSet results = null;
        try {
            results = executeQuery("validation:clear-grammar-cache()");
            String r = (String) results.getResource(0).getContent();
            System.out.println(r);

        } catch (Exception e) {
            LOG.error(e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /*
     * ***********************************************************************************
     */
    
    @Test
    public void xsd_stored_catalog_valid() {
        String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/valid.xml'), false()," +
                "doc('/db/parse/catalog.xml') )";

        executeAndEvaluate(query,"valid");
    }

    @Test
    public void xsd_stored_catalog_invalid() {
        String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/invalid.xml'), false()," +
                "doc('/db/parse/catalog.xml') )";

        executeAndEvaluate(query,"invalid");
    }

    /*
     * ***********************************************************************************
     */
    @Test
    public void xsd_anyURI_catalog_valid() {
        String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/valid.xml'), false()," +
                "xs:anyURI('/db/parse/catalog.xml') )";

        executeAndEvaluate(query,"valid");
    }

    @Test
    public void xsd_anyURI_catalog_invalid() {
        String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/invalid.xml'), false()," +
                "xs:anyURI('/db/parse/catalog.xml') )";

        executeAndEvaluate(query,"invalid");
    }

    /*
     * ***********************************************************************************
     */
    
    @Test
    public void xsd_searched_valid() {
        String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/valid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";

        executeAndEvaluate(query,"valid");
    }

    @Test
    public void xsd_searched_invalid() {
        String query = "validation:jaxp-report( " +
                "doc('/db/parse/instance/invalid.xml'), false()," +
                "xs:anyURI('/db/parse/') )";

        executeAndEvaluate(query,"invalid");
    }

    private void executeAndEvaluate(String query, String expectedValue){

        LOG.info("Query="+query);
        LOG.info("ExpectedValue="+query);

        try {
            ResourceSet results = executeQuery(query);
            assertEquals(1, results.getSize());

            String r = (String) results.getResource(0).getContent();
            LOG.info(r);

            assertXpathEvaluatesTo(expectedValue, "//status/text()", r);

        } catch (Exception ex) {
            LOG.error(ex);
            fail(ex.getMessage());
        }
    }
}
