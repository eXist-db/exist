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
import org.exist.TestUtils;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.FileUtils;
import org.exist.util.XMLFilenameFilter;
import org.junit.*;
import static org.junit.Assert.*;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;

import java.io.IOException;
import java.nio.file.Path;

import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 * Tests for the validation:jaxp() function with DTDss.
 * 
 * @author dizzzz@exist-db.org
 */
public class ParseDtdTestNOK {

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
            conf = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "system/config/db/hamlet");
            ExistXmldbEmbeddedServer.storeResource(conf, "collection.xconf", noValidation.getBytes());
        } finally {
            if(conf != null) {
                conf.close();
            }
        }

        // Store dtd test files
        Collection collection = null;
        try {
            collection = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "hamlet");
            final Path sources = TestUtils.resolveSample("validation/dtd");

            for (final Path file : FileUtils.list(sources, XMLFilenameFilter.asPredicate())) {
                final byte[] data = TestUtils.readFile(file);
                ExistXmldbEmbeddedServer.storeResource(collection, FileUtils.fileName(file), data);
            }
        } finally {
            if(collection != null) {
                collection.close();
            }
        }

        final Path dtd = TestUtils.resolveSample("validation/dtd");
        Collection collection1 = null;
        try {
            collection1 = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "hamlet/dtd");
            final byte[] data = TestUtils.readFile(dtd, "hamlet.dtd");
            ExistXmldbEmbeddedServer.storeResource(collection1, "hamlet.dtd", data);
        } finally {
            if(collection1 != null) {
                collection1.close();
            }
        }

    }

    @Test
    public void xsd_stored_valid() throws XMLDBException, SAXException, IOException, XpathException {
        final String query = "validation:jaxp-report( " +
                "doc('/db/hamlet/hamlet_valid.xml'), " +
                "xs:anyURI('/db/hamlet/dtd/hamlet.dtd'), () )";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());

        final String r = (String) results.getResource(0).getContent();
        assertXpathEvaluatesTo("valid", "//status/text()", r);
    }

    @Test
    public void xsd_stored_invalid() throws XMLDBException, SAXException, IOException, XpathException {
        final String query = "validation:jaxp-report(doc('/db/hamlet/hamlet_invalid.xml'), " +
                "xs:anyURI('/db/hamlet/dtd/hamlet.dtd'), () )";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());

        final String r = (String) results.getResource(0).getContent();
        assertXpathEvaluatesTo("invalid", "//status/text()", r);
    }

    @Test
    public void xsd_anyuri_valid() throws XMLDBException, SAXException, IOException, XpathException {
        final String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/hamlet/hamlet_valid.xml'), " +
                "xs:anyURI('/db/hamlet/dtd/hamlet.dtd'), () )";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());

        final String r = (String) results.getResource(0).getContent();
        assertXpathEvaluatesTo("valid", "//status/text()", r);
    }

    @Test
    public void xsd_anyuri_invalid() throws XMLDBException, SAXException, IOException, XpathException {
        final String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/hamlet/hamlet_invalid.xml'), " +
                "xs:anyURI('/db/hamlet/dtd/hamlet.dtd'), () )";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());

        final String r = (String) results.getResource(0).getContent();
        assertXpathEvaluatesTo("invalid", "//status/text()", r);
    }
}
