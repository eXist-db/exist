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
import org.junit.jupiter.api.BeforeEach;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.xmlunit.matchers.EvaluateXPathMatcher.hasXPath;
import static org.junit.jupiter.api.Assertions.assertEquals;


import org.xmldb.api.base.ResourceSet;

import java.io.IOException;
import org.xml.sax.SAXException;
import org.xmldb.api.base.XMLDBException;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests for the validation:jing() function with NVDLs
 *
 * @author jim.fuller@webcomposite.com
 * @author dizzzz@exist-db.org
 */
public class JingOnvdlTest {

    @RegisterExtension
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private final static String RNG_DATA1 =
            "<element  name=\'Book\' xmlns='http://relaxng.org/ns/structure/1.0'  ns=\'http://www.books.org\'> " +
            "<element name=\'Title\'><text/></element>" +
            "<element name=\'Author\'><text/></element>" +
            "<element name=\'Date\'><text/></element>" +
            "<element name=\'ISBN\'><text/></element>" +
            "<element name=\'Publisher\'><text/></element>" +
            "</element>";
    private final static String NVDL_DATA1 = "<rules xmlns='http://purl.oclc.org/dsdl/nvdl/ns/structure/1.0'> " +
            "<namespace ns=\'http://www.books.org\'>" +
            "     <validate schema=\"Book.rng\" />" +
            "</namespace>" +
            "</rules>";
    private final static String XML_DATA1 = "<Book xmlns='http://www.books.org'> " +
            "<Title>The Wisdom of Crowds</Title>" +
            "<Author>James Surowiecki</Author>" +
            "<Date>2005</Date>" +
            "<ISBN>0-385-72170-6</ISBN>" +
            "<Publisher>Anchor Books</Publisher>" +
            "</Book>";

    private final static String XML_DATA2 = "<Book xmlns='http://www.books.org'> " +
            "<Title>The Wisdom of Crowds</Title>" +
            "<Author>James Surowiecki</Author>" +
            "<Dateee>2005</Dateee>" +
            "<ISBN>0-385-72170-6</ISBN>" +
            "<Publisher>Anchor Books</Publisher>" +
            "</Book>";

    @BeforeEach
    public void setUp() throws Exception {
        final String query = "xmldb:create-collection('xmldb:exist:///db','validate-test')";
		existEmbeddedServer.executeQuery(query);

        final String query1 = "xmldb:store('/db/validate-test', 'test.nvdl'," + NVDL_DATA1 + ")";
        existEmbeddedServer.executeQuery(query1);

        final String query2 = "xmldb:store('/db/validate-test', 'Book.rng'," + RNG_DATA1 + ")";
        existEmbeddedServer.executeQuery(query2);

        final String data1 = "xmldb:store('/db/validate-test', 'valid.xml'," + XML_DATA1 + ")";
        existEmbeddedServer.executeQuery(data1);

        final String data2 = "xmldb:store('/db/validate-test', 'invalid.xml'," + XML_DATA2 + ")";
        existEmbeddedServer.executeQuery(data2);
    }

    @org.junit.jupiter.api.Test
    public void onvdlValid() throws IOException, SAXException, XMLDBException {
        final String query = "let $a := " + XML_DATA1 +
                "let $b := xs:anyURI('/db/validate-test/test.nvdl')" +
                "return " +
                "validation:jing-report($a,$b)";
        assertThat(QueryResults.single(existEmbeddedServer, query), hasXPath("//status/text()", equalTo("valid")));
    }

    @org.junit.jupiter.api.Test
    public void onvdlInvalid() throws IOException, SAXException, XMLDBException {
        final String query = "let $a := <test/>" +
                    "let $b := xs:anyURI('/db/validate-test/test.nvdl')" +
                    "return " +
                    "validation:jing-report($a,$b)";
        assertThat(QueryResults.single(existEmbeddedServer, query), hasXPath("//status/text()", equalTo("invalid")));
    }


    @org.junit.jupiter.api.Test
    public void onvdlStoredValid() throws XMLDBException, SAXException, IOException {
        final String query = "validation:jing-report( " +
                "doc('/db/validate-test/valid.xml'), " +
                "doc('/db/validate-test/test.nvdl') )";
        assertThat(QueryResults.single(existEmbeddedServer, query), hasXPath("//status/text()", equalTo("valid")));
    }

    @org.junit.jupiter.api.Test
    public void onvdlStoredInvalid() throws XMLDBException, SAXException, IOException {
        final String query = "validation:jing-report( " +
                "doc('/db/validate-test/invalid.xml'), " +
                "doc('/db/validate-test/test.nvdl') )";
        assertThat(QueryResults.single(existEmbeddedServer, query), hasXPath("//status/text()", equalTo("invalid")));
    }

    @org.junit.jupiter.api.Test
    public void onvdlAnyuriValid() throws XMLDBException, SAXException, IOException {
        final String query = "validation:jing-report( " +
                "xs:anyURI('xmldb:exist:///db/validate-test/valid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/validate-test/test.nvdl') )";
        assertThat(QueryResults.single(existEmbeddedServer, query), hasXPath("//status/text()", equalTo("valid")));
    }

    @org.junit.jupiter.api.Test
    public void onvdlAnyuriInvalid() throws XMLDBException, SAXException, IOException {
        final String query = "validation:jing-report( " +
                "xs:anyURI('xmldb:exist:///db/validate-test/invalid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/validate-test/test.nvdl') )";
        assertThat(QueryResults.single(existEmbeddedServer, query), hasXPath("//status/text()", equalTo("invalid")));
    }

    @org.junit.jupiter.api.Test
    public void onvdl_anyuri_valid_boolean() throws XMLDBException {
        final String query = "validation:jing( " +
                "xs:anyURI('xmldb:exist:///db/validate-test/valid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/validate-test/test.nvdl') )";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());
        assertEquals("true", results.getResource(0).getContent().toString(),
                query);
    }
}
