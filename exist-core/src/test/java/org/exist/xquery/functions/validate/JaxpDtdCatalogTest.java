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
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.io.InputStreamUtil;
import org.junit.*;
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
 * Tests for the validation:jaxp() function with Catalog (resolvers).
 * 
 * @author dizzzz@exist-db.org
 */
public class JaxpDtdCatalogTest {

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
            conf = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "system/config/db/parse");
            ExistXmldbEmbeddedServer.storeResource(conf, "collection.xconf", noValidation.getBytes());
        } finally {
            if(conf != null) {
                conf.close();
            }
        }

        Collection dtdsCollection = null;
        try {
            dtdsCollection = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "parse/dtds");

            try (final InputStream is = SAMPLES.getSample("validation/parse/dtds/MyNameSpace.dtd")) {
                assertNotNull(is);
                ExistXmldbEmbeddedServer.storeResource(dtdsCollection, "MyNameSpace.dtd", InputStreamUtil.readAll(is));
            }
        } finally {
            if(dtdsCollection != null) {
                dtdsCollection.close();
            }
        }

        Collection parseCollection = null;
        try {
            parseCollection = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "parse");

            try (final InputStream is = SAMPLES.getSample("validation/parse/catalog.xml")) {
                assertNotNull(is);
                ExistXmldbEmbeddedServer.storeResource(parseCollection, "catalog.xml", InputStreamUtil.readAll(is));
            }
        } finally {
            if(parseCollection != null) {
                parseCollection.close();
            }
        }

        Collection instanceCollection = null;
        try {
            instanceCollection = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "parse/instance");

            try (final InputStream is = SAMPLES.getSample("validation/parse/instance/valid-dtd.xml")) {
                assertNotNull(is);
                ExistXmldbEmbeddedServer.storeResource(instanceCollection, "valid-dtd.xml", InputStreamUtil.readAll(is));
            }

            try (final InputStream is = SAMPLES.getSample("validation/parse/instance/invalid-dtd.xml")) {
                assertNotNull(is);
                ExistXmldbEmbeddedServer.storeResource(instanceCollection, "invalid-dtd.xml", InputStreamUtil.readAll(is));
            }
        } finally {
            if(instanceCollection != null) {
                instanceCollection.close();
            }
        }
    }

    @Before
    public void clearGrammarCache() throws XMLDBException {
        final ResourceSet results = existEmbeddedServer.executeQuery("validation:clear-grammar-cache()");
        results.getResource(0).getContent();
    }

    /*
     * ***********************************************************************************
     */
    @Test
    public void dtd_stored_catalog_valid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/valid-dtd.xml'), false()," +
                "doc('/db/parse/catalog.xml') )";
        executeAndEvaluate(query,"valid");
    }

    @Test
    public void dtd_stored_catalog_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/invalid-dtd.xml'), false()," +
                "doc('/db/parse/catalog.xml') )";
        executeAndEvaluate(query,"invalid");
    }

    @Test
    public void dtd_anyURI_catalog_valid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/valid-dtd.xml'), false()," +
                "xs:anyURI('/db/parse/catalog.xml') )";
        executeAndEvaluate(query,"valid");
    }

    @Test
    public void dtd_anyURI_catalog_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/invalid-dtd.xml'), false()," +
                "xs:anyURI('/db/parse/catalog.xml') )";
       executeAndEvaluate(query,"invalid");
    }

    /*
     * ***********************************************************************************
     *
     * DIZZZZ: doc('/db/parse/instance/valid-dtd.xml') does not work xs:anyURI does
     *
     */
    @Test
    public void dtd_searched_valid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/valid-dtd.xml'), false()," +
                "xs:anyURI('/db/parse/') )";
        executeAndEvaluate(query,"valid");
    }

    @Test
    public void dtd_searched_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        final String query = "validation:jaxp-report( " +
                "xs:anyURI('/db/parse/instance/invalid-dtd.xml'), false()," +
                "xs:anyURI('/db/parse/') )";
        executeAndEvaluate(query,"invalid");
    }

    private void executeAndEvaluate(final String query, final String expectedValue) throws XMLDBException, SAXException, IOException, XpathException {
        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());

        final String r = (String) results.getResource(0).getContent();
        assertXpathEvaluatesTo(expectedValue, "//status/text()", r);
    }
}
