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

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.io.InputStreamUtil;
import org.junit.jupiter.api.BeforeAll;

import static org.exist.collections.CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE;
import static org.exist.samples.Samples.SAMPLES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.xmlunit.matchers.EvaluateXPathMatcher.hasXPath;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests for the validation:jaxp() function with DTDss.
 * 
 * @author dizzzz@exist-db.org
 */
public class ParseDtdNokTest {

    @RegisterExtension
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String noValidation = "<?xml version='1.0'?>" +
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "<validation mode=\"no\"/>" +
            "</collection>";

    @BeforeAll
    public static void prepareResources() throws Exception {

        // Switch off validation
        try (Collection conf = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "system/config/db/hamlet")) {
            ExistXmldbEmbeddedServer.storeResource(conf, DEFAULT_COLLECTION_CONFIG_FILE, noValidation.getBytes());
        }

        // Store dtd test files; catalog.xml expects hamlet.dtd in same collection
        final String[] dtdTestFiles = { "catalog.xml", "hamlet.dtd", "hamlet_invalid.xml", "hamlet_nodoctype.xml", "hamlet_valid.xml", "hamlet_wrongdoctype.xml" };
        try (Collection collection = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "hamlet")) {

            for (final String dtdTestFile : dtdTestFiles) {
                try (final InputStream is = SAMPLES.getSample("validation/dtd/" + dtdTestFile)) {
                    ExistXmldbEmbeddedServer.storeResource(collection, dtdTestFile, InputStreamUtil.readAll(is));
                }
            }
        }

    }

    @org.junit.jupiter.api.Test
    public void xsdStoredValid() throws XMLDBException {
        final String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/hamlet/hamlet_valid.xml'), false(), xs:anyURI('/db/hamlet/') )";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());

        final String r = (String) results.getResource(0).getContent();
        assertThat(r, hasXPath("//status/text()", equalTo("valid")));
    }

    @org.junit.jupiter.api.Test
    public void xsdStoredInvalid() throws XMLDBException {
        final String query = "validation:jaxp-report( xs:anyURI('/db/hamlet/hamlet_invalid.xml'), false(), xs:anyURI('/db/hamlet/') )";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());

        final String r = (String) results.getResource(0).getContent();
        assertThat(r, hasXPath("//status/text()", equalTo("invalid")));
    }

    @org.junit.jupiter.api.Test
    public void xsdAnyuriValid() throws XMLDBException {
        final String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/hamlet/hamlet_valid.xml'), false(), xs:anyURI('/db/hamlet/') )";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());

        final String r = (String) results.getResource(0).getContent();
        assertThat(r, hasXPath("//status/text()", equalTo("valid")));
    }

    @org.junit.jupiter.api.Test
    public void xsdAnyuriInvalid() throws XMLDBException {
        final String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/hamlet/hamlet_invalid.xml'), false(), xs:anyURI('/db/hamlet/') )";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());

        final String r = (String) results.getResource(0).getContent();
        assertThat(r, hasXPath("//status/text()", equalTo("invalid")));
    }
}
