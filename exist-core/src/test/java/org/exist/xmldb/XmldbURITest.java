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
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class XmldbURITest {

    @Test
    public void xmldbURIConstructors() throws URISyntaxException {
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
    public void failingXmldbURIConstructors() {
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
    public void xmldbURIConstructor1() throws URISyntaxException {
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
    public void xmldbURIConstructor2() throws URISyntaxException {
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
    public void xmldbURIConstructor3() throws URISyntaxException {
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
    public void xmldbURIConstructor4() throws URISyntaxException {
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
    public void xmldbURIConstructor5() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///db/");
        assertEquals("/db", xmldbURI.getCollectionPath());
        //assertEquals("xmldb:exist:///db", xmldbURI.toString());
        xmldbURI = XmldbURI.create("xmldb:exist:///db/");
        assertEquals("/db", xmldbURI.getCollectionPath());
        //assertEquals("xmldb:exist:///db", xmldbURI.toString());
    }

    @Test
    public void xmldbURIConstructor6() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist2://localhost:8080/webdav/db");
        assertEquals("exist2", xmldbURI.getInstanceName());
        xmldbURI = XmldbURI.create("xmldb:exist2://localhost:8080/webdav/db");
        assertEquals("exist2", xmldbURI.getInstanceName());
    }

    @Test
    public void xmldbURIConstructor7() throws URISyntaxException {
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
    public void xmldbURIConstructor8() throws URISyntaxException {
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
    public void xmldbURIConstructor9() throws URISyntaxException {
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
    public void xmldbURIConstructor10() throws URISyntaxException {
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
    public void xmldbURIConstructor11() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost/db");
        assertEquals("exist", xmldbURI.getInstanceName());
        assertEquals("localhost", xmldbURI.getHost());
        assertEquals(-1, xmldbURI.getPort());
        assertNull(xmldbURI.getContext());
        assertEquals("/db", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_REST, xmldbURI.getApiName());
    }

    @Test
    public void xmldbURIConstructor12() throws URISyntaxException {
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
    public void xmldbURIConstructor13() throws URISyntaxException {
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
    public void xmldbURIConstructor14() throws URISyntaxException {
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
    public void xmldbURIConstructor15() throws URISyntaxException {
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
    public void xmldbURIConstructor16() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:///db/aa/bb/ccc");
        assertEquals("xmldb", xmldbURI.getInstanceName());
        assertNull(xmldbURI.getHost());
        assertEquals(-1, xmldbURI.getPort());
        assertNull(xmldbURI.getContext());
        assertEquals("/db/aa/bb/ccc", xmldbURI.getCollectionPath());
        assertEquals(XmldbURI.API_LOCAL, xmldbURI.getApiName());
    }

    @Test
    public void xmldbURIFaultyConstructor1() {
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
    public void xmldbURIFaultyConstructor3() {
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
    public void xmldbURIFaultyConstructor4() {
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
    public void xmldbURICompareTo1() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist:///db");
        XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist:///db/collection1");
        assertTrue(xmldbURI1.compareTo(xmldbURI2) < 0);
        assertTrue(xmldbURI2.compareTo(xmldbURI1) > 0);
    }

    @Test
    public void xmldbURICompareTo2() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist:///db/collection1");
        XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist:///db/collection1");
        assertEquals(0, xmldbURI1.compareTo(xmldbURI2));
    }

    @Test
    public void xmldbURICompareTo3() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist:///collection1");
        XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist:///collection2");
        assertTrue(xmldbURI1.compareTo(xmldbURI2) < 0);
        assertTrue(xmldbURI2.compareTo(xmldbURI1) > 0);
    }

    @Test
    public void xmldbURICompareTo4() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist1:///db");
        XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist2:///db");
        assertTrue(xmldbURI1.compareTo(xmldbURI2) < 0);
        assertTrue(xmldbURI2.compareTo(xmldbURI1) > 0);
    }

    @Test
    public void xmldbURIEquals1() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist:///db");
        XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist:///db");
        assertTrue(xmldbURI1.equals(xmldbURI2));
    }

    @Test
    public void xmldbURIEquals2() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/db");
        XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/db");
        assertTrue(xmldbURI1.equals(xmldbURI2));
    }

    @Test
    public void xmldbURIEquals3() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/xmlrpc");
        XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/xmlrpc");
        assertTrue(xmldbURI1.equals(xmldbURI2));
    }

    @Test
    public void xmldbURIEquals4() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/xmlrpc/db");
        XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/xmlrpc/db");
        assertTrue(xmldbURI1.equals(xmldbURI2));
    }

    @Test
    public void xmldbURIEquals5() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist1://localhost:8080/db");
        XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist1://localhost:8080/db");
        assertTrue(xmldbURI1.equals(xmldbURI2));
    }

    @Test
    public void xmldbURIIsAbsolute1() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///db");
        assertTrue(xmldbURI.isAbsolute());
    }

    @Test
    public void xmldbURIIsAbsolute2() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor(".");
        assertFalse(xmldbURI.isAbsolute());
    }

    @Test
    public void xmldbURIIsAbsolute3() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("..");
        assertFalse(xmldbURI.isAbsolute());
    }

    @Test
    public void xmldbURIIsContextAbsolute1() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/exist/xmlrpc/db");
        assertTrue(xmldbURI1.isContextAbsolute());
    }

    @Test
    public void xmldbURINormalizeContext1() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/./xmlrpc/db");
        XmldbURI xmldbURI2 = xmldbURI1.normalizeContext();
        assertEquals("xmldb:exist://localhost:8080/exist/xmlrpc/db", xmldbURI2.toString());
    }

    @Test
    public void xmldbURINormalizeContext2() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/../xmlrpc/db");
        XmldbURI xmldbURI2 = xmldbURI1.normalizeContext();
        assertEquals("xmldb:exist://localhost:8080/xmlrpc/db", xmldbURI2.toString());
    }
    
    @Test
    public void xmldbURINormalizeContext3() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist:///db");
        XmldbURI xmldbURI2  = xmldbURI1.normalizeContext();
        assertEquals("xmldb:exist:///db", xmldbURI2.toString());
    }

    @Test
    public void xmldbURIRelativizeContext1() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/exist/xmlrpc/db");
        URI uri = new URI("/exist/xmlrpc");
        assertEquals("/exist/xmlrpc", xmldbURI.relativizeContext(uri).toString());
    }

    @Test
    public void xmldbURIRelativizeContext2() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/xmlrpc/db");
        URI uri = new URI("/exist/exist/xmlrpc");
        assertEquals("/exist/exist/xmlrpc", xmldbURI.relativizeContext(uri).toString());
    }

    @Test
    public void xmldbURIRelativizeContext3() {
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
    public void xmldbURIResolveContext1() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/a/b/xmlrpc/db");
        URI uri = new URI("..");
        //Strange but it's like this
        assertEquals("/a/b/", xmldbURI.resolveContext(uri).toString());
    }
    
    @Test
    public void xmldbURIResolveContext2() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/a/b/xmlrpc/db");
        URI uri = new URI("../..");
        //Strange but it's like this
        assertEquals("/a/", xmldbURI.resolveContext(uri).toString());
    }

    @Test
    public void xmldbURIResolveContext3() {
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
    public void xmldbURIResolveContext4() throws URISyntaxException {
        //Null context here ;-)
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db");
        //Up and up...
        URI uri = new URI("/../../..");
        //Strange but it's like this
        assertEquals("/../../..", xmldbURI.resolveContext(uri).toString());
    }

    @Test
    public void xmldbURIIsCollectionNameAbsolute1() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db");
        assertTrue(xmldbURI1.isCollectionPathAbsolute());
    }

    @Test
    public void xmldbURINormalizeCollectionName1() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db/./collection");
        assertEquals("xmldb:exist://localhost:8080/xmlrpc/db/collection", xmldbURI.normalizeCollectionPath().toString());
    }

    @Test
    public void xmldbURINormalizeCollectionName2() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db/../collection");
        XmldbURI xmldbURI2 = xmldbURI1.normalizeCollectionPath();
        assertEquals("xmldb:exist://localhost:8080/xmlrpc/collection", xmldbURI2.toString());
    }

    @Test
    public void xmldbURINormalizeCollectionName3() throws URISyntaxException {
        XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist:///");
        XmldbURI xmldbURI2  = xmldbURI1.normalizeCollectionPath();
        assertEquals("xmldb:exist:///", xmldbURI2.toString());
    }

    @Test
    public void xmldbURIRelativizeCollectionName1() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db/db/collection");
        URI uri = new URI("/db/collection");
        assertEquals("/db/collection", xmldbURI.relativizeCollectionPath(uri).toString());
    }

    @Test
    public void xmldbURIRelativizeCollectionName2() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db/collection");
        URI uri = new URI("/db/db/collection");
        assertEquals("/db/db/collection", xmldbURI.relativizeCollectionPath(uri).toString());
    }

    @Test
    public void xmldbURIRelativizeCollectionName3() {
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
    public void xmldbURIResolveCollectionName1() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db/a/b");
        URI uri = new URI("..");
        assertEquals("/db/a/", xmldbURI.resolveCollectionPath(uri).toString());
    }

    @Test
    public void xmldbURIResolveCollectionName2() throws URISyntaxException {
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db/a/b");
        URI uri = new URI("../..");
        assertEquals("/db/", xmldbURI.resolveCollectionPath(uri).toString());
    }

    @Test
    public void xmldbURIResolveCollectionName3() throws URISyntaxException {
        //Null context here ;-)
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///");
        URI uri = new URI("..");
        assertEquals("/..", xmldbURI.resolveCollectionPath(uri).toString());
    }

    @Test
    public void xmldbURIResolveCollectionName4() throws URISyntaxException {
        //Null context here ;-)
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db");
        //Up and up...
        URI uri = new URI("/../../..");
        //Strange but it's like this
        assertEquals("/../../..", xmldbURI.resolveCollectionPath(uri).toString());
    }

    @Test
    public void xmldbURICollectionPathEncoding1() throws URISyntaxException {
        //Should return decoded path
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///xmlrpc/"+TestConstants.SPECIAL_NAME);
        assertEquals(xmldbURI.getCollectionPath(),"/xmlrpc/"+TestConstants.DECODED_SPECIAL_NAME);
    }

    @Test
    public void xmldbURICollectionPathEncoding2() throws URISyntaxException {
        //Should return encoded path
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///xmlrpc/"+TestConstants.SPECIAL_NAME);
        assertEquals(xmldbURI.getRawCollectionPath(),"/xmlrpc/"+TestConstants.SPECIAL_NAME);
    }

    @Test
    public void xmldbURILastSegment() throws URISyntaxException {
        //Should return encoded path
        XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///xmlrpc/test/"+TestConstants.SPECIAL_NAME);
        assertEquals(xmldbURI.lastSegment(),TestConstants.SPECIAL_URI);

        xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///xmlrpc/"+TestConstants.SPECIAL_NAME);
        assertEquals(xmldbURI.lastSegment(),TestConstants.SPECIAL_URI);

        xmldbURI = XmldbURI.xmldbUriFor("test/"+TestConstants.SPECIAL_NAME);
        assertEquals(xmldbURI.lastSegment(),TestConstants.SPECIAL_URI);

        xmldbURI = XmldbURI.xmldbUriFor("test/"+TestConstants.SPECIAL_NAME+"/");
        assertEquals(xmldbURI.lastSegment(),TestConstants.SPECIAL_URI);

        xmldbURI = XmldbURI.xmldbUriFor("/test/"+TestConstants.SPECIAL_NAME+"/");
        assertEquals(xmldbURI.lastSegment(),TestConstants.SPECIAL_URI);

        xmldbURI = XmldbURI.xmldbUriFor(TestConstants.SPECIAL_NAME+"/");
        assertEquals(xmldbURI.lastSegment(),TestConstants.SPECIAL_URI);

        assertEquals(TestConstants.SPECIAL_URI.lastSegment(),TestConstants.SPECIAL_URI);
        assertEquals(XmldbURI.EMPTY_URI.lastSegment(),XmldbURI.EMPTY_URI);
        assertEquals(XmldbURI.create("/").lastSegment(),XmldbURI.EMPTY_URI);
    }

    @Test
    public void xmldbURIRemoveLastSegment() throws URISyntaxException {
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
        assertEquals(xmldbURI.removeLastSegment(),XmldbURI.EMPTY_URI);

        assertEquals(TestConstants.SPECIAL_URI.removeLastSegment(),XmldbURI.xmldbUriFor(""));
    }

    @Test
    public void appenders(){
        
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
    public void appendRelative() {
        final XmldbURI originalUri = XmldbURI.create("/db/colA/col1/col2/col3");
        
        final XmldbURI newUri = originalUri.append("../../../../colB/other");
        
        assertEquals("/db/colB/other", newUri.toString());
    }

    @Test
    public void lastSegment() {
        XmldbURI uri = XmldbURI.create("/db/xmldb:something 1.xml");

        assertEquals("/db/xmldb:something%201.xml", uri.toString());

        assertEquals("xmldb:something%201.xml", uri.lastSegment().toString());
    }

    @Test
    public void startsWith() {

        assertTrue(XmldbURI.create("/db/test").startsWith(XmldbURI.create("/db/test")));

        assertFalse(XmldbURI.create("/db/test").startsWith(XmldbURI.create("/db/test2")));

        assertTrue(XmldbURI.create("/db/test/db").startsWith(XmldbURI.create("/db/test")));
        assertTrue(XmldbURI.create("/db/test/db").startsWith(XmldbURI.create("/db/test/")));

        assertFalse(XmldbURI.create("/db/test_db").startsWith(XmldbURI.create("/db/test")));
    }
}
