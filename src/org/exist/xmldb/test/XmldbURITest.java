package org.exist.xmldb.test;

import java.net.URI;
import java.net.URISyntaxException;

import junit.framework.TestCase;

import org.exist.xmldb.XmldbURI;

public class XmldbURITest extends TestCase {
	
	public void testConstructors() {
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
	
	public void testConstructor1() {
		try{
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist:///db");
			assertEquals("exist", xmldbURI.getInstanceName());
			assertNull(xmldbURI.getHost());
			assertEquals(-1, xmldbURI.getPort());
			assertNull(xmldbURI.getContext());
			assertEquals("/db", xmldbURI.getCollectionName());
			assertEquals("direct access", xmldbURI.getApiName());
			xmldbURI = XmldbURI.create("xmldb:exist:///db");
			assertEquals("exist", xmldbURI.getInstanceName());
			assertNull(xmldbURI.getHost());
			assertEquals(-1, xmldbURI.getPort());
			assertNull(xmldbURI.getContext());
			assertEquals("/db", xmldbURI.getCollectionName());
			assertEquals("direct access", xmldbURI.getApiName());			
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testConstructor2() {
		try{
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8080/exist/xmlrpc");
			assertEquals("exist", xmldbURI.getInstanceName());
			assertEquals("localhost", xmldbURI.getHost());
			assertEquals(8080, xmldbURI.getPort());
			assertEquals("/exist/xmlrpc", xmldbURI.getContext());
			assertEquals("", xmldbURI.getCollectionName());
			assertEquals("xmlrpc", xmldbURI.getApiName());
			xmldbURI = XmldbURI.create("xmldb:exist://localhost:8080/exist/xmlrpc");
			assertEquals("exist", xmldbURI.getInstanceName());
			assertEquals("localhost", xmldbURI.getHost());
			assertEquals(8080, xmldbURI.getPort());
			assertEquals("/exist/xmlrpc", xmldbURI.getContext());
			assertEquals("", xmldbURI.getCollectionName());
			assertEquals("xmlrpc", xmldbURI.getApiName());			
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}	
	
	public void testConstructor3() {
		try{
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/xmlrpc");
			assertEquals("exist", xmldbURI.getInstanceName());
			assertEquals("localhost", xmldbURI.getHost());
			assertEquals(8088, xmldbURI.getPort());
			assertEquals("/xmlrpc", xmldbURI.getContext());
			assertEquals("", xmldbURI.getCollectionName());
			assertEquals("xmlrpc", xmldbURI.getApiName());
			xmldbURI = XmldbURI.create("xmldb:exist://localhost:8088/xmlrpc");
			assertEquals("exist", xmldbURI.getInstanceName());
			assertEquals("localhost", xmldbURI.getHost());
			assertEquals(8088, xmldbURI.getPort());
			assertEquals("/xmlrpc", xmldbURI.getContext());
			assertEquals("", xmldbURI.getCollectionName());
			assertEquals("xmlrpc", xmldbURI.getApiName());			
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testConstructor4() {
		try{
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/webdav");
			assertEquals("exist", xmldbURI.getInstanceName());
			assertEquals("localhost", xmldbURI.getHost());
			assertEquals(8088, xmldbURI.getPort());
			assertEquals("/webdav", xmldbURI.getContext());
			assertEquals("", xmldbURI.getCollectionName());
			assertEquals("webdav", xmldbURI.getApiName());
			xmldbURI = XmldbURI.create("xmldb:exist://localhost:8088/webdav");
			assertEquals("exist", xmldbURI.getInstanceName());
			assertEquals("localhost", xmldbURI.getHost());
			assertEquals(8088, xmldbURI.getPort());
			assertEquals("/webdav", xmldbURI.getContext());
			assertEquals("", xmldbURI.getCollectionName());
			assertEquals("webdav", xmldbURI.getApiName());			
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}	
	
	public void testConstructor5() {
		try{
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist:///db/");
			assertEquals("/db", xmldbURI.getCollectionName());
			//assertEquals("xmldb:exist:///db", xmldbURI.toString());
			xmldbURI = XmldbURI.create("xmldb:exist:///db/");	
			assertEquals("/db", xmldbURI.getCollectionName());
			//assertEquals("xmldb:exist:///db", xmldbURI.toString());			
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testConstructor6() {
		try{
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist2://localhost:8088/webdav/db");
			assertEquals("exist2", xmldbURI.getInstanceName());
			xmldbURI = XmldbURI.create("xmldb:exist2://localhost:8088/webdav/db");
			assertEquals("exist2", xmldbURI.getInstanceName());			
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}	
	
	public void testConstructor7() {
		try{
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/xmlrpc/db");
			assertEquals("exist", xmldbURI.getInstanceName());
			assertEquals("localhost", xmldbURI.getHost());
			assertEquals(8088, xmldbURI.getPort());
			assertEquals("/xmlrpc", xmldbURI.getContext());
			assertEquals("/db", xmldbURI.getCollectionName());
			assertEquals("xmlrpc", xmldbURI.getApiName());
			xmldbURI = XmldbURI.create("xmldb:exist://localhost:8088/xmlrpc/db");
			assertEquals("exist", xmldbURI.getInstanceName());
			assertEquals("localhost", xmldbURI.getHost());
			assertEquals(8088, xmldbURI.getPort());
			assertEquals("/xmlrpc", xmldbURI.getContext());
			assertEquals("/db", xmldbURI.getCollectionName());
			assertEquals("xmlrpc", xmldbURI.getApiName());			
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}	
	
	public void testConstructor8() {
		try{
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/webdav/db");
			assertEquals("exist", xmldbURI.getInstanceName());
			assertEquals("localhost", xmldbURI.getHost());
			assertEquals(8088, xmldbURI.getPort());
			assertEquals("/webdav", xmldbURI.getContext());
			assertEquals("/db", xmldbURI.getCollectionName());
			assertEquals("webdav", xmldbURI.getApiName());
			xmldbURI = XmldbURI.create("xmldb:exist://localhost:8088/webdav/db");
			assertEquals("exist", xmldbURI.getInstanceName());
			assertEquals("localhost", xmldbURI.getHost());
			assertEquals(8088, xmldbURI.getPort());
			assertEquals("/webdav", xmldbURI.getContext());
			assertEquals("/db", xmldbURI.getCollectionName());
			assertEquals("webdav", xmldbURI.getApiName());			
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}	
	
	public void testConstructor9() {
		try{
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
			assertEquals("exist", xmldbURI.getInstanceName());
			assertEquals("localhost", xmldbURI.getHost());
			assertEquals(8088, xmldbURI.getPort());
			assertEquals("/xmlrpc/webdav", xmldbURI.getContext());
			assertEquals("/db", xmldbURI.getCollectionName());
			assertEquals("webdav", xmldbURI.getApiName());
			xmldbURI = XmldbURI.create("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
			assertEquals("exist", xmldbURI.getInstanceName());
			assertEquals("localhost", xmldbURI.getHost());
			assertEquals(8088, xmldbURI.getPort());
			assertEquals("/xmlrpc/webdav", xmldbURI.getContext());
			assertEquals("/db", xmldbURI.getCollectionName());
			assertEquals("webdav", xmldbURI.getApiName());			
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testConstructor10() {
		try{
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/webdav/xmlrpc/db");
			assertEquals("exist", xmldbURI.getInstanceName());
			assertEquals("localhost", xmldbURI.getHost());
			assertEquals(8088, xmldbURI.getPort());
			assertEquals("/webdav/xmlrpc", xmldbURI.getContext());
			assertEquals("/db", xmldbURI.getCollectionName());
			assertEquals("xmlrpc", xmldbURI.getApiName());
			xmldbURI = XmldbURI.create("xmldb:exist://localhost:8088/webdav/xmlrpc/db");
			assertEquals("exist", xmldbURI.getInstanceName());
			assertEquals("localhost", xmldbURI.getHost());
			assertEquals(8088, xmldbURI.getPort());
			assertEquals("/webdav/xmlrpc", xmldbURI.getContext());
			assertEquals("/db", xmldbURI.getCollectionName());
			assertEquals("xmlrpc", xmldbURI.getApiName());			
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testConstructor11() {
		try{
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost/db");
			assertEquals("exist", xmldbURI.getInstanceName());
			assertEquals("localhost", xmldbURI.getHost());
			assertEquals(-1, xmldbURI.getPort());
			assertNull(xmldbURI.getContext());
			assertEquals("/db", xmldbURI.getCollectionName());
			assertEquals("rest-style", xmldbURI.getApiName());		
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testFaultyConstructor1() {
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
	
	public void testFaultyConstructor2() {
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
	
	public void testFaultyConstructor3() {
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
	
	public void testFaultyConstructor4() {
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
	
	public void testFaultyConstructor5() {
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
	
	public void testFaultyConstructor6() {
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
	
	public void testChangePart1() {
		try {
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
			xmldbURI.setInstanceName("exist2");
			assertEquals("xmldb:exist2://localhost:8088/xmlrpc/webdav/db", xmldbURI.toString());			
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testChangePart2() {
		try {
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
			xmldbURI.setHost("remotehost");
			assertEquals("xmldb:exist://remotehost:8088/xmlrpc/webdav/db", xmldbURI.toString());			
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testChangePart3() {
		try {
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
			xmldbURI.setPort(8080);
			assertEquals("xmldb:exist://localhost:8080/xmlrpc/webdav/db", xmldbURI.toString());			
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}	
	
	public void testChangePart4() {
		try {
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
			xmldbURI.setPort(-1);
			assertEquals("xmldb:exist://localhost/xmlrpc/webdav/db", xmldbURI.toString());			
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testChangePart5() {
		try {
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8088/xmlrpc/webdav/db");
			xmldbURI.setContext("/webdav");
			assertEquals("xmldb:exist://localhost:8088/webdav/db", xmldbURI.toString());			
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}	

	public void testCompareTo1() {
		try{
			XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist:///db");
			XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist:///db/collection1");				
			assertTrue(xmldbURI1.compareTo(xmldbURI2) < 0);	
			assertTrue(xmldbURI2.compareTo(xmldbURI1) > 0);	
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testCompareTo2() {
		try{
			XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist:///db/collection1");
			XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist:///db/collection1");			
			assertEquals(0, xmldbURI1.compareTo(xmldbURI2));		
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testCompareTo3() {
		try{
			XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist:///collection1");
			XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist:///collection2");			
			assertTrue(xmldbURI1.compareTo(xmldbURI2) < 0);	
			assertTrue(xmldbURI2.compareTo(xmldbURI1) > 0);			
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}	
	
	public void testCompareTo4() {
		try{
			XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist1:///db");
			XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist2:///db");				
			assertTrue(xmldbURI1.compareTo(xmldbURI2) < 0);	
			assertTrue(xmldbURI2.compareTo(xmldbURI1) > 0);	
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}	
	
	public void testEquals1() {
		try{
			XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist:///db");
			XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist:///db");				
			assertTrue(xmldbURI1.equals(xmldbURI2));
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testEquals2() {
		try{
			XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist://localhost:8080/db");
			XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist://localhost:8080/db");				
			assertTrue(xmldbURI1.equals(xmldbURI2));
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testEquals3() {
		try{
			XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist://localhost:8080/exist/xmlrpc");
			XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist://localhost:8080/exist/xmlrpc");				
			assertTrue(xmldbURI1.equals(xmldbURI2));
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}	
	
	public void testEquals4() {
		try{
			XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist://localhost:8080/exist/xmlrpc/db");
			XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist://localhost:8080/exist/xmlrpc/db");				
			assertTrue(xmldbURI1.equals(xmldbURI2));
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}		
	
	public void testEquals5() {
		try{
			XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist1://localhost:8088/db");
			XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist1://localhost:8088/db");				
			assertTrue(xmldbURI1.equals(xmldbURI2));
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testIsAbsolute1() {
		try{
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist:///db");						
			assertTrue(xmldbURI.isAbsolute());
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testIsAbsolute2() {
		try{
			XmldbURI xmldbURI = new XmldbURI(".");						
			assertFalse(xmldbURI.isAbsolute());
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}	
	
	public void testIsAbsolute3() {
		try{
			XmldbURI xmldbURI = new XmldbURI("..");						
			assertFalse(xmldbURI.isAbsolute());
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testNormalize1() {
		try{
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8080/exist/./xmlrpc/db");			
			assertEquals("xmldb:exist://localhost:8080/exist/xmlrpc/db", xmldbURI.normalize().toString());		
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testNormalize2() {
		try{
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist://localhost:8080/exist/../xmlrpc/db");			
			assertEquals("xmldb:exist://localhost:8080/xmlrpc/db", xmldbURI.normalize().toString());		
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testNormalize3() {
		try{
			XmldbURI xmldbURI = new XmldbURI("xmldb:exist:///db");			
			assertEquals("xmldb:exist:///db", xmldbURI.normalize().toString());		
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}	
	
	public void testRelativize1() {
		try{
			XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist://localhost:8080/exist/exist/xmlrpc/db");	
			XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist://localhost:8080/exist/xmlrpc/db");	
			assertEquals("/exist/xmlrpc", (xmldbURI1.relativize(xmldbURI2)).toString());		
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testRelativize2() {
		try{
			XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist://localhost:8080/exist/xmlrpc/db");	
			XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist://localhost:8080/exist/exist/xmlrpc/db");	
			assertEquals("/exist/exist/xmlrpc", (xmldbURI1.relativize(xmldbURI2)).toString());		
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testRelativize3() {
		boolean exceptionThrown = false;
		try{
			XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist:///db");	
			XmldbURI xmldbURI2 = new XmldbURI("xmldb:exist:///db");	
			assertEquals("/exist/exist/xmlrpc", (xmldbURI1.relativize(xmldbURI2)).toString());		
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		} catch (NullPointerException e) {
			exceptionThrown = true;
		}
		
	}	
	
	public void testResolve1() {
		try{
			XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist://localhost:8080/a/b/xmlrpc/db");	
			XmldbURI xmldbURI2 = new XmldbURI("..");
			XmldbURI xmldbURI3 = xmldbURI1.resolve(xmldbURI2);
			assertEquals("xmldb:exist://localhost:8080/a/db", (xmldbURI1.resolve(xmldbURI2)).toString());
			//Note the API change ;-)
			assertEquals("xmlrpc", xmldbURI1.getApiName());
			assertEquals("rest-style", xmldbURI3.getApiName());			
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}
	
	public void testResolve2() {
		try{
			XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist://localhost:8080/a/b/xmlrpc/db");	
			XmldbURI xmldbURI2 = new XmldbURI("../..");
			XmldbURI xmldbURI3 = xmldbURI1.resolve(xmldbURI2);
			assertEquals("xmldb:exist://localhost:8080/db", xmldbURI3.toString());	
			//Note the API change ;-)
			assertEquals("xmlrpc", xmldbURI1.getApiName());
			assertEquals("rest-style", xmldbURI3.getApiName());
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
	}	
	
	public void testResolve3() {
		boolean exceptionThrown = false;
		try{
			//Null context here ;-)
			XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist:///a/db");	
			XmldbURI xmldbURI2 = new XmldbURI("..");			
			xmldbURI1.resolve(xmldbURI2);	
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		} catch (NullPointerException e) {
			exceptionThrown = true;
		}		
		assertTrue(exceptionThrown);
	}
	
	public void testResolve4() {		
		try{
			//Null context here ;-)
			XmldbURI xmldbURI1 = new XmldbURI("xmldb:exist://localhost:8080/xmlrpc/db");	
			//Up and up...
			XmldbURI xmldbURI2 = new XmldbURI("../../..");			
			XmldbURI xmldbURI3 = xmldbURI1.resolve(xmldbURI2);	
			//Strange but it's like this
			assertEquals("xmldb:exist://localhost:8080/../../../db", xmldbURI3.toString());				
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}				
	}	
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(XmldbURITest.class);
	}	

}
