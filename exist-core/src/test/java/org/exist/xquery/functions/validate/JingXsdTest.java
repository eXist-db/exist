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
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;

import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests for the validation:jing() function with XSDs.
 *
 * @author dizzzz@exist-db.org
 */
public class JingXsdTest {

    private static final String[] TEST_RESOURCES = { "personal-valid.xml", "personal-invalid.xml", "personal.xsd" };

    @RegisterExtension
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @BeforeAll
    public static void prepareResources() throws XMLDBException, IOException {
        final String noValidation = "<?xml version='1.0'?>" +
                "<collection xmlns='http://exist-db.org/collection-config/1.0'>" +
                "    <validation mode='no'/>" +
                "</collection>";

        try (Collection conf = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "system/config/db/personal")) {
            ExistXmldbEmbeddedServer.storeResource(conf, DEFAULT_COLLECTION_CONFIG_FILE, noValidation.getBytes());
        }

        try (Collection collection = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "personal")) {

            for (final String testResource : TEST_RESOURCES) {
                try (final InputStream is = SAMPLES.getSample("validation/personal/" + testResource)) {
                    assertNotNull(is);
                    final byte[] data = InputStreamUtil.readAll(is);
                    ExistXmldbEmbeddedServer.storeResource(collection, testResource, data);
                }
            }
        }

    }

    @org.junit.jupiter.api.Test
    public void xsdStoredValid() throws XMLDBException, SAXException, IOException {
        final String query = "validation:jing-report( " +
                "doc('/db/personal/personal-valid.xml'), " +
                "doc('/db/personal/personal.xsd') )";
        assertThat(QueryResults.single(existEmbeddedServer, query), hasXPath("//status/text()", equalTo("valid")));
    }

    @org.junit.jupiter.api.Test
    public void xsdStoredInvalid() throws XMLDBException, SAXException, IOException {
        final String query = "validation:jing-report( " +
                "doc('/db/personal/personal-invalid.xml'), " +
                "doc('/db/personal/personal.xsd') )";
        assertThat(QueryResults.single(existEmbeddedServer, query), hasXPath("//status/text()", equalTo("invalid")));
    }

    @org.junit.jupiter.api.Test
    public void xsdAnyuriValid() throws XMLDBException, SAXException, IOException {
        final String query = "validation:jing-report( " +
                "xs:anyURI('xmldb:exist:///db/personal/personal-valid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/personal/personal.xsd') )";
        assertThat(QueryResults.single(existEmbeddedServer, query), hasXPath("//status/text()", equalTo("valid")));
    }

    @org.junit.jupiter.api.Test
    public void xsdAnyuriInvalid() throws XMLDBException, SAXException, IOException {
        final String query = "validation:jing-report( " +
                "xs:anyURI('xmldb:exist:///db/personal/personal-invalid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/personal/personal.xsd') )";
        assertThat(QueryResults.single(existEmbeddedServer, query), hasXPath("//status/text()", equalTo("invalid")));
    }
}
