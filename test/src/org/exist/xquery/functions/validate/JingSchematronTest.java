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
import org.exist.TestUtils;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.FileUtils;
import org.junit.*;
import static org.junit.Assert.*;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Predicate;

import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 * Tests for the validation:jing() function with SCHs.
 * 
 * @author dizzzz@exist-db.org
 */
public class JingSchematronTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true);

    private static final String noValidation = "<?xml version='1.0'?>" +
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "<validation mode=\"no\"/>" +
            "</collection>";

    @BeforeClass
    public static void prepareResources() throws Exception {

        // Switch off validation
        Collection conf = null;
        try {
            conf = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "system/config/db/tournament");
            ExistXmldbEmbeddedServer.storeResource(conf, "collection.xconf", noValidation.getBytes());
        } finally {
            if(conf != null) {
                conf.close();
            }
        }

        // Create filter
        final Predicate<Path> filter = path -> {
            final String fileName = FileUtils.fileName(path);
            return fileName.startsWith("Tournament") || fileName.startsWith("tournament");
        };

        // Store schematron 1.5 test files
        Collection col15 = null;
        try {
            col15 = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "tournament/1.5");
            final Path sch15 = TestUtils.resolveSample("validation/tournament/1.5");

            for (final Path file : FileUtils.list(sch15, filter)) {
                final byte[] data = TestUtils.readFile(file);
                ExistXmldbEmbeddedServer.storeResource(col15, FileUtils.fileName(file), data);
            }
        } finally {
            if(col15 != null) {
                col15.close();
            }
        }

        // Store schematron iso testfiles
        Collection colISO = null;
        try {
            colISO = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "tournament/iso");
            final Path schISO = TestUtils.resolveSample("validation/tournament/iso");

            for (final Path file : FileUtils.list(schISO, filter)) {
                final byte[] data = TestUtils.readFile(file);
                ExistXmldbEmbeddedServer.storeResource(colISO, FileUtils.fileName(file), data);
            }
        } finally {
            if(colISO != null) {
                colISO.close();
            }
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
        final String query = "validation:jing( " +
                "doc('/db/tournament/1.5/Tournament-valid.xml'), " +
                "doc('/db/tournament/1.5/tournament-schema.sch') )";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());

        final String r = (String) results.getResource(0).getContent();
        assertEquals("true", r);
    }

    @Test

    public void sch_15_stored_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jing-report( " +
                "doc('/db/tournament/1.5/Tournament-invalid.xml'), " +
                "doc('/db/tournament/1.5/tournament-schema.sch') )";
        executeAndEvaluate(query,"invalid");
    }

    @Test
    public void sch_15_anyuri_valid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jing-report( " +
                "xs:anyURI('xmldb:exist:///db/tournament/1.5/Tournament-valid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/tournament/1.5/tournament-schema.sch') )";
        executeAndEvaluate(query,"valid");
    }

    @Test
    public void sch_15_anyuri_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jing-report( " +
                "xs:anyURI('xmldb:exist:///db/tournament/1.5/Tournament-invalid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/tournament/1.5/tournament-schema.sch') )";
        executeAndEvaluate(query,"invalid");
    }

    private void executeAndEvaluate(final String query, final String expectedValue) throws XMLDBException, SAXException, IOException, XpathException {
        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());

        final String r = (String) results.getResource(0).getContent();
        assertXpathEvaluatesTo(expectedValue, "//status/text()", r);
    }
}
