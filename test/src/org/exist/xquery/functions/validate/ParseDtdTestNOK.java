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
import org.exist.util.XMLFilenameFilter;
import org.junit.*;
import static org.junit.Assert.*;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.exist.test.EmbeddedExistTester;

import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 * Tests for the validation:jaxp() function with DTDss.
 * 
 * @author dizzzz@exist-db.org
 */
public class ParseDtdTestNOK extends EmbeddedExistTester {

    private static final String noValidation = "<?xml version='1.0'?>" +
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "<validation mode=\"no\"/>" +
            "</collection>";

    @BeforeClass
    public static void prepareResources() throws Exception {

        // Switch off validation
        final Collection conf = createCollection(rootCollection, "system/config/db/hamlet");
        storeResource(conf, "collection.xconf", noValidation.getBytes());

        // Store dtd test files
        final Collection collection = createCollection(rootCollection, "hamlet");
        final Path sources = Paths.get("samples/validation/dtd");

        for (final Path file : FileUtils.list(sources, XMLFilenameFilter.asPredicate())) {
            final byte[] data = readFile(file);
            storeResource(collection, FileUtils.fileName(file), data);
        }

        final Path dtd = Paths.get("samples/validation/dtd");
        final Collection collection1 = createCollection(rootCollection, "hamlet/dtd");
        final byte[] data = readFile(dtd, "hamlet.dtd");
        storeResource(collection1, "hamlet.dtd", data);

    }

    @Test
    public void xsd_stored_valid() throws XMLDBException, SAXException, IOException, XpathException {
        String query = "validation:jaxp-report( " +
                "doc('/db/hamlet/hamlet_valid.xml'), " +
                "xs:anyURI('/db/hamlet/dtd/hamlet.dtd'), () )";

        ResourceSet results = executeQuery(query);
        assertEquals(1, results.getSize());

        String r = (String) results.getResource(0).getContent();

        assertXpathEvaluatesTo("valid", "//status/text()", r);
    }

    @Test
    public void xsd_stored_invalid() throws XMLDBException, SAXException, IOException, XpathException {
        String query = "validation:jaxp-report(doc('/db/hamlet/hamlet_invalid.xml'), " +
                "xs:anyURI('/db/hamlet/dtd/hamlet.dtd'), () )";

        ResourceSet results = executeQuery(query);
        assertEquals(1, results.getSize());

        String r = (String) results.getResource(0).getContent();

        assertXpathEvaluatesTo("invalid", "//status/text()", r);
    }

    @Test
    public void xsd_anyuri_valid() throws XMLDBException, SAXException, IOException, XpathException {
        String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/hamlet/hamlet_valid.xml'), " +
                "xs:anyURI('/db/hamlet/dtd/hamlet.dtd'), () )";

        ResourceSet results = executeQuery(query);
        assertEquals(1, results.getSize());

        String r = (String) results.getResource(0).getContent();

        assertXpathEvaluatesTo("valid", "//status/text()", r);
    }

    @Test
    public void xsd_anyuri_invalid() throws XMLDBException, SAXException, IOException, XpathException {
        String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/hamlet/hamlet_invalid.xml'), " +
                "xs:anyURI('/db/hamlet/dtd/hamlet.dtd'), () )";

        ResourceSet results = executeQuery(query);
        assertEquals(1, results.getSize());

        String r = (String) results.getResource(0).getContent();

        assertXpathEvaluatesTo("invalid", "//status/text()", r);
    }
}
