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

import org.apache.log4j.Appender;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.PatternLayout;
import org.exist.security.Permission;
import org.exist.security.UnixStylePermission;

import org.exist.storage.DBBroker;
import org.exist.storage.io.ExistIOException;
import org.exist.util.ConfigurationHelper;
import org.exist.validation.service.ValidationService;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.UserManagementService;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XPathQueryService;

/**
 *  Tests for the Validation Service, e.g. used by InteractiveClient
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class ValidationServiceTest {

    private final static String URI = "xmldb:exist://" + DBBroker.ROOT_COLLECTION;
    private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
    private static String eXistHome = ConfigurationHelper.getExistHome().getAbsolutePath();
    private static CollectionManagementService cmservice = null;
    private static UserManagementService umservice = null;
    private static Collection root = null;
    private static ValidationService validationService = null;
    private static XPathQueryService xqservice;
    private static Database database = null;

    public static void initLog4J() {
        Layout layout = new PatternLayout("%d [%t] %-5p (%F [%M]:%L) - %m %n");
        Appender appender = new ConsoleAppender(layout);
        BasicConfigurator.configure(appender);
    }

    @BeforeClass
    public static void init() {
        initLog4J();

        try {
            System.out.println(">>> setUp");
            Class<?> cl = Class.forName(DRIVER);
            database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "guest", "guest");
            Assert.assertNotNull("Could not connect to database.");
            validationService = getValidationService();

            xqservice = (XPathQueryService) root.getService("XQueryService", "1.0");

            cmservice = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
            Collection col1 = cmservice.createCollection("/db/validation");

            Permission permission = new UnixStylePermission("guest", "guest", 666);

            umservice = (UserManagementService) root.getService("UserManagementService", "1.0");
            umservice.setPermissions(col1, permission);

            String addressbook = eXistHome + "/samples/validation/addressbook";
            
            TestTools.insertDocumentToURL(addressbook + "/addressbook_valid.xml",
                "xmldb:exist:///db/validation/addressbook_valid.xsd");

            TestTools.insertDocumentToURL(addressbook + "/addressbook_invalid.xml",
                "xmldb:exist:///db/validation/addressbook_invalid.xsd");

            TestTools.insertDocumentToURL(addressbook + "/catalog.xml",
                "xmldb:exist:///db/validation/catalog_xsd.xml");

            TestTools.insertDocumentToURL(addressbook + "/addressbook.xsd",
                "xmldb:exist:///db/validation/addressbook.xsd");

            System.out.println("<<<\n");
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    private static ValidationService getValidationService() {
        try {
            return (ValidationService) root.getService("ValidationService", "1.0");
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
        return null;
    }

    // ===========================================================
    @Before
    public void clearGrammarCache() throws XMLDBException {
        System.out.println("Clearing grammar cache");
        @SuppressWarnings("unused")
        ResourceSet result = xqservice.query("validation:clear-grammar-cache()");
    }

    @Test
    public void testGetName() {
        System.out.println("testGetName");
        try {
            Assert.assertEquals("ValidationService check", validationService.getName(), "ValidationService");
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testGetVersion() {
        System.out.println("testGetVersion");
        try {
            Assert.assertEquals("ValidationService check", validationService.getVersion(), "1.0");
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testXsdValidDocument() {
        System.out.println("testXsdValidDocument");
        try {
            Assert.assertFalse("system catalog", validationService.validateResource("/db/validation/addressbook_valid.xml"));
//            Assert.assertTrue("specified catalog", validationService.validateResource("/db/validation/addressbook_valid.xml",
//                    "/db/validation/catalog_xsd.xml"));
//            Assert.assertTrue("specified grammar", validationService.validateResource("/db/validation/addressbook_valid.xml",
//                    "xmldb:///db/validation/addressbook.xsd"));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testXsdInvalidDocument() {
        System.out.println("testXsdInvalidDocument");
        try {
            Assert.assertFalse("system catalog", validationService.validateResource("/db/validation/addressbook_invalid.xml"));
            Assert.assertFalse("specified catalog", validationService.validateResource("/db/validation/addressbook_invalid.xml",
                    "/db/validation/xsd/catalog.xml"));
            Assert.assertFalse("specified grammar", validationService.validateResource("/db/validation/addressbook_invalid.xml",
                    "/db/validation/xsd/addressbook.xsd"));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testNonexistingDocument() {
        System.out.println("testNonexistingDocument");
        try {
            Assert.assertFalse("non existing document", validationService.validateResource(DBBroker.ROOT_COLLECTION + "/foobar.xml"));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Ignore
    @Test
    public void testDtdValidDocument() {
        System.out.println("testDtdValidDocument");
        try {
            Assert.assertFalse("system catalog", validationService.validateResource("/db/validation/hamlet_valid.xml"));
            Assert.assertTrue("specified catalog", validationService.validateResource("/db/validation/hamlet_valid.xml",
                    "/db/validation/dtd/catalog.xml"));
//            Assert.assertTrue( "specified grammar", service.validateResource("/db/validation/hamlet_valid.xml",
//                "/db/validation/dtd/hamlet.dtd") );
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Ignore("cannot specify dtd as second parameter")
    @Test
    public void testDtdValidDocument2() {
        System.out.println("testDtdValidDocument");
        try {
            Assert.assertTrue("specified grammar", validationService.validateResource("/db/validation/hamlet_valid.xml",
                    "/db/validation/dtd/hamlet.dtd"));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testDtdInvalidDocument() {
        System.out.println("testDtdInvalidDocument");
        try {
            Assert.assertFalse("system catalog", validationService.validateResource("/db/grammar/hamlet_invalid.xml"));

            Assert.assertFalse("specified catalog", validationService.validateResource("/db/validation/hamlet_invalid.xml",
                    "/db/validation/dtd/catalog.xml"));

//            Assert.assertFalse( "specified grammar", service.validateResource("/db/validation/hamlet_invalid.xml",
//                "/db/validation/dtd/hamlet.dtd") );
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testNoDoctype() {
        System.out.println("testNoDoctype");
        try {
            Assert.assertFalse("system catalog", validationService.validateResource("/db/validation/hamlet_nodoctype.xml"));

            Assert.assertFalse("specified catalog", validationService.validateResource("/db/validation/hamlet_nodoctype.xml",
                    "/db/validation/dtd/catalog.xml"));

//            Assert.assertFalse( "specified grammar", service.validateResource("/db/validation/hamlet_nodoctype.xml",
//                "/db/validation/dtd/hamlet.dtd") );

        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testWrongDoctype() {
        System.out.println("testWrongDoctype");
        try {
            Assert.assertFalse("system catalog", validationService.validateResource("/db/validation/hamlet_wrongdoctype.xml"));

            Assert.assertFalse("specified catalog", validationService.validateResource("/db/validation/hamlet_wrongdoctype.xml",
                    "/db/validation/dtd/catalog.xml"));

//            Assert.assertFalse( "specified grammar", service.validateResource("/db/validation/hamlet_wrongdoctype.xml",
//                "/db/validation/dtd/hamlet.dtd") );

        } catch (Exception e) {

            if (e instanceof ExistIOException) {
                e.getCause().printStackTrace();
                Assert.fail(e.getCause().getMessage());
            } else {
                e.printStackTrace();
                Assert.fail(e.getMessage());
            }
        }
    }

    @AfterClass
    public static void shutdown() throws Exception {

        System.out.println("shutdown");

         root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);

        DatabaseManager.deregisterDatabase(database);
        DatabaseInstanceManager dim =
                (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();

    }
}
