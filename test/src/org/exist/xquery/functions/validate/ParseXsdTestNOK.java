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

import org.junit.*;
import static org.junit.Assert.*;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;

import java.io.File;
import java.io.FilenameFilter;

import org.exist.test.EmbeddedExistTester;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;

/**
 * Tests for the validation:jing() function with SCHs.
 * 
 * @author dizzzz@exist-db.org
 */
public class ParseXsdTestNOK extends EmbeddedExistTester {

    private static final String noValidation = "<?xml version='1.0'?>" +
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "<validation mode=\"no\"/>" +
            "</collection>";

    @BeforeClass
    public static void prepareResources() throws Exception {

        // Switch off validation
        Collection conf = createCollection(rootCollection, "system/config/db/addressbook");
        storeResource(conf, "collection.xconf", noValidation.getBytes());

        // Create filter
        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return (name.endsWith("xml") || name.startsWith("address"));
            }
        };

        // Store schematron 1.5 test files
        Collection collection = createCollection(rootCollection, "addressbook");
        File sources = new File("samples/validation/addressbook");

        for (File file : sources.listFiles(filter)) {
            LOG.info("Storing " + file.getAbsolutePath());
            byte[] data = readFile(sources, file.getName());
            storeResource(collection, file.getName(), data);
        }

    }

    @Test
    public void xsd_stored_valid() {
        String query = "validation:jaxp-report( " +
                "doc('/db/addressbook/addressbook_valid.xml'), " +
                "xs:anyURI('/db/addressbook/addressbook.xsd'), () )";

        try {
            ResourceSet results = executeQuery(query);
            assertEquals(1, results.getSize());

            String r = (String) results.getResource(0).getContent();
            System.out.println(r);

            assertXpathEvaluatesTo("valid", "//status/text()", r);

        } catch (Exception ex) {
            LOG.error(ex);
            ex.printStackTrace();
            fail(ex.getMessage());
        }
    }

    @Test @Ignore("todo")
    public void xsd_stored_invalid() {
        String query = "validation:jaxp-report( doc('/db/tournament/1.5/Tournament-invalid.xml'), " +
                "doc('/db/tournament/1.5/tournament-schema.sch') )";

        try {
            ResourceSet results = executeQuery(query);
            assertEquals(1, results.getSize());

            String r = (String) results.getResource(0).getContent();
            System.out.println(r);

            assertXpathEvaluatesTo("invalid", "//status/text()", r);

        } catch (Exception ex) {
            LOG.error(ex);
            ex.printStackTrace();
            fail(ex.getMessage());
        }
    }

    @Test @Ignore("todo")
    public void xsd_anyuri_valid() {
        String query = "validation:jaxp-report( xs:anyURI('xmldb:exist:///db/tournament/1.5/Tournament-valid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/tournament/1.5/tournament-schema.sch') )";

        try {
            ResourceSet results = executeQuery(query);
            assertEquals(1, results.getSize());

            String r = (String) results.getResource(0).getContent();
            System.out.println(r);

            assertXpathEvaluatesTo("valid", "//status/text()", r);

        } catch (Exception ex) {
            LOG.error(ex);
            ex.printStackTrace();
            fail(ex.getMessage());
        }
    }

    @Test @Ignore("todo")
    public void xsd_anyuri_invalid() {
        String query = "validation:jaxp-report( xs:anyURI('xmldb:exist:///db/tournament/1.5/Tournament-invalid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/tournament/1.5/tournament-schema.sch') )";

        try {
            ResourceSet results = executeQuery(query);
            assertEquals(1, results.getSize());

            String r = (String) results.getResource(0).getContent();
            System.out.println(r);

            assertXpathEvaluatesTo("invalid", "//status/text()", r);

        } catch (Exception ex) {
            LOG.error(ex);
            ex.printStackTrace();
            fail(ex.getMessage());
        }
    }
}
