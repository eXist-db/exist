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
package org.exist.xquery;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.LockException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import static org.junit.Assert.*;

import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XPathQueryService;

import java.io.IOException;

public class AnnotationsTest {

    @ClassRule
    public final static ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @BeforeClass
    public static void setUp() throws XMLDBException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        CollectionManagementService service = existEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        Collection testCollection = service.createCollection("test");
        assertNotNull(testCollection);
    }

    @AfterClass
    public static void tearDown() throws LockException, TriggerException, PermissionDeniedException, EXistException, IOException {
        // testCollection.removeResource( testCollection .getResource(file_name));
        TestUtils.cleanupDB();
    }

    private Collection getTestCollection() throws XMLDBException {
        return DatabaseManager.getCollection("xmldb:exist:///db/test", "admin", "");
    }

    
    @Test
    public void annotation() throws XMLDBException {
        final String query = """
                declare namespace hello = 'http://world.com';
                declare
                %hello:world
                function local:hello() {
                    'hello world'
                };
                local:hello()""";

        final XPathQueryService service = getQueryService();
        final ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        Resource res = result.getIterator().nextResource();
        assertEquals("hello world", res.getContent());
    }

    @Test
    public void annotationWithLiterals() throws XMLDBException {
        final String query = """
                declare namespace hello = 'http://world.com';
                declare
                %hello:world('a=b', 'b=c')
                function local:hello() {
                    'hello world'
                };
                local:hello()""";
            
        final XPathQueryService service = getQueryService();
        final ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        Resource res = result.getIterator().nextResource();
        assertEquals("hello world", res.getContent());
    }

    @Test(expected = XMLDBException.class)
    public void annotationInXMLNamespaceFails() throws XMLDBException {
        final String query = """
                declare namespace hello = 'http://www.w3.org/XML/1998/namespace';
                declare %hello:world function local:hello() { 'hello world' };
                local:hello()""";
        getQueryService().query(query);
    }

    @Test(expected = XMLDBException.class)
    public void annotationInXMLSchemaNamespaceFails() throws XMLDBException {
        final String query = """
                declare namespace hello = 'http://www.w3.org/2001/XMLSchema';
                declare %hello:world function local:hello() { 'hello world' };
                local:hello()""";
        getQueryService().query(query);
    }

    @Test(expected = XMLDBException.class)
    public void annotationInXMLSchemaInstanceNamespaceFails() throws XMLDBException {
        final String query = """
                declare namespace hello = 'http://www.w3.org/2001/XMLSchema-instance';
                declare %hello:world function local:hello() { 'hello world' };
                local:hello()""";
        getQueryService().query(query);
    }

    @Test(expected = XMLDBException.class)
    public void annotationInXPathFunctionsNamespaceFails() throws XMLDBException {
        final String query = """
                declare namespace hello = 'http://www.w3.org/2005/xpath-functions';
                declare %hello:world function local:hello() { 'hello world' };
                local:hello()""";
        getQueryService().query(query);
    }

    @Test(expected = XMLDBException.class)
    public void annotationInXPathFunctionsMathNamespaceFails() throws XMLDBException {
        final String query = """
                declare namespace hello = 'http://www.w3.org/2005/xpath-functions/math';
                declare %hello:world function local:hello() { 'hello world' };
                local:hello()""";
        getQueryService().query(query);
    }

    @Test(expected = XMLDBException.class)
    public void annotationInXQueryOptionsNamespaceFails() throws XMLDBException {
        final String query = """
                declare namespace hello = 'http://www.w3.org/2011/xquery-options';
                declare %hello:world function local:hello() { 'hello world' };
                local:hello()""";
        getQueryService().query(query);
    }

    @Test(expected = XMLDBException.class)
    public void annotationInXPathFunctionsMapNamespaceFails() throws XMLDBException {
        final String query = """
                declare namespace m = 'http://www.w3.org/2005/xpath-functions/map';
                declare %m:x function local:foo() { 'bar' };
                local:foo()""";
        getQueryService().query(query);
    }

    @Test(expected = XMLDBException.class)
    public void annotationInXPathFunctionsArrayNamespaceFails() throws XMLDBException {
        final String query = """
                declare namespace a = 'http://www.w3.org/2005/xpath-functions/array';
                declare %a:x function local:foo() { 'bar' };
                local:foo()""";
        getQueryService().query(query);
    }

    @Test(expected = XMLDBException.class)
    public void annotationInXQueryNamespaceFails() throws XMLDBException {
        final String query = """
                declare namespace xq = 'http://www.w3.org/2012/xquery';
                declare %xq:x function local:foo() { 'bar' };
                local:foo()""";

        getQueryService().query(query);
    }

    /** XQ3.1+ allows annotations on FunctionTest in sequence-type positions. */
    @Test
    public void annotationOnFunctionTestParses() throws XMLDBException {
        final String query = """
                declare namespace eg = 'http://example.com';
                () instance of %eg:x function(*)""";

        final ResourceSet result = getQueryService().query(query);
        assertEquals(1, result.getSize());
        assertEquals("false", result.getIterator().nextResource().getContent());
    }

    @Test
    public void multipleAnnotationsOnFunctionTestParse() throws XMLDBException {
        final String query = """
                declare namespace eg = 'http://example.com';
                () instance of %eg:x %eg:y(1) %eg:z('foo') function(*)""";

        final ResourceSet result = getQueryService().query(query);
        assertEquals(1, result.getSize());
        assertEquals("false", result.getIterator().nextResource().getContent());
    }

    @Test
    public void annotationOnTypedFunctionTestParses() throws XMLDBException {
        final String query = """
                declare namespace eg = 'http://example.com';
                () instance of %eg:x function(xs:integer) as xs:string""";

        final ResourceSet result = getQueryService().query(query);
        assertEquals(1, result.getSize());
        assertEquals("false", result.getIterator().nextResource().getContent());
    }

    @Test
    public void annotationOnFunctionTestWithBracedURI() throws XMLDBException {
        final String query =
                "() instance of %Q{http://example.com}x function(*)";

        final ResourceSet result = getQueryService().query(query);
        assertEquals(1, result.getSize());
        assertEquals("false", result.getIterator().nextResource().getContent());
    }

    /** Annotation on FunctionTest in a reserved namespace must raise XQST0045. */
    @Test(expected = XMLDBException.class)
    public void annotationOnFunctionTestInReservedNamespaceFails() throws XMLDBException {
        final String query =
                "() instance of %Q{http://www.w3.org/XML/1998/namespace}x function(*)";

        getQueryService().query(query);
    }

    private XPathQueryService getQueryService() throws XMLDBException {
        Collection testCollection = getTestCollection();       
        XPathQueryService service = testCollection.getService(XPathQueryService.class);
        return service;
    }
}