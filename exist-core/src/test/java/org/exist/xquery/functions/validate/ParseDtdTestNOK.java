/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.validate;

import org.custommonkey.xmlunit.exceptions.XpathException;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.io.InputStreamUtil;
import org.junit.*;

import static org.exist.collections.CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE;
import static org.junit.Assert.*;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;
import static org.exist.samples.Samples.SAMPLES;

import java.io.IOException;
import java.io.InputStream;

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
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String noValidation = "<?xml version='1.0'?>" +
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "<validation mode=\"no\"/>" +
            "</collection>";

    @BeforeClass
    public static void prepareResources() throws Exception {

        // Switch off validation
        try (Collection conf = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "system/config/db/hamlet")) {
            ExistXmldbEmbeddedServer.storeResource(conf, DEFAULT_COLLECTION_CONFIG_FILE, noValidation.getBytes());
        }

        // Store dtd test files
        final String[] dtdTestFiles = { "catalog.xml", "hamlet_invalid.xml", "hamlet_nodoctype.xml", "hamlet_valid.xml", "hamlet_wrongdoctype.xml" };
        try (Collection collection = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "hamlet")) {

            for (final String dtdTestFile : dtdTestFiles) {
                try (final InputStream is = SAMPLES.getSample("validation/dtd/" + dtdTestFile)) {
                    ExistXmldbEmbeddedServer.storeResource(collection, dtdTestFile, InputStreamUtil.readAll(is));
                }
            }
        }

        try (Collection collection1 = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "hamlet/dtd")) {
            try (final InputStream is = SAMPLES.getSample("validation/dtd/hamlet.dtd")) {
                ExistXmldbEmbeddedServer.storeResource(collection1, "hamlet.dtd", InputStreamUtil.readAll(is));
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
