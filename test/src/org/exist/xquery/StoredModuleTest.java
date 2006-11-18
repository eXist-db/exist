/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import org.exist.storage.DBBroker;
import org.exist.xmldb.EXistResource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XQueryService;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * @author wolf
 *
 */
public class StoredModuleTest extends TestCase {

    public static void main(String[] args) {
        TestRunner.run(StoredModuleTest.class);
    }
    
    private final static String URI = "xmldb:exist://" + DBBroker.ROOT_COLLECTION;
    private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
    
    private final static String MODULE =
        "module namespace itg-modules = \"http://localhost:80/itg/xquery\";\n" +
        "declare variable $itg-modules:colls as xs:string+ external;\n" +
        "declare variable $itg-modules:coll as xs:string external;\n" +
        "declare variable $itg-modules:ordinal as xs:integer external;\n" +
        "declare function itg-modules:check-coll() as xs:boolean {\n" +
        "   if (fn:empty($itg-modules:coll)) then fn:false()\n" +
        "   else fn:true()\n" +
        "};";

    private Collection c;
    
    public void testQuery() throws Exception {
        XQueryService service = (XQueryService) c.getService("XQueryService", "1.0");
        String query = "import module namespace itg-modules = \"http://localhost:80/itg/xquery\" at " +
            "\"xmldb:exist://" + DBBroker.ROOT_COLLECTION + "/test/test.xqm\"; itg-modules:check-coll()";
        
        String cols[] = { "one", "two", "three" };
        
        service.setNamespace("itg-modules", "http://localhost:80/itg/xquery");
        
        CompiledExpression compiledQuery = service.compile(query);
        for (int i = 0; i < cols.length; i++) {
            service.declareVariable("itg-modules:coll", cols[i]);
            ResourceSet result = service.execute(compiledQuery);
            System.out.println("Result: " + result.getResource(0).getContent());
        }
    }
    
    private Collection setupTestCollection() throws XMLDBException {
        Collection root = DatabaseManager.getCollection(URI, "admin", "");
        CollectionManagementService rootcms = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
        Collection c = root.getChildCollection("test");
        if(c != null) rootcms.removeCollection("test");
        rootcms.createCollection("test");
        c = DatabaseManager.getCollection(URI+"/test", "admin", "");
        assertNotNull(c);
        
        BinaryResource res = (BinaryResource) c.createResource("test.xqm", "BinaryResource");
        ((EXistResource)res).setMimeType("application/xquery");
        res.setContent(MODULE.getBytes());
        c.storeResource(res);

        return c;
    }

    protected void setUp() {
        try {
            // initialize driver
            Database database = (Database) Class.forName(DRIVER).newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            
            c = setupTestCollection();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("failed setup", e);
        }
    }
    
    protected void tearDown() {
        try {
            if (c != null) c.close();
        } catch (XMLDBException e) {
            throw new RuntimeException("failed teardown", e);
        }
    }
}
