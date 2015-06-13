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
 * Tests for the validation:jing() function with SCHs.
 * 
 * @author dizzzz@exist-db.org
 */
public class JingSchematronTest extends EmbeddedExistTester {

    private static final String noValidation = "<?xml version='1.0'?>" +
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "<validation mode=\"no\"/>" +
            "</collection>";

    @BeforeClass
    public static void prepareResources() throws Exception {

        // Switch off validation
        Collection conf = createCollection(rootCollection, "system/config/db/tournament");
        storeResource(conf, "collection.xconf", noValidation.getBytes());

        // Create filter
        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return (name.startsWith("Tournament") || name.startsWith("tournament"));
            }
        };

        // Store schematron 1.5 test files
        Collection col15 = createCollection(rootCollection, "tournament/1.5");
        File sch15 = new File("samples/validation/tournament/1.5");

        for (File file : sch15.listFiles(filter)) {
            byte[] data = readFile(sch15, file.getName());
            storeResource(col15, file.getName(), data);
        }

        // Store schematron iso testfiles
        Collection colISO = createCollection(rootCollection, "tournament/iso");
        File schISO = new File("samples/validation/tournament/iso");

        for (File file : schISO.listFiles(filter)) {
            byte[] data = readFile(schISO, file.getName());
            storeResource(colISO, file.getName(), data);
        }

    }

    @Test
    public void sch_15_stored_valid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jing-report( " +
                "doc('/db/tournament/1.5/Tournament-valid.xml'), " +
                "doc('/db/tournament/1.5/tournament-schema.sch') )";

        executeAndEvaluate(query,"valid");
    }

    @Test
    public void sch_15_stored_valid_boolean() throws XMLDBException {
        String query = "validation:jing( " +
                "doc('/db/tournament/1.5/Tournament-valid.xml'), " +
                "doc('/db/tournament/1.5/tournament-schema.sch') )";

        ResourceSet results = executeQuery(query);
        assertEquals(1, results.getSize());

        String r = (String) results.getResource(0).getContent();

        assertEquals("true", r);
    }

    @Test

    public void sch_15_stored_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jing-report( " +
                "doc('/db/tournament/1.5/Tournament-invalid.xml'), " +
                "doc('/db/tournament/1.5/tournament-schema.sch') )";

        executeAndEvaluate(query,"invalid");
    }

    @Test
    public void sch_15_anyuri_valid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jing-report( " +
                "xs:anyURI('xmldb:exist:///db/tournament/1.5/Tournament-valid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/tournament/1.5/tournament-schema.sch') )";

        executeAndEvaluate(query,"valid");
    }

    @Test
    public void sch_15_anyuri_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jing-report( " +
                "xs:anyURI('xmldb:exist:///db/tournament/1.5/Tournament-invalid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/tournament/1.5/tournament-schema.sch') )";

        executeAndEvaluate(query,"invalid");
    }

    private void executeAndEvaluate(String query, String expectedValue) throws XMLDBException, SAXException, IOException, XpathException {
        ResourceSet results = executeQuery(query);
        assertEquals(1, results.getSize());

        String r = (String) results.getResource(0).getContent();

        assertXpathEvaluatesTo(expectedValue, "//status/text()", r);
    }
}
