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

import org.exist.TestUtils;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import static org.junit.Assert.*;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XPathQueryService;

public class AnnotationsTest {

    private static Database database;

    public AnnotationsTest() {
    }

    @BeforeClass
    public static void setUp() throws XMLDBException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        // initialize driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        CollectionManagementService service = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
        Collection testCollection = service.createCollection("test");
        assertNotNull(testCollection);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        // testCollection.removeResource( testCollection .getResource(file_name));
        TestUtils.cleanupDB();
        DatabaseInstanceManager dim =
                (DatabaseInstanceManager) DatabaseManager.getCollection("xmldb:exist:///db", "admin", null).getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();
        DatabaseManager.deregisterDatabase(database);
        database = null;
    }

    private Collection getTestCollection() throws XMLDBException {
        return DatabaseManager.getCollection("xmldb:exist:///db/test", "admin", null);
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