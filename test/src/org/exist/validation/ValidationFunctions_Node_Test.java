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
 * $Id: ValidationFunctions_XSD_Test.java 5941 2007-05-29 20:27:59Z dizzzz $
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
public class ValidationFunctions_Node_Test {

        @Test
    public void noTest() {

    }

//    private final static Logger logger = LogManager.getLogger(ValidationFunctions_Node_Test.class);
//
//    private static String eXistHome = ConfigurationHelper.getExistHome().getAbsolutePath();
//
//    private static CollectionManagementService  cmservice = null;
//    private static UserManagementService  umservice = null;
//    private static XPathQueryService service;
//    private static Collection root = null;
//    private static Database database = null;
//
//
//
//
//    public static void initLog4J() {
//        Layout layout = new PatternLayout("%d [%t] %-5p (%F [%M]:%L) - %m %n");
//        Appender appender = new ConsoleAppender(layout);
//        BasicConfigurator.configure(appender);
//    }
//
//    @BeforeClass
//    public static void start() throws Exception {
//
//        // initialize driver
//        initLog4J();
//        logger.info("start");
//
//        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
//        database = (Database) cl.newInstance();
//        database.setProperty("create-database", "true");
//        DatabaseManager.registerDatabase(database);
//        root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "guest", "guest");
//        service = (XPathQueryService) root.getService("XQueryService", "1.0");
//
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
//    public void storedNode() {
//
//        logger.info("storedNode");
//
//        clearGrammarCache();
//
//        String query = null;
//        ResourceSet result = null;
//        String r = null;
//
//        try {
//            logger.info("Test1");
//            query = "let $doc := doc('/db/validation/addressbook_valid.xml') " +
//                    "let $result := validation:validate( $doc, " +
//                    " xs:anyURI('/db/validation/xsd/addressbook.xsd') ) " +
//                    "return $result";
//            result = service.query(query);
//            r = (String) result.getResource(0).getContent();
//            assertEquals("valid document as node", "true", r);
//
//            clearGrammarCache();
//
//        } catch (Exception e) {
//            logger.error(e);
//            e.printStackTrace();
//            fail(e.getMessage());
//        }
//
//        try {
//            logger.info("Test2");
//
//            query = "let $doc := doc('/db/validation/addressbook_invalid.xml') " +
//                    "let $result := validation:validate( $doc, " +
//                    " xs:anyURI('/db/validation/xsd/addressbook.xsd') ) " +
//                    "return $result";
//            result = service.query(query);
//            r = (String) result.getResource(0).getContent();
//            assertEquals("invalid document as node", "false", r);
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
//    public void constructedNode() {
//
//        logger.info("constructedNode");
//
//        clearGrammarCache();
//
//        String query = null;
//        ResourceSet result = null;
//        String r = null;
//
//        try {
//            logger.info("Test1");
//
//            query = "let $doc := " +
//                    "<addressBook xmlns=\"http://jmvanel.free.fr/xsd/addressBook\">" +
//                    "<owner> <cname>John Punin</cname> <email>puninj@cs.rpi.edu</email> </owner>" +
//                    "<person> <cname>Harrison Ford</cname> <email>hford@famous.org</email> </person>" +
//                    "<person> <cname>Julia Roberts</cname> <email>jr@pw.com</email> </person>" +
//                    "</addressBook> " +
//                    "let $result := validation:validate( $doc, " +
//                    " xs:anyURI('/db/validation/xsd/addressbook.xsd') ) " +
//                    "return $result";
//            result = service.query(query);
//            r = (String) result.getResource(0).getContent();
//            assertEquals("valid document as node", "true", r);
//
//            clearGrammarCache();
//
//        } catch (Exception e) {
//            logger.error(e);
//            e.printStackTrace();
//            fail(e.getMessage());
//        }
//
//        try {
//            logger.info("Test2");
//
//            query = "let $doc := " +
//                    "<addressBook xmlns=\"http://jmvanel.free.fr/xsd/addressBook\">" +
//                    "<owner1> <cname>John Punin</cname> <email>puninj@cs.rpi.edu</email> </owner1>" +
//                    "<person> <cname>Harrison Ford</cname> <email>hford@famous.org</email> </person>" +
//                    "<person> <cname>Julia Roberts</cname> <email>jr@pw.com</email> </person>" +
//                    "</addressBook> " +
//                    "let $result := validation:validate( $doc, " +
//                    " xs:anyURI('/db/validation/xsd/addressbook.xsd') ) " +
//                    "return $result";
//            result = service.query(query);
//            r = (String) result.getResource(0).getContent();
//            assertEquals("invalid document as node", "false", r);
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
//    @AfterClass
//    public static void shutdownDB() throws Exception {
//
//        logger.info("shutdownDB");
//
//
//        root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
//
//
//        DatabaseManager.deregisterDatabase(database);
//        DatabaseInstanceManager dim =
//                (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
//        dim.shutdownDB();
//
//    }
}
