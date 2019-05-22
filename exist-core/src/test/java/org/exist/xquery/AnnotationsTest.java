/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2004-2012 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
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
        CollectionManagementService service = (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
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
        
        final String TEST_VALUE_CONSTANT = "hello world";
        
        final String query = 
                "declare namespace hello = 'http://world.com';\n"
                + "declare\n"
                + "%hello:world\n"
                + "function local:hello() {\n"
                +   "'" + TEST_VALUE_CONSTANT + "'\n"
                + "};\n"
                + "local:hello()";
            
        final XPathQueryService service = getQueryService();
        final ResourceSet result = service.query(query);
        
        assertEquals(1, result.getSize());
        Resource res = result.getIterator().nextResource();
        assertEquals(TEST_VALUE_CONSTANT, res.getContent());
    }
    
    @Test
    public void annotationWithLiterals() throws XMLDBException {
        
        final String TEST_VALUE_CONSTANT = "hello world";
        
        final String query = 
                "declare namespace hello = 'http://world.com';\n"
                + "declare\n"
                + "%hello:world('a=b', 'b=c')\n"
                + "function local:hello() {\n"
                +   "'" + TEST_VALUE_CONSTANT + "'\n"
                + "};\n"
                + "local:hello()";
            
        final XPathQueryService service = getQueryService();
        final ResourceSet result = service.query(query);
        
        assertEquals(1, result.getSize());
        Resource res = result.getIterator().nextResource();
        assertEquals(TEST_VALUE_CONSTANT, res.getContent());
    }
    
    @Test(expected = XMLDBException.class)
    public void annotationInXMLNamespaceFails() throws XMLDBException {
        
        final String TEST_VALUE_CONSTANT = "hello world";
        
        final String query = 
                "declare namespace hello = 'http://www.w3.org/XML/1998/namespace';\n"
                + "declare\n"
                + "%hello:world\n"
                + "function local:hello() {\n"
                +   "'" + TEST_VALUE_CONSTANT + "'\n"
                + "};\n"
                + "local:hello()";
            
        final XPathQueryService service = getQueryService();
        service.query(query);
    }
    
    @Test(expected = XMLDBException.class)
    public void annotationInXMLSchemaNamespaceFails() throws XMLDBException {
        
        final String TEST_VALUE_CONSTANT = "hello world";
        
        final String query = 
                "declare namespace hello = 'http://www.w3.org/2001/XMLSchema';\n"
                + "declare\n"
                + "%hello:world\n"
                + "function local:hello() {\n"
                +   "'" + TEST_VALUE_CONSTANT + "'\n"
                + "};\n"
                + "local:hello()";
            
        final XPathQueryService service = getQueryService();
        service.query(query);
    }
    
    @Test(expected = XMLDBException.class)
    public void annotationInXMLSchemaInstanceNamespaceFails() throws XMLDBException {
        
        final String TEST_VALUE_CONSTANT = "hello world";
        
        final String query = 
                "declare namespace hello = 'http://www.w3.org/2001/XMLSchema-instance';\n"
                + "declare\n"
                + "%hello:world\n"
                + "function local:hello() {\n"
                +   "'" + TEST_VALUE_CONSTANT + "'\n"
                + "};\n"
                + "local:hello()";
            
        final XPathQueryService service = getQueryService();
        service.query(query);
    }
    
    @Test(expected = XMLDBException.class)
    public void annotationInXPathFunctionsNamespaceFails() throws XMLDBException {
        
        final String TEST_VALUE_CONSTANT = "hello world";
        
        final String query = 
                "declare namespace hello = 'http://www.w3.org/2005/xpath-functions';\n"
                + "declare\n"
                + "%hello:world\n"
                + "function local:hello() {\n"
                +   "'" + TEST_VALUE_CONSTANT + "'\n"
                + "};\n"
                + "local:hello()";
            
        final XPathQueryService service = getQueryService();
        service.query(query);
    }
    
    @Test(expected = XMLDBException.class)
    public void annotationInXPathFunctionsMathNamespaceFails() throws XMLDBException {
        
        final String TEST_VALUE_CONSTANT = "hello world";
        
        final String query = 
                "declare namespace hello = 'http://www.w3.org/2005/xpath-functions/math';\n"
                + "declare\n"
                + "%hello:world\n"
                + "function local:hello() {\n"
                +   "'" + TEST_VALUE_CONSTANT + "'\n"
                + "};\n"
                + "local:hello()";
            
        final XPathQueryService service = getQueryService();
        service.query(query);
    }
    
    @Test(expected = XMLDBException.class)
    public void annotationInXQueryOptionsNamespaceFails() throws XMLDBException {
        
        final String TEST_VALUE_CONSTANT = "hello world";
        
        final String query = 
                "declare namespace hello = 'http://www.w3.org/2011/xquery-options';\n"
                + "declare\n"
                + "%hello:world\n"
                + "function local:hello() {\n"
                +   "'" + TEST_VALUE_CONSTANT + "'\n"
                + "};\n"
                + "local:hello()";
            
        final XPathQueryService service = getQueryService();
        service.query(query);
    }
   
    private XPathQueryService getQueryService() throws XMLDBException {
        Collection testCollection = getTestCollection();       
        XPathQueryService service = (XPathQueryService) testCollection.getService("XPathQueryService", "1.0");
        return service;
    }
}