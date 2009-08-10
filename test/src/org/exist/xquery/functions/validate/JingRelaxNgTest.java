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
 * Tests for the validation:jing() function with RNGs and RNCs.
 * 
 * @author dizzzz@exist-db.org
 */
public class JingRelaxNgTest extends EmbeddedExistTester {

    @BeforeClass
    public static void prepareResources() throws Exception {

        String noValidation = "<?xml version='1.0'?>" +
                "<collection xmlns=\"http://exist-db.org/collection-config/1.0" +
                "\">" +
                "<validation mode=\"no\"/>" +
                "</collection>";

        Collection conf = createCollection(rootCollection, "system/config/db/personal");
        storeResource(conf, "collection.xconf", noValidation.getBytes());

        Collection collection = createCollection(rootCollection, "personal");

        File directory = new File("samples/validation/personal");

        FilenameFilter filter = new FilenameFilter() {

            public boolean accept(File dir, String name) {
                return (name.startsWith("personal"));
            }
        };

        for (File file : directory.listFiles(filter)) {
            LOG.info("Storing " + file.getAbsolutePath());
            byte[] data = readFile(directory, file.getName());
            storeResource(collection, file.getName(), data);
        }

    }


    @Test
    public void rng_stored_valid_boolean() {
        String query = "validation:jing( " +
                "doc('/db/personal/personal-valid.xml'), " +
                "doc('/db/personal/personal.rng') )";

        try {
            ResourceSet results = executeQuery(query);
            assertEquals(1, results.getSize());
            assertEquals(query, "true",
                    results.getResource(0).getContent().toString());

        } catch (Exception ex) {
            LOG.error(ex);
            fail(ex.getMessage());
        }
    }
    
    @Test
    public void rng_stored_valid() {
        String query = "validation:jing-report( " +
                "doc('/db/personal/personal-valid.xml'), " +
                "doc('/db/personal/personal.rng') )";

        executeAndEvaluate(query,"valid");
    }



    @Test
    public void rng_stored_invalid() {
        String query = "validation:jing-report( " +
                "doc('/db/personal/personal-invalid.xml'), " +
                "doc('/db/personal/personal.rng') )";

        executeAndEvaluate(query,"invalid");
    }

    @Test
    public void rng_anyuri_valid() {
        String query = "validation:jing-report( " +
                "xs:anyURI('xmldb:exist:///db/personal/personal-valid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/personal/personal.rng') )";

        executeAndEvaluate(query,"valid");
    }

    @Test
    public void rng_anyuri_invalid() {
        String query = "validation:jing-report( " +
                "xs:anyURI('xmldb:exist:///db/personal/personal-invalid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/personal/personal.rng') )";

        executeAndEvaluate(query,"invalid");
    }

    @Test
    public void rnc_stored_valid() {
        String query = "validation:jing-report( " +
                "doc('/db/personal/personal-valid.xml'), " +
                "util:binary-doc('/db/personal/personal.rnc') )";

        executeAndEvaluate(query,"valid");
    }

    @Test
    public void rnc_stored_invalid() {
        String query = "validation:jing-report( " +
                "doc('/db/personal/personal-invalid.xml'), " +
                "util:binary-doc('/db/personal/personal.rnc') )";

        executeAndEvaluate(query,"invalid");
    }

    @Test
    public void rnc_anyuri_valid() {
        String query = "validation:jing-report( xs:anyURI('xmldb:exist:///db/personal/personal-valid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/personal/personal.rnc') )";

        executeAndEvaluate(query,"valid");
    }

    @Test
    public void rnc_anyuri_invalid() {
        String query = "validation:jing-report( xs:anyURI('xmldb:exist:///db/personal/personal-invalid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/personal/personal.rnc') )";

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
