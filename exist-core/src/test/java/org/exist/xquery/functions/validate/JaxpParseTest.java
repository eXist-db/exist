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

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.io.InputStreamUtil;
import org.junit.*;
import static org.junit.Assert.*;
import static org.exist.samples.Samples.SAMPLES;

import java.io.IOException;
import java.io.InputStream;

import org.custommonkey.xmlunit.XMLAssert;

import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 * Tests for the validation:jaxp() function with Catalog (resolvers).
 * 
 * @author dizzzz@exist-db.org
 */
public class JaxpParseTest {

    private static final String[] TEST_RESOURCES = { "defaultValue.xml", "defaultValue.xsd" };

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String noValidation = "<?xml version='1.0'?>" +
            "<collection xmlns='http://exist-db.org/collection-config/1.0'>" +
            "    <validation mode='no'/>" +
            "</collection>";

    @BeforeClass
    public static void prepareResources() throws Exception {

        // Switch off validation
        Collection conf = null;
        try {
            conf = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "system/config/db/parse_validate");
            ExistXmldbEmbeddedServer.storeResource(conf, "collection.xconf", noValidation.getBytes());
        } finally {
            if(conf != null) {
                conf.close();
            }
        }

        Collection schemasCollection = null;
        try {
            schemasCollection = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "parse_validate");

            for (final String testResource : TEST_RESOURCES) {
                try (final InputStream is = SAMPLES.getSample("validation/parse_validate/" + testResource)) {
                    assertNotNull(is);
                    ExistXmldbEmbeddedServer.storeResource(schemasCollection, testResource, InputStreamUtil.readAll(is));
                }
            }
        } finally {
            if(schemasCollection != null) {
                schemasCollection.close();
            }
        }

    }

    @Before
    public void clearGrammarCache() throws XMLDBException {
        final ResourceSet results = existEmbeddedServer.executeQuery("validation:clear-grammar-cache()");
        results.getResource(0).getContent();
    }

    @Test
    public void parse_and_fill_defaults() throws XMLDBException, IOException, SAXException {
        String query = "validation:pre-parse-grammar(xs:anyURI('/db/parse_validate/defaultValue.xsd'))";
        String result = execute(query);
        assertEquals(result, "defaultTest");

        query = "declare option exist:serialize 'indent=no'; " +
                "validation:jaxp-parse(xs:anyURI('/db/parse_validate/defaultValue.xml'), true(), ())";
        result = execute(query);

        String expected = "<ns1:root xmlns:ns1=\"defaultTest\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "    <color>red</color>\n" +
                "    <shoesize country=\"nl\">43</shoesize>\n" +
                "</ns1:root>";

        XMLAssert.assertXMLEqual(expected, result);
    }

    private String execute(final String query) throws XMLDBException {
        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());
        return (String) results.getResource(0).getContent();
    }
}
