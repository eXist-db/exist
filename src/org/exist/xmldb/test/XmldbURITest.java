package org.exist.xmldb.test;

import java.net.URI;
import java.net.URISyntaxException;

import junit.framework.TestCase;

import org.exist.xmldb.XmldbURI;

public class XmldbURITest extends TestCase {
    
	private final static String DECODED_COLL = "test[98]";
	private final static String ENCODED_COLL = "test%5B98%5D";

	public void testXmldbURIConstructors() {
        try{
            XmldbURI xmldbURI;
            //TODO : add some other instances
            xmldbURI = new XmldbURI(".");
            xmldbURI = new XmldbURI("..");
            xmldbURI = new XmldbURI("/db");
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIConstructor1() {
        try{
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist:///db");
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
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8080/exist/xmlrpc");
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
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/xmlrpc");
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
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/webdav");
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
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist:///db/");
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
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist2://localhost:8088/webdav/db");
            assertEquals("exist2", xmldbURI.getInstanceName());
            xmldbURI = XmldbURI.create("xmldb:exist2://localhost:8088/webdav/db");
            assertEquals("exist2", xmldbURI.getInstanceName());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIConstructor7() {
        try{
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/xmlrpc/db");
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
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/webdav/db");
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
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
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
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/webdav/xmlrpc/db");
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
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost/db");
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
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist:///db/aa/bb/ccc");
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
    
    public void testXmldbURIFaultyConstructor1() {
        boolean exceptionThrown = false;
        try{
            XmldbURI xmldbURI = new XmldbURI("exist:///db");
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
            XmldbURI xmldbURI = new XmldbURI("xmldb:///db");
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
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://");
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
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://");
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
    
    public void testXmldbURIFaultyConstructor5() {
        boolean exceptionThrown = false;
        try{
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist:///db?param=value");
        } catch (URISyntaxException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        exceptionThrown = false;
        try{
            XmldbURI xmldbURI = XmldbURI.create("xmldb:exist:///db?param=value");
        } catch (Exception e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }
    
    public void testXmldbURIFaultyConstructor6() {
        boolean exceptionThrown = false;
        try{
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist:///db#123");
        } catch (URISyntaxException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        exceptionThrown = false;
        try{
            XmldbURI xmldbURI = XmldbURI.create("xmldb:exist:///db#123");
        } catch (Exception e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }
    
    public void testXmldbURIChangePart1() {
        try {
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
            xmldbURI.setInstanceName("exist2");
            assertEquals("xmldb:exist2://localhost:8088/xmlrpc/webdav/db", xmldbURI.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIChangePart2() {
        try {
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
            xmldbURI.setHost("remotehost");
            assertEquals("xmldb:exist://remotehost:8088/xmlrpc/webdav/db", xmldbURI.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIChangePart3() {
        try {
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
            xmldbURI.setPort(8080);
            assertEquals("xmldb:exist://localhost:8080/xmlrpc/webdav/db", xmldbURI.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIChangePart4() {
        try {
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
            xmldbURI.setPort(-1);
            assertEquals("xmldb:exist://localhost/xmlrpc/webdav/db", xmldbURI.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIChangePart5() {
        try {
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
            xmldbURI.setContext("/webdav");
            assertEquals("xmldb:exist://localhost:8088/webdav/db", xmldbURI.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURICompareTo1() {
        try{
            XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist:///db");
            XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist:///db/collection1");
            assertTrue(xmldbURI1.compareTo(xmldbURI2) < 0);
            assertTrue(xmldbURI2.compareTo(xmldbURI1) > 0);
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURICompareTo2() {
        try{
            XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist:///db/collection1");
            XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist:///db/collection1");
            assertEquals(0, xmldbURI1.compareTo(xmldbURI2));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURICompareTo3() {
        try{
            XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist:///collection1");
            XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist:///collection2");
            assertTrue(xmldbURI1.compareTo(xmldbURI2) < 0);
            assertTrue(xmldbURI2.compareTo(xmldbURI1) > 0);
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURICompareTo4() {
        try{
            XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist1:///db");
            XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist2:///db");
            assertTrue(xmldbURI1.compareTo(xmldbURI2) < 0);
            assertTrue(xmldbURI2.compareTo(xmldbURI1) > 0);
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIEquals1() {
        try{
            XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist:///db");
            XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist:///db");
            assertTrue(xmldbURI1.equals(xmldbURI2));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIEquals2() {
        try{
            XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist://localhost:8080/db");
            XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist://localhost:8080/db");
            assertTrue(xmldbURI1.equals(xmldbURI2));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIEquals3() {
        try{
            XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist://localhost:8080/exist/xmlrpc");
            XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist://localhost:8080/exist/xmlrpc");
            assertTrue(xmldbURI1.equals(xmldbURI2));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIEquals4() {
        try{
            XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist://localhost:8080/exist/xmlrpc/db");
            XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist://localhost:8080/exist/xmlrpc/db");
            assertTrue(xmldbURI1.equals(xmldbURI2));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIEquals5() {
        try{
            XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist1://localhost:8088/db");
            XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist1://localhost:8088/db");
            assertTrue(xmldbURI1.equals(xmldbURI2));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIIsAbsolute1() {
        try{
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist:///db");
            assertTrue(xmldbURI.isAbsolute());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIIsAbsolute2() {
        try{
            XmldbURI xmldbURI = new XmldbURI(".");
            assertFalse(xmldbURI.isAbsolute());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIIsAbsolute3() {
        try{
            XmldbURI xmldbURI = new XmldbURI("..");
            assertFalse(xmldbURI.isAbsolute());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIIsContextAbsolute1() {
        try{
            XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist://localhost:8080/exist/exist/xmlrpc/db");
            assertTrue(xmldbURI1.isContextAbsolute());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURINormalizeContext1() {
        try{
            XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist://localhost:8080/exist/./xmlrpc/db");
            XmldbURI xmldbURI2 = xmldbURI1.normalizeContext();
            assertEquals("xmldb:exist://localhost:8080/exist/xmlrpc/db", xmldbURI2.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURINormalizeContext2() {
        try{
            XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist://localhost:8080/exist/../xmlrpc/db");
            XmldbURI xmldbURI2 = xmldbURI1.normalizeContext();
            assertEquals("xmldb:exist://localhost:8080/xmlrpc/db", xmldbURI2.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURINormalizeContext3() {
        try{
            XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist:///db");
            XmldbURI xmldbURI2  = xmldbURI1.normalizeContext();
            assertEquals("xmldb:exist:///db", xmldbURI2.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIRelativizeContext1() {
        try{
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8080/exist/exist/xmlrpc/db");
            URI uri = new URI("/exist/xmlrpc");
            assertEquals("/exist/xmlrpc", xmldbURI.relativizeContext(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIRelativizeContext2() {
        try{
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8080/exist/xmlrpc/db");
            URI uri = new URI("/exist/exist/xmlrpc");
            assertEquals("/exist/exist/xmlrpc", xmldbURI.relativizeContext(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIRelativizeContext3() {
        boolean exceptionThrown = false;
        try{
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist:///db");
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
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8080/a/b/xmlrpc/db");
            URI uri = new URI("..");
            //Strange but it's like this
            assertEquals("/a/b/", xmldbURI.resolveContext(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIResolveContext2() {
        try{
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8080/a/b/xmlrpc/db");
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
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist:///a/db");
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
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8080/xmlrpc/db");
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
            XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist://localhost:8080/xmlrpc/db");
            assertTrue(xmldbURI1.isCollectionPathAbsolute());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURINormalizeCollectionName1() {
        try{
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8080/xmlrpc/db/./collection");
            assertEquals("xmldb:exist://localhost:8080/xmlrpc/db/collection", xmldbURI.normalizeCollectionPath().toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURINormalizeCollectionName2() {
        try{
            XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist://localhost:8080/xmlrpc/db/../collection");
            XmldbURI xmldbURI2 = xmldbURI1.normalizeCollectionPath();
            assertEquals("xmldb:exist://localhost:8080/xmlrpc/collection", xmldbURI2.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURINormalizeCollectionName3() {
        try{
            XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist:///");
            XmldbURI xmldbURI2  = xmldbURI1.normalizeCollectionPath();
            assertEquals("xmldb:exist:///", xmldbURI2.toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIRelativizeCollectionName1() {
        try{
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8080/xmlrpc/db/db/collection");
            URI uri = new URI("/db/collection");
            assertEquals("/db/collection", xmldbURI.relativizeCollectionPath(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIRelativizeCollectionName2() {
        try{
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8080/xmlrpc/db/collection");
            URI uri = new URI("/db/db/collection");
            assertEquals("/db/db/collection", xmldbURI.relativizeCollectionPath(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIRelativizeCollectionName3() {
        boolean exceptionThrown = false;
        try{
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist:///");
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
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8080/xmlrpc/db/a/b");
            URI uri = new URI("..");
            assertEquals("/db/a/", xmldbURI.resolveCollectionPath(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIResolveCollectionName2() {
        try{
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8080/xmlrpc/db/a/b");
            URI uri = new URI("../..");
            assertEquals("/db/", xmldbURI.resolveCollectionPath(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIResolveCollectionName3() {
        try{
            //Null context here ;-)
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist:///");
            URI uri = new URI("..");
            assertEquals("/..", xmldbURI.resolveCollectionPath(uri).toString());
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURIResolveCollectionName4() {
        try{
            //Null context here ;-)
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8080/xmlrpc/db");
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
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist:///xmlrpc/"+ENCODED_COLL);
            assertEquals(xmldbURI.getCollectionPath(),"/xmlrpc/"+DECODED_COLL);
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public void testXmldbURICollectionPathEncoding2() {
        try{
        	//Should return encoded path
            XmldbURI xmldbURI = new XmldbURI("xmldb:exist:///xmlrpc/"+ENCODED_COLL);
            assertEquals(xmldbURI.getRawCollectionPath(),"/xmlrpc/"+ENCODED_COLL);
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        junit.textui.TestRunner.run(XmldbURITest.class);
    }
    
}
