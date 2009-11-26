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
package org.exist.xquery;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
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

import org.junit.*;
import static org.junit.Assert.*;

/**
 * @author wolf
 *
 */
public class StoredModuleTest {

    private final static Logger LOG = Logger.getLogger(StoredModuleTest.class);
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
    private static Collection rootCollection = null;
    private static CollectionManagementService cmService = null;
    private static XQueryService xqService = null;
    private static Database database = null;

    @BeforeClass
    public static void first() {
        LOG.info("Starting...");
        try {
            BasicConfigurator.configure();
            Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);

            rootCollection = DatabaseManager.getCollection(URI, "admin", "");
            xqService = (XQueryService) rootCollection.getService("XQueryService", "1.0");
            cmService = (CollectionManagementService) rootCollection.getService("CollectionManagementService", "1.0");

        } catch (Exception ex) {
            LOG.error(ex);
        }
    }

    @AfterClass
    public static void tearDown() throws XMLDBException {
        LOG.info("Shutting down");
        DatabaseManager.deregisterDatabase(database);
        DatabaseInstanceManager dim =
                (DatabaseInstanceManager) rootCollection.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();
    }

    private Collection createCollection(String collectionName) throws XMLDBException {
        LOG.info("Create collection " + collectionName);
        Collection collection = rootCollection.getChildCollection(collectionName);
        if (collection == null) {
            //cmService.removeCollection(collectionName);
            cmService.createCollection(collectionName);
        }

        collection = DatabaseManager.getCollection(URI + "/" + collectionName, "admin", "");
        assertNotNull(collection);
        return collection;
    }

    private void writeModule(Collection collection, String modulename, String module) throws XMLDBException {
        LOG.info("Create module " + modulename);
        BinaryResource res = (BinaryResource) collection.createResource(modulename, "BinaryResource");
        ((EXistResource) res).setMimeType("application/xquery");
        res.setContent(module.getBytes());
        collection.storeResource(res);
        collection.close();
    }

    private ResourceSet executeQuery(String query) throws XMLDBException {
        CompiledExpression compiledQuery = xqService.compile(query);
        ResourceSet result = xqService.execute(compiledQuery);
        return result;
    }

    @Test
    public void testDB() {
        assertNotNull(database);
        assertNotNull(rootCollection);
        assertNotNull(xqService);
        assertNotNull(cmService);
    }

    @Test
    public void testQuery() throws Exception {

        Collection c = createCollection("test");
        writeModule(c, "test.xqm", MODULE);

        String query = "import module namespace itg-modules = \"http://localhost:80/itg/xquery\" at " +
                "\"xmldb:exist://" + DBBroker.ROOT_COLLECTION + "/test/test.xqm\"; itg-modules:check-coll()";

        String cols[] = {"one", "two", "three"};

        xqService.setNamespace("itg-modules", "http://localhost:80/itg/xquery");

        CompiledExpression compiledQuery = xqService.compile(query);
        for (int i = 0; i < cols.length; i++) {
            xqService.declareVariable("itg-modules:coll", cols[i]);
            ResourceSet result = xqService.execute(compiledQuery);
            System.out.println("Result: " + result.getResource(0).getContent());
        }
    }

    @Test
    public void testModule1() throws Exception {
        String collectionName = "module1";
        String module = "module namespace mod1 = 'urn:module1';" +
                "declare function mod1:showMe() as xs:string {" +
                "'hi from module 1'" +
                "};";
        String query = "import module namespace mod1 = 'urn:module1' " +
                "at  'xmldb:exist:/" + collectionName + "/module1.xqm'; " +
                "mod1:showMe()";

        Collection c = createCollection(collectionName);
        writeModule(c, "module1.xqm", module);

        ResourceSet rs = executeQuery(query);
        String r = (String) rs.getResource(0).getContent();
        assertEquals("hi from module 1", r);

    }
    private static final String module2 = "module namespace mod2 = 'urn:module2'; " +
    "import module namespace mod3 = 'urn:module3' " +
    "at  'module3/module3.xqm'; " +
    "declare function mod2:showMe() as xs:string {" +
    " mod3:showMe() " +
    "};";

    private static final String module2b = "module namespace mod2 = 'urn:module2'; " +
    "import module namespace mod3 = 'urn:module3' " +
    "at  'module3.xqm'; " +
    "declare function mod2:showMe() as xs:string {" +
    " mod3:showMe() " +
    "};";

    private static final String module3a = "module namespace mod3 = 'urn:module3';" +
    "declare function mod3:showMe() as xs:string {" +
    "'hi from module 3a'" +
    "};";
    
    private static final String module3b = "module namespace mod3 = 'urn:module3';" +
    "import module namespace mod4 = 'urn:module4' " +
    "at  '../module2/module4.xqm'; " +
    "declare function mod3:showMe() as xs:string {" +
    "mod4:showMe()" +
    "};";

    private static final String module4 = "module namespace mod4 = 'urn:module4';" +
    "declare function mod4:showMe() as xs:string {" +
    "'hi from module 4'" +
    "};";

    @Test(expected=XMLDBException.class)
    public void testModule23_missingRelativeContext() throws XMLDBException {
        String collection2Name = "module2";
        String collection3Name = "module2/module3";

        String query = "import module namespace mod2 = 'urn:module2' " +
                "at  'module2/module2.xqm'; " +
                "mod2:showMe()";

        Collection c2 = createCollection(collection2Name);
        writeModule(c2, "module2.xqm", module2);

        Collection c3 = createCollection(collection3Name);
        writeModule(c3, "module3.xqm", module3a);

        ResourceSet rs = executeQuery(query);
        String r = (String) rs.getResource(0).getContent();
        assertEquals("hi from module 3a", r);
    }

    @Test 
    public void testRelativeImportDb() throws Exception {
        String collection2Name = "module2";
        String collection3Name = "module2/module3";

        String query = "import module namespace mod2 = 'urn:module2' " +
                "at  'xmldb:exist:/" + collection2Name + "/module2.xqm'; " +
                "mod2:showMe()";
        
        Collection c2 = createCollection(collection2Name);
        writeModule(c2, "module2.xqm", module2);

        writeModule(c2, "module3.xqm", module3b);
        
        writeModule(c2, "module4.xqm", module4);

        Collection c3 = createCollection(collection3Name);
        writeModule(c3, "module3.xqm", module3a);

        // test relative module import in subfolder
        try {
            ResourceSet rs = executeQuery(query);
            String r = (String) rs.getResource(0).getContent();
            assertEquals("hi from module 3a", r);
            
        } catch (XMLDBException ex) {
            fail(ex.getMessage());
            LOG.error(ex);
        }

        // test relative module import in same folder, and using ".."
        writeModule(c2, "module2.xqm", module2b);
        
        try {
            ResourceSet rs = executeQuery(query);
            String r = (String) rs.getResource(0).getContent();
            assertEquals("hi from module 4", r);
        } catch (XMLDBException ex) {
            fail(ex.getMessage());
            LOG.error(ex);
        }
    }
    
    @Test 
    public void testRelativeImportFile() throws Exception {
        String collection2Name = "module2";
        String collection3Name = "module2/module3";

        String query = "import module namespace mod2 = 'urn:module2' " +
                "at  '/test/temp/" + collection2Name + "/module2.xqm'; " +
                "mod2:showMe()";
        
        String c2 = "test/temp/" + collection2Name;
        writeFile(c2 + "/module2.xqm", module2);
        writeFile(c2 + "/module3.xqm", module3b);
        writeFile(c2 + "/module4.xqm", module4);

        String c3 = "test/temp/" + collection3Name;
        writeFile(c3 + "/module3.xqm", module3a);

        // test relative module import in subfolder
        try {
            ResourceSet rs = executeQuery(query);
            String r = (String) rs.getResource(0).getContent();
            assertEquals("hi from module 3a", r);
            
        } catch (XMLDBException ex) {
            fail(ex.getMessage());
            LOG.error(ex);
        }
        
        // test relative module import in same folder, and using ".."
        writeFile(c2 + "/module2.xqm", module2b);
        
        try {
            ResourceSet rs = executeQuery(query);
            String r = (String) rs.getResource(0).getContent();
            assertEquals("hi from module 4", r);
        } catch (XMLDBException ex) {
            fail(ex.getMessage());
            LOG.error(ex);
        }
    }

    private void writeFile(String path, String module) throws IOException {
        path = path.replace("/", File.separator);
        File f = new File (path);
        File dir = f.getParentFile();
        assertTrue (dir.exists() || dir.mkdirs());
        assertTrue (dir.canWrite());
        assertTrue (f.createNewFile() || f.canWrite());
        PrintWriter writer = new PrintWriter (new FileOutputStream(f));
        writer.print(module);
        writer.close();
    }
}
