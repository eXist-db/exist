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
package org.exist.xmldb;

import java.net.URI;
import java.net.URISyntaxException;

import org.exist.test.TestConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class XmldbURITest {

    @Test
    void xmldbURIConstructors() throws URISyntaxException {
        XmldbURI.xmldbUriFor(".");
        XmldbURI.xmldbUriFor("..");
        XmldbURI.xmldbUriFor("/db");
        XmldbURI.xmldbUriFor("xmldb:exist:///db");
        XmldbURI.xmldbUriFor("xmldb:exist://localhost/db");

        XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/db");
        XmldbURI.xmldbUriFor("//localhost:8080/db");
        XmldbURI.xmldbUriFor("./db");
        XmldbURI.xmldbUriFor("../db");
        XmldbURI.xmldbUriFor("/db/test");
        XmldbURI.xmldbUriFor("xmldb:exist:///db/test");
        XmldbURI.xmldbUriFor("xmldb:exist://localhost/db/test");
        XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/db/test");
        XmldbURI.xmldbUriFor("//localhost:8080/db/test");
        XmldbURI.xmldbUriFor("./");
        XmldbURI.xmldbUriFor("../");
        XmldbURI.xmldbUriFor("/db/");
        XmldbURI.xmldbUriFor("xmldb:exist:///db/");
        XmldbURI.xmldbUriFor("xmldb:exist://localhost/db/");
        XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/db/");
        XmldbURI.xmldbUriFor("//localhost:8080/db/");
        //XXX: this MUST work or no MS OS support at all
        //XmldbURI.xmldbUriFor("D:\\workspace\\");

        XmldbURI.xmldbUriFor("xmldb:///db/");
        XmldbURI.xmldbUriFor("xmldb:///db/test");

        XmldbURI.xmldbUriFor("xmldb:/db/");
        XmldbURI.xmldbUriFor("xmldb:/db/test");
    }

    @Test
    void failingXmldbURIConstructors() {
        try{
            XmldbURI.xmldbUriFor("exist:///db");
            fail("Invalid constructor threw no exception!");
        } catch (URISyntaxException e) {
        }
        try{
            XmldbURI.xmldbUriFor("exist://localhost/db");
            fail("Invalid constructor threw no exception!");
        } catch (URISyntaxException e) {
        }
        try{
            XmldbURI.xmldbUriFor("exist://localhost:8080/db");
            fail("Invalid constructor threw no exception!");
        } catch (URISyntaxException e) {
        }
        try{
            XmldbURI.xmldbUriFor("[");
            fail("Invalid constructor threw no exception!");
        } catch (URISyntaxException e) {
        }
    }

    @Test
    void xmldbURIConstructor1() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///db");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertNull(xmldbURI.getHost());
        assertEquals(-1, xmldbURI.getPort());
        assertNull(xmldbURI.getContext());
        assertEquals("/db", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_LOCAL, xmldbURI.getApiName());
        xmldbURI = XmldbURI.create("xmldb:exist:///db");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertNull(xmldbURI.getHost());
        assertEquals(-1, xmldbURI.getPort());
        assertNull(xmldbURI.getContext());
        assertEquals("/db", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_LOCAL, xmldbURI.getApiName());
    }

    @Test
    void xmldbURIConstructor2() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/xmlrpc");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertEquals("localhost", xmldbURI.getHost());
        assertEquals("8080", "" + xmldbURI.getPort());
        assertEquals("/exist/xmlrpc", xmldbURI.getContext());
        assertEquals("", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_XMLRPC, xmldbURI.getApiName());
        xmldbURI = XmldbURI.create("xmldb:exist://localhost:8080/exist/xmlrpc");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertEquals("localhost", xmldbURI.getHost());
        assertEquals("8080", "" + xmldbURI.getPort());
        assertEquals("/exist/xmlrpc", xmldbURI.getContext());
        assertEquals("", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_XMLRPC, xmldbURI.getApiName());
    }

    @Test
    void xmldbURIConstructor3() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertEquals("localhost", xmldbURI.getHost());
        assertEquals("8080", "" + xmldbURI.getPort());
        assertEquals("/xmlrpc", xmldbURI.getContext());
        assertEquals("", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_XMLRPC, xmldbURI.getApiName());
        xmldbURI = XmldbURI.create("xmldb:exist://localhost:8080/xmlrpc");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertEquals("localhost", xmldbURI.getHost());
        assertEquals("8080", "" + xmldbURI.getPort());
        assertEquals("/xmlrpc", xmldbURI.getContext());
        assertEquals("", xmldbURI.getCollectionPath());
        assertEquals("xmlrpc", xmldbURI.getApiName());
    }

    @Test
    void xmldbURIConstructor4() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/webdav");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertEquals("localhost", xmldbURI.getHost());
        assertEquals("8080", "" + xmldbURI.getPort());
        assertEquals("/webdav", xmldbURI.getContext());
        assertEquals("", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_WEBDAV, xmldbURI.getApiName());
        xmldbURI = XmldbURI.create("xmldb:exist://localhost:8080/webdav");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertEquals("localhost", xmldbURI.getHost());
        assertEquals("8080", "" + xmldbURI.getPort());
        assertEquals("/webdav", xmldbURI.getContext());
        assertEquals("", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_WEBDAV, xmldbURI.getApiName());
    }

    @Test
    void xmldbURIConstructor5() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///db/");
        assertEquals("/db", xmldbURI.getCollectionPath());
        //assertEquals("xmldb:exist:///db", xmldbURI.toString());
        xmldbURI = XmldbURI.create("xmldb:exist:///db/");
        assertEquals("/db", xmldbURI.getCollectionPath());
        //assertEquals("xmldb:exist:///db", xmldbURI.toString());
    }

    @Test
    void xmldbURIConstructor6() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist2://localhost:8080/webdav/db");
        assertEquals("exist2", xmldbURI.getInstanceName());
        xmldbURI = XmldbURI.create("xmldb:exist2://localhost:8080/webdav/db");
        assertEquals("exist2", xmldbURI.getInstanceName());
    }

    @Test
    void xmldbURIConstructor7() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertEquals("localhost", xmldbURI.getHost());
        assertEquals("8080", "" + xmldbURI.getPort());
        assertEquals("/xmlrpc", xmldbURI.getContext());
        assertEquals("/db", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_XMLRPC, xmldbURI.getApiName());
        xmldbURI = XmldbURI.create("xmldb:exist://localhost:8080/xmlrpc/db");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertEquals("localhost", xmldbURI.getHost());
        assertEquals("8080", "" + xmldbURI.getPort());
        assertEquals("/xmlrpc", xmldbURI.getContext());
        assertEquals("/db", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_XMLRPC, xmldbURI.getApiName());
    }

    @Test
    void xmldbURIConstructor8() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/webdav/db");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertEquals("localhost", xmldbURI.getHost());
        assertEquals("8080", "" + xmldbURI.getPort());
        assertEquals("/webdav", xmldbURI.getContext());
        assertEquals("/db", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_WEBDAV, xmldbURI.getApiName());
        xmldbURI = XmldbURI.create("xmldb:exist://localhost:8080/webdav/db");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertEquals("localhost", xmldbURI.getHost());
        assertEquals("8080", "" + xmldbURI.getPort());
        assertEquals("/webdav", xmldbURI.getContext());
        assertEquals("/db", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_WEBDAV, xmldbURI.getApiName());
    }

    @Test
    void xmldbURIConstructor9() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/webdav/db");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertEquals("localhost", xmldbURI.getHost());
        assertEquals("8080", "" + xmldbURI.getPort());
        assertEquals("/xmlrpc/webdav", xmldbURI.getContext());
        assertEquals("/db", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_WEBDAV, xmldbURI.getApiName());
        xmldbURI = XmldbURI.create("xmldb:exist://localhost:8080/xmlrpc/webdav/db");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertEquals("localhost", xmldbURI.getHost());
        assertEquals("8080", "" + xmldbURI.getPort());
        assertEquals("/xmlrpc/webdav", xmldbURI.getContext());
        assertEquals("/db", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_WEBDAV, xmldbURI.getApiName());
    }

    @Test
    void xmldbURIConstructor10() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/webdav/xmlrpc/db");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertEquals("localhost", xmldbURI.getHost());
        assertEquals("8080", "" + xmldbURI.getPort());
        assertEquals("/webdav/xmlrpc", xmldbURI.getContext());
        assertEquals("/db", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_XMLRPC, xmldbURI.getApiName());
        xmldbURI = XmldbURI.create("xmldb:exist://localhost:8080/webdav/xmlrpc/db");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertEquals("localhost", xmldbURI.getHost());
        assertEquals("8080", "" + xmldbURI.getPort());
        assertEquals("/webdav/xmlrpc", xmldbURI.getContext());
        assertEquals("/db", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_XMLRPC, xmldbURI.getApiName());
    }

    @Test
    void xmldbURIConstructor11() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost/db");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertEquals("localhost", xmldbURI.getHost());
        assertEquals(-1, xmldbURI.getPort());
        assertNull(xmldbURI.getContext());
        assertEquals("/db", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_REST, xmldbURI.getApiName());
    }

    @Test
    void xmldbURIConstructor12() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///db/aa/bb/ccc");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertNull(xmldbURI.getHost());
        assertEquals(-1, xmldbURI.getPort());
        assertNull(xmldbURI.getContext());
        assertEquals("/db/aa/bb/ccc", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_LOCAL, xmldbURI.getApiName());
        xmldbURI = XmldbURI.create("xmldb:exist:///db/aa/bb/ccc");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertEquals(-1, xmldbURI.getPort());
        assertNull(xmldbURI.getContext());
        assertEquals("/db/aa/bb/ccc", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_LOCAL, xmldbURI.getApiName());
    }

    /*
     * These are no longer faulty
     */
    @Test
    void xmldbURIConstructor13() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///db?param=value");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertNull(xmldbURI.getHost());
        assertEquals(-1, xmldbURI.getPort());
        assertNull(xmldbURI.getContext());
        assertEquals("/db", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_LOCAL, xmldbURI.getApiName());
        assertEquals("param=value",xmldbURI.getQuery());
        xmldbURI = XmldbURI.create("xmldb:exist:///db?param=value");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertNull(xmldbURI.getHost());
        assertEquals(-1, xmldbURI.getPort());
        assertNull(xmldbURI.getContext());
        assertEquals("/db", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_LOCAL, xmldbURI.getApiName());
        assertEquals("param=value",xmldbURI.getQuery());
    }

    @Test
    void xmldbURIConstructor14() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///db#123");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertNull(xmldbURI.getHost());
        assertEquals(-1, xmldbURI.getPort());
        assertNull(xmldbURI.getContext());
        assertEquals("/db", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_LOCAL, xmldbURI.getApiName());
        assertEquals("123",xmldbURI.getFragment());
        xmldbURI = XmldbURI.create("xmldb:exist:///db#123");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertNull(xmldbURI.getHost());
        assertEquals(-1, xmldbURI.getPort());
        assertNull(xmldbURI.getContext());
        assertEquals("/db", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_LOCAL, xmldbURI.getApiName());
        assertEquals("123",xmldbURI.getFragment());
    }

    @Test
    void xmldbURIConstructor15() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///db?param=value#123");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertNull(xmldbURI.getHost());
        assertEquals(-1, xmldbURI.getPort());
        assertNull(xmldbURI.getContext());
        assertEquals("/db", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_LOCAL, xmldbURI.getApiName());
        assertEquals("123",xmldbURI.getFragment());
        assertEquals("param=value",xmldbURI.getQuery());
        xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///db?param=value#123");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertNull(xmldbURI.getHost());
        assertEquals(-1, xmldbURI.getPort());
        assertNull(xmldbURI.getContext());
        assertEquals("/db", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_LOCAL, xmldbURI.getApiName());
        assertEquals("123",xmldbURI.getFragment());
        assertEquals("param=value",xmldbURI.getQuery());
    }

    @Test
    void xmldbURIConstructor16() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:///db/aa/bb/ccc");
        assertEquals("xmldb", xmldbURI.getInstanceName());
        assertNull(xmldbURI.getHost());
        assertEquals(-1, xmldbURI.getPort());
        assertNull(xmldbURI.getContext());
        assertEquals("/db/aa/bb/ccc", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_LOCAL, xmldbURI.getApiName());
    }

    @Test
    void xmldbURIFaultyConstructor1() {
        boolean exceptionThrown = false;
        try{
            @SuppressWarnings("unused")
			XmldbURI xmldbURI = XmldbURI.xmldbUriFor("exist:///db");
        } catch (URISyntaxException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        exceptionThrown = false;
        try{
            @SuppressWarnings("unused")
            XmldbURI xmldbURI = XmldbURI.create("exist:///db");
        } catch (Exception e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    void xmldbURIFaultyConstructor3() {
        boolean exceptionThrown = false;
        try{
            @SuppressWarnings("unused")
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://");
        } catch (URISyntaxException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        exceptionThrown = false;
        try{
            @SuppressWarnings("unused")
            XmldbURI xmldbURI = XmldbURI.create("xmldb:exist://");
        } catch (Exception e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    void xmldbURIFaultyConstructor4() {
        boolean exceptionThrown = false;
        try{
            @SuppressWarnings("unused")
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://");
        } catch (URISyntaxException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        exceptionThrown = false;
        try{
            @SuppressWarnings("unused")
            XmldbURI xmldbURI = XmldbURI.create("xmldb:exist://");
        } catch (Exception e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }


    /*
     * These test are irrelevant for immutable URIs
     */
    /*
    public void testXmldbURIChangePart1() {
        try {
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:" + jettyPort + "/xmlrpc/webdav/db");
            xmldbURI.setInstanceName("exist2");
            assertEquals("xmldb:exist2://localhost:" + jettyPort + "/xmlrpc/webdav/db", xmldbURI.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIChangePart2() {
        try {
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:" + jettyPort + "/xmlrpc/webdav/db");
            xmldbURI.setHost("remotehost");
            assertEquals("xmldb:exist://remotehost:" + jettyPort + "/xmlrpc/webdav/db", xmldbURI.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIChangePart3() {
        try {
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:" + jettyPort + "/xmlrpc/webdav/db");
            xmldbURI.setPort(jettyPort);
            assertEquals("xmldb:exist://localhost:" + jettyPort + "/xmlrpc/webdav/db", xmldbURI.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIChangePart4() {
        try {
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:" + jettyPort + "/xmlrpc/webdav/db");
            xmldbURI.setPort(-1);
            assertEquals("xmldb:exist://localhost/xmlrpc/webdav/db", xmldbURI.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIChangePart5() {
        try {
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:" + jettyPort + "/xmlrpc/webdav/db");
            xmldbURI.setContext("/webdav");
            assertEquals("xmldb:exist://localhost:" + jettyPort + "/webdav/db", xmldbURI.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    */

    @Test
    void xmldbURICompareTo1() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist:///db");
        XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist:///db/collection1");
        assertTrue(xmldbURI1.compareTo(xmldbURI2) < 0);
        assertTrue(xmldbURI2.compareTo(xmldbURI1) > 0);
    }

    @Test
    void xmldbURICompareTo2() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist:///db/collection1");
        XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist:///db/collection1");
        assertEquals(0, xmldbURI1.compareTo(xmldbURI2));
    }

    @Test
    void xmldbURICompareTo3() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist:///collection1");
        XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist:///collection2");
        assertTrue(xmldbURI1.compareTo(xmldbURI2) < 0);
        assertTrue(xmldbURI2.compareTo(xmldbURI1) > 0);
    }

    @Test
    void xmldbURICompareTo4() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist1:///db");
        XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist2:///db");
        assertTrue(xmldbURI1.compareTo(xmldbURI2) < 0);
        assertTrue(xmldbURI2.compareTo(xmldbURI1) > 0);
    }

    @Test
    void xmldbURIEquals1() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist:///db");
        XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist:///db");
        assertEquals(xmldbURI1, xmldbURI2);
    }

    @Test
    void xmldbURIEquals2() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/db");
        XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/db");
        assertEquals(xmldbURI1, xmldbURI2);
    }

    @Test
    void xmldbURIEquals3() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/xmlrpc");
        XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/xmlrpc");
        assertEquals(xmldbURI1, xmldbURI2);
    }

    @Test
    void xmldbURIEquals4() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/xmlrpc/db");
        XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/xmlrpc/db");
        assertEquals(xmldbURI1, xmldbURI2);
    }

    @Test
    void xmldbURIEquals5() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist1://localhost:8080/db");
        XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist1://localhost:8080/db");
        assertEquals(xmldbURI1, xmldbURI2);
    }

    @Test
    void xmldbURIIsAbsolute1() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///db");
        assertTrue(xmldbURI.isAbsolute());
    }

    @Test
    void xmldbURIIsAbsolute2() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor(".");
        assertFalse(xmldbURI.isAbsolute());
    }

    @Test
    void xmldbURIIsAbsolute3() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("..");
        assertFalse(xmldbURI.isAbsolute());
    }

    @Test
    void xmldbURIIsContextAbsolute1() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/exist/xmlrpc/db");
        assertTrue(xmldbURI1.isContextAbsolute());
    }

    @Test
    void xmldbURINormalizeContext1() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/./xmlrpc/db");
        XmldbURI xmldbURI2 = xmldbURI1.normalizeContext();
        assertEquals("xmldb:exist://localhost:8080/exist/xmlrpc/db", xmldbURI2.toString());
    }

    @Test
    void xmldbURINormalizeContext2() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/../xmlrpc/db");
        XmldbURI xmldbURI2 = xmldbURI1.normalizeContext();
        assertEquals("xmldb:exist://localhost:8080/xmlrpc/db", xmldbURI2.toString());
    }

    @Test
    void xmldbURINormalizeContext3() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist:///db");
        XmldbURI xmldbURI2  = xmldbURI1.normalizeContext();
        assertEquals("xmldb:exist:///db", xmldbURI2.toString());
    }

    @Test
    void xmldbURIRelativizeContext1() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/exist/xmlrpc/db");
        URI uri = new URI("/exist/xmlrpc");
        assertEquals("/exist/xmlrpc", xmldbURI.relativizeContext(uri).toString());
    }

    @Test
    void xmldbURIRelativizeContext2() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/xmlrpc/db");
        URI uri = new URI("/exist/exist/xmlrpc");
        assertEquals("/exist/exist/xmlrpc", xmldbURI.relativizeContext(uri).toString());
    }

    @Test
    void xmldbURIRelativizeContext3() {
        @SuppressWarnings("unused")
        boolean exceptionThrown = false;
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///db");
            URI uri = new URI("/db");
            assertEquals("/exist/exist/xmlrpc", xmldbURI.relativizeContext(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        } catch (NullPointerException e) {
            exceptionThrown = true;
        }
    }

    @Test
    void xmldbURIResolveContext1() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/a/b/xmlrpc/db");
        URI uri = new URI("..");
        //Strange but it's like this
        assertEquals("/a/b/", xmldbURI.resolveContext(uri).toString());
    }

    @Test
    void xmldbURIResolveContext2() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/a/b/xmlrpc/db");
        URI uri = new URI("../..");
        //Strange but it's like this
        assertEquals("/a/", xmldbURI.resolveContext(uri).toString());
    }

    @Test
    void xmldbURIResolveContext3() {
        boolean exceptionThrown = false;
        try{
            //Null context here ;-)
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///a/db");
            URI uri = new URI("..");
            xmldbURI.resolveContext(uri);
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        } catch (NullPointerException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    void xmldbURIResolveContext4() throws URISyntaxException {
        //Null context here ;-)
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db");
        //Up and up...
        URI uri = new URI("/../../..");
        //Strange but it's like this
        assertEquals("/../../..", xmldbURI.resolveContext(uri).toString());
    }

    @Test
    void xmldbURIIsCollectionNameAbsolute1() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db");
        assertTrue(xmldbURI1.isCollectionPathAbsolute());
    }

    @Test
    void xmldbURINormalizeCollectionName1() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db/./collection");
        assertEquals("xmldb:exist://localhost:8080/xmlrpc/db/collection", xmldbURI.normalizeCollectionPath().toString());
    }

    @Test
    void xmldbURINormalizeCollectionName2() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db/../collection");
        XmldbURI xmldbURI2 = xmldbURI1.normalizeCollectionPath();
        assertEquals("xmldb:exist://localhost:8080/xmlrpc/collection", xmldbURI2.toString());
    }

    @Test
    void xmldbURINormalizeCollectionName3() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist:///");
        XmldbURI xmldbURI2  = xmldbURI1.normalizeCollectionPath();
        assertEquals("xmldb:exist:///", xmldbURI2.toString());
    }

    @Test
    void xmldbURIRelativizeCollectionName1() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db/db/collection");
        URI uri = new URI("/db/collection");
        assertEquals("/db/collection", xmldbURI.relativizeCollectionPath(uri).toString());
    }

    @Test
    void xmldbURIRelativizeCollectionName2() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db/collection");
        URI uri = new URI("/db/db/collection");
        assertEquals("/db/db/collection", xmldbURI.relativizeCollectionPath(uri).toString());
    }

    @Test
    void xmldbURIRelativizeCollectionName3() {
        @SuppressWarnings("unused")
        boolean exceptionThrown = false;
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///");
            URI uri = new URI("/");
            assertEquals("", xmldbURI.relativizeCollectionPath(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        } catch (NullPointerException e) {
            exceptionThrown = true;
        }
    }

    @Test
    void xmldbURIResolveCollectionName1() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db/a/b");
        URI uri = new URI("..");
        assertEquals("/db/a/", xmldbURI.resolveCollectionPath(uri).toString());
    }

    @Test
    void xmldbURIResolveCollectionName2() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db/a/b");
        URI uri = new URI("../..");
        assertEquals("/db/", xmldbURI.resolveCollectionPath(uri).toString());
    }

    @Test
    void xmldbURIResolveCollectionName3() throws URISyntaxException {
        //Null context here ;-)
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///");
        URI uri = new URI("..");
        assertEquals("/..", xmldbURI.resolveCollectionPath(uri).toString());
    }

    @Test
    void xmldbURIResolveCollectionName4() throws URISyntaxException {
        //Null context here ;-)
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db");
        //Up and up...
        URI uri = new URI("/../../..");
        //Strange but it's like this
        assertEquals("/../../..", xmldbURI.resolveCollectionPath(uri).toString());
    }

    @Test
    void xmldbURICollectionPathEncoding1() throws URISyntaxException {
        //Should return decoded path
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///xmlrpc/"+TestConstants.SPECIAL_NAME);
        assertEquals(xmldbURI.getCollectionPath(),"/xmlrpc/"+TestConstants.DECODED_SPECIAL_NAME);
    }

    @Test
    void xmldbURICollectionPathEncoding2() throws URISyntaxException {
        //Should return encoded path
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///xmlrpc/"+TestConstants.SPECIAL_NAME);
        assertEquals(xmldbURI.getRawCollectionPath(),"/xmlrpc/"+TestConstants.SPECIAL_NAME);
    }

    @Test
    void xmldbURILastSegment() throws URISyntaxException {
        //Should return encoded path
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///xmlrpc/test/"+TestConstants.SPECIAL_NAME);
        assertEquals(TestConstants.SPECIAL_URI, xmldbURI.lastSegment());

        xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///xmlrpc/"+TestConstants.SPECIAL_NAME);
        assertEquals(TestConstants.SPECIAL_URI, xmldbURI.lastSegment());

        xmldbURI = XmldbURI.xmldbUriFor("test/"+TestConstants.SPECIAL_NAME);
        assertEquals(TestConstants.SPECIAL_URI, xmldbURI.lastSegment());

        xmldbURI = XmldbURI.xmldbUriFor("test/"+TestConstants.SPECIAL_NAME+"/");
        assertEquals(TestConstants.SPECIAL_URI, xmldbURI.lastSegment());

        xmldbURI = XmldbURI.xmldbUriFor("/test/"+TestConstants.SPECIAL_NAME+"/");
        assertEquals(TestConstants.SPECIAL_URI, xmldbURI.lastSegment());

        xmldbURI = XmldbURI.xmldbUriFor(TestConstants.SPECIAL_NAME+"/");
        assertEquals(TestConstants.SPECIAL_URI, xmldbURI.lastSegment());

        assertEquals(TestConstants.SPECIAL_URI, TestConstants.SPECIAL_URI.lastSegment());
        assertEquals(XmldbURI.EMPTY_URI, XmldbURI.EMPTY_URI.lastSegment());
        assertEquals(XmldbURI.EMPTY_URI, XmldbURI.create("/").lastSegment());
    }

    @Test
    void xmldbURIRemoveLastSegment() throws URISyntaxException {
        //Should return encoded path
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///xmlrpc/test/"+TestConstants.SPECIAL_NAME);
        assertEquals(xmldbURI.removeLastSegment(),XmldbURI.xmldbUriFor("xmldb:exist:///xmlrpc/test"));

        xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///xmlrpc/test/"+TestConstants.SPECIAL_NAME+"/");
        assertEquals(xmldbURI.removeLastSegment(),XmldbURI.xmldbUriFor("xmldb:exist:///xmlrpc/test"));

        xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///xmlrpc/"+TestConstants.SPECIAL_NAME);
        assertEquals(xmldbURI.removeLastSegment(),XmldbURI.xmldbUriFor("xmldb:exist:///xmlrpc"));

        xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///xmlrpc/"+TestConstants.SPECIAL_NAME+"/");
        assertEquals(xmldbURI.removeLastSegment(),XmldbURI.xmldbUriFor("xmldb:exist:///xmlrpc"));

        xmldbURI = XmldbURI.xmldbUriFor("test/"+TestConstants.SPECIAL_NAME);
        assertEquals(xmldbURI.removeLastSegment(),XmldbURI.xmldbUriFor("test"));

        xmldbURI = XmldbURI.xmldbUriFor("test/"+TestConstants.SPECIAL_NAME+"/");
        assertEquals(xmldbURI.removeLastSegment(),XmldbURI.xmldbUriFor("test"));

        xmldbURI = XmldbURI.xmldbUriFor("/test/"+TestConstants.SPECIAL_NAME);
        assertEquals(xmldbURI.removeLastSegment(),XmldbURI.xmldbUriFor("/test"));

        xmldbURI = XmldbURI.xmldbUriFor("/test/"+TestConstants.SPECIAL_NAME+"/");
        assertEquals(xmldbURI.removeLastSegment(),XmldbURI.xmldbUriFor("/test"));

        xmldbURI = XmldbURI.xmldbUriFor(TestConstants.SPECIAL_NAME+"/");
        assertEquals(XmldbURI.EMPTY_URI, xmldbURI.removeLastSegment());

        assertEquals(TestConstants.SPECIAL_URI.removeLastSegment(),XmldbURI.xmldbUriFor(""));
    }

    @Test
    void appenders(){
        
        String   append_txt_1 = "test/new_test.xml";
        XmldbURI root         = XmldbURI.ROOT_COLLECTION_URI;
        XmldbURI append_uri_1 = root.append(append_txt_1);
        assertEquals( root +"/"+ append_txt_1 , append_uri_1.toString() );

        assertEquals( 
                    TestConstants.TEST_COLLECTION_URI.toString() 
                    + "/" + TestConstants.TEST_BINARY_URI.toString() ,
                    (TestConstants.TEST_COLLECTION_URI.append(TestConstants.TEST_BINARY_URI)).toString()
                );
    }

    @Test
    void appendRelative() {
        final XmldbURI originalUri = XmldbURI.create("/db/colA/col1/col2/col3");
        
        final XmldbURI newUri = originalUri.append("../../../../colB/other");
        
        assertEquals("/db/colB/other", newUri.toString());
    }

    @Test
    void lastSegment() {
        XmldbURI uri = XmldbURI.create("/db/xmldb:something 1.xml");

        assertEquals("/db/xmldb:something%201.xml", uri.toString());

        assertEquals("xmldb:something%201.xml", uri.lastSegment().toString());
    }

    @Test
    void startsWith() {

        assertTrue(XmldbURI.create("/db/test").startsWith(XmldbURI.create("/db/test")));

        assertFalse(XmldbURI.create("/db/test").startsWith(XmldbURI.create("/db/test2")));

        assertTrue(XmldbURI.create("/db/test/db").startsWith(XmldbURI.create("/db/test")));
        assertTrue(XmldbURI.create("/db/test/db").startsWith(XmldbURI.create("/db/test/")));

        assertFalse(XmldbURI.create("/db/test_db").startsWith(XmldbURI.create("/db/test")));
    }
}
