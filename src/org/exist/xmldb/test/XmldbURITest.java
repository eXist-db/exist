package org.exist.xmldb.test;

import java.net.URI;
import java.net.URISyntaxException;

import junit.framework.TestCase;

import org.exist.test.TestConstants;
import org.exist.xmldb.XmldbURI;

public class XmldbURITest extends TestCase {
   
	public void testXmldbURIConstructors() {
        try{
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
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
	public void testFailingXmldbURIConstructors() {
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
    
    public void testXmldbURIConstructor1() {
        try{
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
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIConstructor2() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/xmlrpc");
            assertEquals("exist", xmldbURI.getInstanceName());
            assertEquals("localhost", xmldbURI.getHost());
            assertEquals(8080, xmldbURI.getPort());
            assertEquals("/exist/xmlrpc", xmldbURI.getContext());
            assertEquals("", xmldbURI.getCollectionPath());
            assertEquals(XmldbURI.API_XMLRPC, xmldbURI.getApiName());
            xmldbURI = XmldbURI.create("xmldb:exist://localhost:8080/exist/xmlrpc");
            assertEquals("exist", xmldbURI.getInstanceName());
            assertEquals("localhost", xmldbURI.getHost());
            assertEquals(8080, xmldbURI.getPort());
            assertEquals("/exist/xmlrpc", xmldbURI.getContext());
            assertEquals("", xmldbURI.getCollectionPath());
            assertEquals(XmldbURI.API_XMLRPC, xmldbURI.getApiName());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIConstructor3() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8088/xmlrpc");
            assertEquals("exist", xmldbURI.getInstanceName());
            assertEquals("localhost", xmldbURI.getHost());
            assertEquals(8088, xmldbURI.getPort());
            assertEquals("/xmlrpc", xmldbURI.getContext());
            assertEquals("", xmldbURI.getCollectionPath());
            assertEquals(XmldbURI.API_XMLRPC, xmldbURI.getApiName());
            xmldbURI = XmldbURI.create("xmldb:exist://localhost:8088/xmlrpc");
            assertEquals("exist", xmldbURI.getInstanceName());
            assertEquals("localhost", xmldbURI.getHost());
            assertEquals(8088, xmldbURI.getPort());
            assertEquals("/xmlrpc", xmldbURI.getContext());
            assertEquals("", xmldbURI.getCollectionPath());
            assertEquals("xmlrpc", xmldbURI.getApiName());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIConstructor4() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8088/webdav");
            assertEquals("exist", xmldbURI.getInstanceName());
            assertEquals("localhost", xmldbURI.getHost());
            assertEquals(8088, xmldbURI.getPort());
            assertEquals("/webdav", xmldbURI.getContext());
            assertEquals("", xmldbURI.getCollectionPath());
            assertEquals(XmldbURI.API_WEBDAV, xmldbURI.getApiName());
            xmldbURI = XmldbURI.create("xmldb:exist://localhost:8088/webdav");
            assertEquals("exist", xmldbURI.getInstanceName());
            assertEquals("localhost", xmldbURI.getHost());
            assertEquals(8088, xmldbURI.getPort());
            assertEquals("/webdav", xmldbURI.getContext());
            assertEquals("", xmldbURI.getCollectionPath());
            assertEquals(XmldbURI.API_WEBDAV, xmldbURI.getApiName());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIConstructor5() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///db/");
            assertEquals("/db", xmldbURI.getCollectionPath());
            //assertEquals("xmldb:exist:///db", xmldbURI.toString());
            xmldbURI = XmldbURI.create("xmldb:exist:///db/");
            assertEquals("/db", xmldbURI.getCollectionPath());
            //assertEquals("xmldb:exist:///db", xmldbURI.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIConstructor6() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist2://localhost:8088/webdav/db");
            assertEquals("exist2", xmldbURI.getInstanceName());
            xmldbURI = XmldbURI.create("xmldb:exist2://localhost:8088/webdav/db");
            assertEquals("exist2", xmldbURI.getInstanceName());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIConstructor7() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8088/xmlrpc/db");
            assertEquals("exist", xmldbURI.getInstanceName());
            assertEquals("localhost", xmldbURI.getHost());
            assertEquals(8088, xmldbURI.getPort());
            assertEquals("/xmlrpc", xmldbURI.getContext());
            assertEquals("/db", xmldbURI.getCollectionPath());
            assertEquals(XmldbURI.API_XMLRPC, xmldbURI.getApiName());
            xmldbURI = XmldbURI.create("xmldb:exist://localhost:8088/xmlrpc/db");
            assertEquals("exist", xmldbURI.getInstanceName());
            assertEquals("localhost", xmldbURI.getHost());
            assertEquals(8088, xmldbURI.getPort());
            assertEquals("/xmlrpc", xmldbURI.getContext());
            assertEquals("/db", xmldbURI.getCollectionPath());
            assertEquals(XmldbURI.API_XMLRPC, xmldbURI.getApiName());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIConstructor8() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8088/webdav/db");
            assertEquals("exist", xmldbURI.getInstanceName());
            assertEquals("localhost", xmldbURI.getHost());
            assertEquals(8088, xmldbURI.getPort());
            assertEquals("/webdav", xmldbURI.getContext());
            assertEquals("/db", xmldbURI.getCollectionPath());
            assertEquals(XmldbURI.API_WEBDAV, xmldbURI.getApiName());
            xmldbURI = XmldbURI.create("xmldb:exist://localhost:8088/webdav/db");
            assertEquals("exist", xmldbURI.getInstanceName());
            assertEquals("localhost", xmldbURI.getHost());
            assertEquals(8088, xmldbURI.getPort());
            assertEquals("/webdav", xmldbURI.getContext());
            assertEquals("/db", xmldbURI.getCollectionPath());
            assertEquals(XmldbURI.API_WEBDAV, xmldbURI.getApiName());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIConstructor9() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
            assertEquals("exist", xmldbURI.getInstanceName());
            assertEquals("localhost", xmldbURI.getHost());
            assertEquals(8088, xmldbURI.getPort());
            assertEquals("/xmlrpc/webdav", xmldbURI.getContext());
            assertEquals("/db", xmldbURI.getCollectionPath());
            assertEquals(XmldbURI.API_WEBDAV, xmldbURI.getApiName());
            xmldbURI = XmldbURI.create("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
            assertEquals("exist", xmldbURI.getInstanceName());
            assertEquals("localhost", xmldbURI.getHost());
            assertEquals(8088, xmldbURI.getPort());
            assertEquals("/xmlrpc/webdav", xmldbURI.getContext());
            assertEquals("/db", xmldbURI.getCollectionPath());
            assertEquals(XmldbURI.API_WEBDAV, xmldbURI.getApiName());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIConstructor10() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8088/webdav/xmlrpc/db");
            assertEquals("exist", xmldbURI.getInstanceName());
            assertEquals("localhost", xmldbURI.getHost());
            assertEquals(8088, xmldbURI.getPort());
            assertEquals("/webdav/xmlrpc", xmldbURI.getContext());
            assertEquals("/db", xmldbURI.getCollectionPath());
            assertEquals(XmldbURI.API_XMLRPC, xmldbURI.getApiName());
            xmldbURI = XmldbURI.create("xmldb:exist://localhost:8088/webdav/xmlrpc/db");
            assertEquals("exist", xmldbURI.getInstanceName());
            assertEquals("localhost", xmldbURI.getHost());
            assertEquals(8088, xmldbURI.getPort());
            assertEquals("/webdav/xmlrpc", xmldbURI.getContext());
            assertEquals("/db", xmldbURI.getCollectionPath());
            assertEquals(XmldbURI.API_XMLRPC, xmldbURI.getApiName());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIConstructor11() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost/db");
            assertEquals("exist", xmldbURI.getInstanceName());
            assertEquals("localhost", xmldbURI.getHost());
            assertEquals(-1, xmldbURI.getPort());
            assertNull(xmldbURI.getContext());
            assertEquals("/db", xmldbURI.getCollectionPath());
            assertEquals(XmldbURI.API_REST, xmldbURI.getApiName());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIConstructor12() {
        try{
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
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    /*
     * These are no longer faulty
     */
    public void testXmldbURIConstructor13() {
        try{
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
       } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIConstructor14() {
        try{
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
       } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIConstructor15() {
        try{
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
       } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIFaultyConstructor1() {
        boolean exceptionThrown = false;
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("exist:///db");
        } catch (URISyntaxException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        exceptionThrown = false;
        try{
            XmldbURI xmldbURI = XmldbURI.create("exist:///db");
        } catch (Exception e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        
    }
    
    public void testXmldbURIFaultyConstructor2() {
        boolean exceptionThrown = false;
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:///db");
        } catch (URISyntaxException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        exceptionThrown = false;
        try{
            XmldbURI xmldbURI = XmldbURI.create("xmldb:///db");
        } catch (Exception e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }
    
    public void testXmldbURIFaultyConstructor3() {
        boolean exceptionThrown = false;
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://");
        } catch (URISyntaxException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        exceptionThrown = false;
        try{
            XmldbURI xmldbURI = XmldbURI.create("xmldb:exist://");
        } catch (Exception e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }
    
    public void testXmldbURIFaultyConstructor4() {
        boolean exceptionThrown = false;
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://");
        } catch (URISyntaxException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        exceptionThrown = false;
        try{
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
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
            xmldbURI.setInstanceName("exist2");
            assertEquals("xmldb:exist2://localhost:8088/xmlrpc/webdav/db", xmldbURI.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIChangePart2() {
        try {
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
            xmldbURI.setHost("remotehost");
            assertEquals("xmldb:exist://remotehost:8088/xmlrpc/webdav/db", xmldbURI.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIChangePart3() {
        try {
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
            xmldbURI.setPort(8080);
            assertEquals("xmldb:exist://localhost:8080/xmlrpc/webdav/db", xmldbURI.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIChangePart4() {
        try {
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
            xmldbURI.setPort(-1);
            assertEquals("xmldb:exist://localhost/xmlrpc/webdav/db", xmldbURI.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIChangePart5() {
        try {
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
            xmldbURI.setContext("/webdav");
            assertEquals("xmldb:exist://localhost:8088/webdav/db", xmldbURI.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    */
    
    public void testXmldbURICompareTo1() {
        try{
            XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist:///db");
            XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist:///db/collection1");
            assertTrue(xmldbURI1.compareTo(xmldbURI2) < 0);
            assertTrue(xmldbURI2.compareTo(xmldbURI1) > 0);
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURICompareTo2() {
        try{
            XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist:///db/collection1");
            XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist:///db/collection1");
            assertEquals(0, xmldbURI1.compareTo(xmldbURI2));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURICompareTo3() {
        try{
            XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist:///collection1");
            XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist:///collection2");
            assertTrue(xmldbURI1.compareTo(xmldbURI2) < 0);
            assertTrue(xmldbURI2.compareTo(xmldbURI1) > 0);
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURICompareTo4() {
        try{
            XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist1:///db");
            XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist2:///db");
            assertTrue(xmldbURI1.compareTo(xmldbURI2) < 0);
            assertTrue(xmldbURI2.compareTo(xmldbURI1) > 0);
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIEquals1() {
        try{
            XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist:///db");
            XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist:///db");
            assertTrue(xmldbURI1.equals(xmldbURI2));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIEquals2() {
        try{
            XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/db");
            XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/db");
            assertTrue(xmldbURI1.equals(xmldbURI2));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIEquals3() {
        try{
            XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/xmlrpc");
            XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/xmlrpc");
            assertTrue(xmldbURI1.equals(xmldbURI2));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIEquals4() {
        try{
            XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/xmlrpc/db");
            XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/xmlrpc/db");
            assertTrue(xmldbURI1.equals(xmldbURI2));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIEquals5() {
        try{
            XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist1://localhost:8088/db");
            XmldbURI xmldbURI2 = XmldbURI.xmldbUriFor("xmldb:exist1://localhost:8088/db");
            assertTrue(xmldbURI1.equals(xmldbURI2));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIIsAbsolute1() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///db");
            assertTrue(xmldbURI.isAbsolute());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIIsAbsolute2() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor(".");
            assertFalse(xmldbURI.isAbsolute());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIIsAbsolute3() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("..");
            assertFalse(xmldbURI.isAbsolute());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIIsContextAbsolute1() {
        try{
            XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/exist/xmlrpc/db");
            assertTrue(xmldbURI1.isContextAbsolute());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURINormalizeContext1() {
        try{
            XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/./xmlrpc/db");
            XmldbURI xmldbURI2 = xmldbURI1.normalizeContext();
            assertEquals("xmldb:exist://localhost:8080/exist/xmlrpc/db", xmldbURI2.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURINormalizeContext2() {
        try{
            XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/../xmlrpc/db");
            XmldbURI xmldbURI2 = xmldbURI1.normalizeContext();
            assertEquals("xmldb:exist://localhost:8080/xmlrpc/db", xmldbURI2.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURINormalizeContext3() {
        try{
            XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist:///db");
            XmldbURI xmldbURI2  = xmldbURI1.normalizeContext();
            assertEquals("xmldb:exist:///db", xmldbURI2.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIRelativizeContext1() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/exist/xmlrpc/db");
            URI uri = new URI("/exist/xmlrpc");
            assertEquals("/exist/xmlrpc", xmldbURI.relativizeContext(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIRelativizeContext2() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/exist/xmlrpc/db");
            URI uri = new URI("/exist/exist/xmlrpc");
            assertEquals("/exist/exist/xmlrpc", xmldbURI.relativizeContext(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIRelativizeContext3() {
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
    
    public void testXmldbURIResolveContext1() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/a/b/xmlrpc/db");
            URI uri = new URI("..");
            //Strange but it's like this
            assertEquals("/a/b/", xmldbURI.resolveContext(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIResolveContext2() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/a/b/xmlrpc/db");
            URI uri = new URI("../..");
            //Strange but it's like this
            assertEquals("/a/", xmldbURI.resolveContext(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIResolveContext3() {
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
    
    public void testXmldbURIResolveContext4() {
        try{
            //Null context here ;-)
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db");
            //Up and up...
            URI uri = new URI("/../../..");
            //Strange but it's like this
            assertEquals("/../../..", xmldbURI.resolveContext(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIIsCollectionNameAbsolute1() {
        try{
            XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db");
            assertTrue(xmldbURI1.isCollectionPathAbsolute());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURINormalizeCollectionName1() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db/./collection");
            assertEquals("xmldb:exist://localhost:8080/xmlrpc/db/collection", xmldbURI.normalizeCollectionPath().toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURINormalizeCollectionName2() {
        try{
            XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db/../collection");
            XmldbURI xmldbURI2 = xmldbURI1.normalizeCollectionPath();
            assertEquals("xmldb:exist://localhost:8080/xmlrpc/collection", xmldbURI2.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURINormalizeCollectionName3() {
        try{
            XmldbURI xmldbURI1 = XmldbURI.xmldbUriFor("xmldb:exist:///");
            XmldbURI xmldbURI2  = xmldbURI1.normalizeCollectionPath();
            assertEquals("xmldb:exist:///", xmldbURI2.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIRelativizeCollectionName1() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db/db/collection");
            URI uri = new URI("/db/collection");
            assertEquals("/db/collection", xmldbURI.relativizeCollectionPath(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIRelativizeCollectionName2() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db/collection");
            URI uri = new URI("/db/db/collection");
            assertEquals("/db/db/collection", xmldbURI.relativizeCollectionPath(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIRelativizeCollectionName3() {
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
    
    public void testXmldbURIResolveCollectionName1() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db/a/b");
            URI uri = new URI("..");
            assertEquals("/db/a/", xmldbURI.resolveCollectionPath(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIResolveCollectionName2() {
        try{
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db/a/b");
            URI uri = new URI("../..");
            assertEquals("/db/", xmldbURI.resolveCollectionPath(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIResolveCollectionName3() {
        try{
            //Null context here ;-)
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///");
            URI uri = new URI("..");
            assertEquals("/..", xmldbURI.resolveCollectionPath(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIResolveCollectionName4() {
        try{
            //Null context here ;-)
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist://localhost:8080/xmlrpc/db");
            //Up and up...
            URI uri = new URI("/../../..");
            //Strange but it's like this
            assertEquals("/../../..", xmldbURI.resolveCollectionPath(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURICollectionPathEncoding1() {
        try{
        	//Should return decoded path
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///xmlrpc/"+TestConstants.SPECIAL_NAME);
            assertEquals(xmldbURI.getCollectionPath(),"/xmlrpc/"+TestConstants.DECODED_SPECIAL_NAME);
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURICollectionPathEncoding2() {
        try{
        	//Should return encoded path
            XmldbURI xmldbURI = XmldbURI.xmldbUriFor("xmldb:exist:///xmlrpc/"+TestConstants.SPECIAL_NAME);
            assertEquals(xmldbURI.getRawCollectionPath(),"/xmlrpc/"+TestConstants.SPECIAL_NAME);
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURILastSegment() {
        try{
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
            assertEquals(XmldbURI.create("").lastSegment(),XmldbURI.create(""));
            assertEquals(XmldbURI.create("/").lastSegment(),XmldbURI.create(""));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIRemoveLastSegment() {
        try{
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
            assertEquals(xmldbURI.removeLastSegment(),XmldbURI.create(""));
            
            assertEquals(TestConstants.SPECIAL_URI.removeLastSegment(),XmldbURI.xmldbUriFor(""));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        junit.textui.TestRunner.run(XmldbURITest.class);
    }
    
}
