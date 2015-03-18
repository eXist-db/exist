/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
package org.exist.validation;

import org.junit.*;
//import static org.junit.Assert.*;
//
//import org.apache.log4j.Appender;
//import org.apache.log4j.BasicConfigurator;
//import org.apache.log4j.ConsoleAppender;
//import org.apache.log4j.Layout;
//import org.apache.log4j.Logger;
//import org.apache.log4j.PatternLayout;
//import org.exist.security.Permission;
//import org.exist.security.UnixStylePermission;
//
//import org.exist.storage.DBBroker;
//import org.exist.util.ConfigurationHelper;
//import org.exist.xmldb.DatabaseInstanceManager;
//import org.exist.xmldb.UserManagementService;
//
//import org.xmldb.api.DatabaseManager;
//import org.xmldb.api.base.Collection;
//import org.xmldb.api.base.Database;
//import org.xmldb.api.base.ResourceSet;
//import org.xmldb.api.modules.CollectionManagementService;
//import org.xmldb.api.modules.XPathQueryService;

/**
 *  Set of Tests for validation:validate($a) and validation:validate($a, $b)
 * regaring validatin using XSD's.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class ValidationFunctions_XSD_Test {
    
    
//    private final static Logger logger = LogManager.getLogger(ValidationFunctions_XSD_Test.class);
//
//    private static String eXistHome = ConfigurationHelper.getExistHome().getAbsolutePath();
//
//    private static CollectionManagementService  cmservice = null;
//    private static UserManagementService  umservice = null;
//    private static XPathQueryService service;
//    private static Collection root = null;
//    private static Database database = null;
//
    @Test
    public void noTest() {
        
    }

//    public static void initLog4J(){
//        Layout layout = new PatternLayout("%d [%t] %-5p (%F [%M]:%L) - %m %n");
//        Appender appender=new ConsoleAppender(layout);
//        BasicConfigurator.configure(appender);
//    }
//
//    @BeforeClass
//    public static void setUp() throws Exception {
//
//        // initialize driver
//        initLog4J();
//
//        logger.info("setUp");
//
//        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
//        database = (Database) cl.newInstance();
//        database.setProperty("create-database", "true");
//        DatabaseManager.registerDatabase(database);
//        root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "guest", "guest");
//        service = (XPathQueryService) root.getService( "XQueryService", "1.0" );
//
//        cmservice = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
//        Collection col1 = cmservice.createCollection(TestTools.VALIDATION_HOME);
//        Collection col2 = cmservice.createCollection(TestTools.VALIDATION_XSD);
//
//        Permission permission = new UnixStylePermission("guest", "guest", 666);
//
//        umservice = (UserManagementService) root.getService("UserManagementService", "1.0");
//        umservice.setPermissions(col1, permission);
//        umservice.setPermissions(col2, permission);
//
//        String addressbook = eXistHome + "/samples/validation/addressbook";
//
//        TestTools.insertDocumentToURL(addressbook + "/addressbook.xsd",
//                "xmldb:exist://" + TestTools.VALIDATION_XSD + "/addressbook.xsd");
//        TestTools.insertDocumentToURL(addressbook + "/catalog.xml",
//                "xmldb:exist://" + TestTools.VALIDATION_XSD + "/catalog.xml");
//
//        TestTools.insertDocumentToURL(addressbook + "/addressbook_valid.xml",
//                "xmldb:exist://" + TestTools.VALIDATION_HOME + "/addressbook_valid.xml");
//        TestTools.insertDocumentToURL(addressbook + "/addressbook_invalid.xml",
//                "xmldb:exist://" + TestTools.VALIDATION_HOME + "/addressbook_invalid.xml");
//    }
//
//    // ===========================================================
//
//    private void clearGrammarCache() {
//        logger.info("Clearing grammar cache");
//        @SuppressWarnings("unused")
//		ResourceSet result = null;
//        try {
//            result = service.query("validation:clear-grammar-cache()");
//
//        } catch (Exception e) {
//            logger.error(e);
//            e.printStackTrace();
//            fail(e.getMessage());
//        }
//
//    }
//
//    @Test
//    public void testXSD_NotInSystemCatalog() {
//
//        logger.info("start");
//
//        clearGrammarCache();
//
//        ResourceSet result = null;
//        String r = null;
//        try {
//            // XSD for addressbook_valid.xml is *not* registered in system catalog.
//            // result should be "document is invalid"
//            result = service.query(
//                "validation:validate( xs:anyURI('/db/validation/addressbook_valid.xml') )");
//            r = (String) result.getResource(0).getContent();
//            assertEquals( "addressbook_valid.xml not in systemcatalog", "false", r );
//
//            clearGrammarCache();
//
//        } catch (Exception e) {
//            logger.error(e);
//            e.printStackTrace();
//            fail(e.getMessage());
//        }
//    }
//
//    @Test
//    public void testXSD_SpecifiedCatalog() {
//
//        logger.info("start");
//
//        clearGrammarCache();
//
//        ResourceSet result = null;
//        String r = null;
//        try {
//            logger.info("Test1");
//            result = service.query(
//                "validation:validate( xs:anyURI('/db/validation/addressbook_valid.xml'), "
//                +" xs:anyURI('/db/validation/xsd/catalog.xml') )");
//            r = (String) result.getResource(0).getContent();
//            assertEquals("valid document", "true", r );
//
//            clearGrammarCache();
//
//            logger.info("Test2");
//            result = service.query(
//                "validation:validate( xs:anyURI('/db/validation/addressbook_invalid.xml'), "
//                +" xs:anyURI('/db/validation/xsd/catalog.xml') )");
//            r = (String) result.getResource(0).getContent();
//            assertEquals( "invalid document", "false", r );
//
//            clearGrammarCache();
//
//            logger.info("Test3");
//            result = service.query(
//                "validation:validate( xs:anyURI('/db/validation/addressbook_valid.xml'), "
//                +" xs:anyURI('/db/validation/dtd/catalog.xml') )");
//            r = (String) result.getResource(0).getContent();
//            assertEquals("wrong catalog", "false", r );
//
//            clearGrammarCache();
//
//            logger.info("Test4");
//            result = service.query(
//                "validation:validate( xs:anyURI('/db/validation/addressbook_invalid.xml'),"
//                +" xs:anyURI('/db/validation/dtd/catalog.xml') )");
//            r = (String) result.getResource(0).getContent();
//            assertEquals("wrong catalog, invalid document", "false", r );
//
//        } catch (Exception e) {
//            logger.error(e);
//            e.printStackTrace();
//            fail(e.getMessage());
//        }
//    }
//
//    @Test
//    public void testXSD_SpecifiedGrammar() {
//
//        logger.info("start");
//
//        clearGrammarCache();
//
//        ResourceSet result = null;
//        String r = null;
//        try {
//            logger.info("Test1");
//            result = service.query(
//                "validation:validate( xs:anyURI('/db/validation/addressbook_valid.xml'), "
//                +" xs:anyURI('/db/validation/xsd/addressbook.xsd') )");
//            r = (String) result.getResource(0).getContent();
//            assertEquals("valid document", "true", r );
//
//            clearGrammarCache();
//
//            logger.info("Test2");
//            result = service.query(
//                "validation:validate( xs:anyURI('/db/validation/addressbook_invalid.xml'), "
//                +" xs:anyURI('/db/validation/xsd/addressbook.xsd') )");
//            r = (String) result.getResource(0).getContent();
//            assertEquals( "invalid document", "false", r );
//
//            clearGrammarCache();
//
//
//        } catch (Exception e) {
//            logger.error(e);
//            e.printStackTrace();
//            fail(e.getMessage());
//        }
//    }
//
//    @Test
//    public void testXSD_SearchedGrammar() {
//
//        logger.info("start");
//
//        clearGrammarCache();
//
//        ResourceSet result = null;
//        String r = null;
//        try {
//
//            logger.info("Test1");
//            result = service.query(
//                "validation:validate( xs:anyURI('/db/validation/addressbook_valid.xml'), "
//                +" xs:anyURI('/db/validation/xsd/') )");
//            r = (String) result.getResource(0).getContent();
//            assertEquals("valid document", "true", r );
//
//            clearGrammarCache();
//
//            logger.info("Test2");
//            result = service.query(
//                "validation:validate( xs:anyURI('/db/validation/addressbook_valid.xml'), "
//                +" xs:anyURI('/db/validation/dtd/') )");
//            r = (String) result.getResource(0).getContent();
//            assertEquals( "valid document, not found", "false", r );
//
//            clearGrammarCache();
//
//            logger.info("Test3");
//            result = service.query(
//                "validation:validate( xs:anyURI('/db/validation/addressbook_valid.xml'), "
//                +" xs:anyURI('/db/validation/') )");
//            r = (String) result.getResource(0).getContent();
//            assertEquals("valid document", "true", r );
//
//            clearGrammarCache();
//
//            logger.info("Test4");
//            result = service.query(
//                "validation:validate( xs:anyURI('/db/validation/addressbook_invalid.xml') ,"
//                +" xs:anyURI('/db/validation/') )");
//            r = (String) result.getResource(0).getContent();
//            assertEquals( "invalid document", "false", r );
//
//            clearGrammarCache();
//
//
//        } catch (Exception e) {
//            logger.error(e);
//            e.printStackTrace();
//            fail(e.getMessage());
//        }
//    }
//
//    // DTDs
//
//    @AfterClass
//    public static void stop() throws Exception {
//
//        logger.info("stop");
//
//        root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
//
//        DatabaseManager.deregisterDatabase(database);
//        DatabaseInstanceManager dim =
//            (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
//        dim.shutdownDB();
//
//    }
    
}
