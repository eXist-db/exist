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

import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLAssert;
import org.exist.test.EmbeddedExistTester;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;

/**
 * Tests for the validation:jaxp() function with Catalog (resolvers).
 * 
 * @author dizzzz@exist-db.org
 */
public class JaxpParseTest extends EmbeddedExistTester {

    private static final String noValidation = "<?xml version='1.0'?>" +
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "<validation mode=\"no\"/>" +
            "</collection>";

    @BeforeClass
    public static void prepareResources() throws Exception {

        // Switch off validation
        Collection conf = createCollection(rootCollection, "system/config/db/parse_validate");
        storeResource(conf, "collection.xconf", noValidation.getBytes());

        // Create filter
        FilenameFilter filter = new FilenameFilter() {

            public boolean accept(File dir, String name) {
                return (name.startsWith("default"));
            }
        };

        Collection schemasCollection = createCollection(rootCollection, "parse_validate");
        File schemas = new File("samples/validation/parse_validate");

        for (File file : schemas.listFiles(filter)) {
            LOG.info("Storing " + file.getAbsolutePath());
            byte[] data = readFile(schemas, file.getName());
            storeResource(schemasCollection, file.getName(), data);
        }

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
    public void parse_and_fill_defaults() {
        String query = "validation:pre-parse-grammar(xs:anyURI('/db/parse_validate/defaultValue.xsd'))";
        String result = execute(query);
        assertEquals(result, "defaultTest");

        query = "declare option exist:serialize 'indent=no'; " +
                "validation:jaxp-parse(xs:anyURI('/db/parse_validate/defaultValue.xml'), true(), ())";
        result = execute(query);

        String expected = "<ns1:root xmlns:ns1=\"defaultTest\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "    <color>red</color>\n" +
                "    <shoesize country=\"nl\">43</shoesize>\n" +
                "</ns1:root>";

        System.out.println("Expected:" + expected);
        System.out.println("Result:" + result);

        try {
            XMLAssert.assertXMLEqual(expected, result);

        } catch (Exception ex) {
            fail(ex.getMessage());
            ex.printStackTrace();
        }
    }

    private String execute(String query) {

        LOG.info("Query=" + query);

        String retVal = null;

        try {
            ResourceSet results = executeQuery(query);
            assertEquals(1, results.getSize());

            retVal = (String) results.getResource(0).getContent();
            LOG.info(retVal);

        } catch (Exception ex) {
            LOG.error(ex);
            fail(ex.getMessage());
        }

        return retVal;
    }
}
