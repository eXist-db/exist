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

import org.junit.*;
import static org.junit.Assert.*;

import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;
import org.custommonkey.xmlunit.exceptions.XpathException;

import org.exist.test.EmbeddedExistTester;
import org.exist.xquery.XPathException;

import org.xmldb.api.base.ResourceSet;

import java.io.IOException;
import org.xml.sax.SAXException;
import org.xmldb.api.base.XMLDBException;

/**
 * Tests for the validation:jing() function with NVDLs
 *
 * @author jim.fuller@webcomposite.com
 * @author dizzzz@exist-db.org
 */
public class JingOnvdlTest extends EmbeddedExistTester {

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

    public JingOnvdlTest() {
    }

    @Before
    public void setUp() throws Exception {

        String query = "xmldb:create-collection('xmldb:exist:///db','validate-test')";
        @SuppressWarnings("unused")
		ResourceSet result = executeQuery(query);

        String query1 = "xmldb:store('/db/validate-test', 'test.nvdl'," + NVDL_DATA1 + ")";
        @SuppressWarnings("unused")
        ResourceSet result1 = executeQuery(query1);

        String query2 = "xmldb:store('/db/validate-test', 'Book.rng'," + RNG_DATA1 + ")";
        @SuppressWarnings("unused")
        ResourceSet result2 = executeQuery(query2);

        String data1 = "xmldb:store('/db/validate-test', 'valid.xml'," + XML_DATA1 + ")";
        @SuppressWarnings("unused")
        ResourceSet result3 = executeQuery(data1);

        String data2 = "xmldb:store('/db/validate-test', 'invalid.xml'," + XML_DATA2 + ")";
        @SuppressWarnings("unused")
        ResourceSet result4 = executeQuery(data2);
    }

    @Test
    public void onvdl_valid() throws XPathException, IOException, XpathException, SAXException, XMLDBException {

        String query = "let $a := " + XML_DATA1 +
                "let $b := xs:anyURI('/db/validate-test/test.nvdl')" +
                "return " +
                "validation:jing-report($a,$b)";

        executeAndEvaluate(query,"valid");
    }

    @Test
    public void onvdl_invalid() throws XPathException, IOException, XpathException, SAXException, XMLDBException {

        String query = "let $a := <test/>" +
                    "let $b := xs:anyURI('/db/validate-test/test.nvdl')" +
                    "return " +
                    "validation:jing-report($a,$b)";
        executeAndEvaluate(query,"invalid");
    }


    @Test
    public void onvdl_stored_valid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jing-report( " +
                "doc('/db/validate-test/valid.xml'), " +
                "doc('/db/validate-test/test.nvdl') )";

        executeAndEvaluate(query,"valid");
    }

    @Test
    public void onvdl_stored_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jing-report( " +
                "doc('/db/validate-test/invalid.xml'), " +
                "doc('/db/validate-test/test.nvdl') )";

        executeAndEvaluate(query,"invalid");
    }

    @Test
    public void onvdl_anyuri_valid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jing-report( " +
                "xs:anyURI('xmldb:exist:///db/validate-test/valid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/validate-test/test.nvdl') )";

        executeAndEvaluate(query,"valid");
    }

    @Test
    public void onvdl_anyuri_invalid() throws XMLDBException, SAXException, XpathException, IOException {
        String query = "validation:jing-report( " +
                "xs:anyURI('xmldb:exist:///db/validate-test/invalid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/validate-test/test.nvdl') )";

        executeAndEvaluate(query,"invalid");
    }

    @Test
    public void onvdl_anyuri_valid_boolean() throws XMLDBException {
        String query = "validation:jing( " +
                "xs:anyURI('xmldb:exist:///db/validate-test/valid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/validate-test/test.nvdl') )";

        ResourceSet results = executeQuery(query);
        assertEquals(1, results.getSize());
        assertEquals(query, "true",
                results.getResource(0).getContent().toString());
    }

    private void executeAndEvaluate(String query, String expectedValue) throws XMLDBException, SAXException, IOException, XpathException {
        ResourceSet results = executeQuery(query);
        assertEquals(1, results.getSize());

        String r = (String) results.getResource(0).getContent();

        assertXpathEvaluatesTo(expectedValue, "//status/text()", r);
    }
}
