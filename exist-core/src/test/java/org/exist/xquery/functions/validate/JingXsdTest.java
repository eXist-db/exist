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
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.io.InputStreamUtil;
import org.junit.*;

import static org.exist.samples.Samples.SAMPLES;
import static org.junit.Assert.*;

import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

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

    private static final String[] TEST_RESOURCES = { "personal-valid.xml", "personal-invalid.xml", "personal.xsd" };

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @BeforeClass
    public static void prepareResources() throws XMLDBException, URISyntaxException, IOException {
        final String noValidation = "<?xml version='1.0'?>" +
                "<collection xmlns='http://exist-db.org/collection-config/1.0'>" +
                "    <validation mode='no'/>" +
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

            for (final String testResource : TEST_RESOURCES) {
                try (final InputStream is = SAMPLES.getSample("validation/personal/" + testResource)) {
                    assertNotNull(is);
                    final byte[] data = InputStreamUtil.readAll(is);
                    ExistXmldbEmbeddedServer.storeResource(collection, testResource, data);
                }
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
        executeAndEvaluate(query, "valid");
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
