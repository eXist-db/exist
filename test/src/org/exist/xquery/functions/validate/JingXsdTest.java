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
 * Tests for the validation:jing() function with XSDs.
 *
 * @author dizzzz@exist-db.org
 */
public class JingXsdTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true);

    @BeforeClass
    public static void prepareResources() throws Exception {
        final String noValidation = "<?xml version='1.0'?>" +
                "<collection xmlns=\"http://exist-db.org/collection-config/1.0" +
                "\">" +
                "<validation mode=\"no\"/>" +
                "</collection>";

        Collection conf = null;
        try {
            conf = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "system/config/db/personal");
            ExistXmldbEmbeddedServer.storeResource(conf, "collection.xconf", noValidation.getBytes());
        } finally {
            if(conf != null) {
                conf.close();
            }
        }

        Collection collection = null;
        try {
            collection = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "personal");

            final Path directory = TestUtils.resolveSample("validation/personal");

            final Predicate<Path> filter = path -> FileUtils.fileName(path).startsWith("personal");

            for (final Path file : FileUtils.list(directory, filter)) {
                final byte[] data = TestUtils.readFile(file);
                ExistXmldbEmbeddedServer.storeResource(collection, FileUtils.fileName(file), data);
            }
        } finally {
            if(collection != null) {
                collection.close();
            }
        }

    }

    @Test
    public void xsd_stored_valid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jing-report( " +
                "doc('/db/personal/personal-valid.xml'), " +
                "doc('/db/personal/personal.xsd') )";
        executeAndEvaluate(query,"valid");
    }

    @Test
    public void xsd_stored_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jing-report( " +
                "doc('/db/personal/personal-invalid.xml'), " +
                "doc('/db/personal/personal.xsd') )";
        executeAndEvaluate(query,"invalid");
    }

    @Test
    public void xsd_anyuri_valid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jing-report( " +
                "xs:anyURI('xmldb:exist:///db/personal/personal-valid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/personal/personal.xsd') )";
        executeAndEvaluate(query,"valid");
    }

    @Test
    public void xsd_anyuri_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jing-report( " +
                "xs:anyURI('xmldb:exist:///db/personal/personal-invalid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/personal/personal.xsd') )";
        executeAndEvaluate(query,"invalid");
    }

    private void executeAndEvaluate(final String query, final String expectedValue) throws XMLDBException, SAXException, IOException, XpathException {
        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());

        final String r = (String) results.getResource(0).getContent();
        assertXpathEvaluatesTo(expectedValue, "//status/text()", r);
    }
}
