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

import org.exist.storage.DBBroker;
import org.exist.storage.io.ExistIOException;
import org.exist.validation.service.ValidationService;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;


/**
 *  Tests for the Validation Service, e.g. used by InteractiveClient
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class ValidationServiceTest  {
    
    private final static String URI = "xmldb:exist://" + DBBroker.ROOT_COLLECTION;
    private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
    private static Collection rootCollection = null;
    private static ValidationService service = null;
    
//    public ValidationServiceTest(String testName) {
//        super(testName);
//    }
//    
//    public static Test suite() {
//        TestSuite suite = new TestSuite(ValidationServiceTest.class);
//        return suite;
//    }
    
    public static void initLog4J(){
        Layout layout = new PatternLayout("%d [%t] %-5p (%F [%M]:%L) - %m %n");
        Appender appender=new ConsoleAppender(layout);
        BasicConfigurator.configure(appender);       
    }
    
    @BeforeClass
	public static void init() {
        initLog4J();
        
    }
    
    @Before
    public void setUp() {
        try {
            System.out.println(">>> setUp");
            Class cl = Class.forName(DRIVER);
            Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            rootCollection = DatabaseManager.getCollection(URI, "admin", null);
            Assert.assertNotNull("Could not connect to database.");
            service = getValidationService();
            System.out.println("<<<\n");
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    private ValidationService getValidationService() {
        try {
            return (ValidationService) rootCollection.getService("ValidationService", "1.0");
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
        return null;
    }
    
    @Test
    public void testGetName() {
        System.out.println("testGetName");
        try {
            Assert.assertEquals("ValidationService check", service.getName(),  "ValidationService" );
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testGetVersion() {
        System.out.println("testGetVersion");
        try {
            Assert.assertEquals("ValidationService check", service.getVersion(),   "1.0" );
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testXsdValidDocument() {
        System.out.println("testXsdValidDocument");
        try {
            Assert.assertFalse( "system catalog", service.validateResource("/db/validation/addressbook_valid.xml") );
            Assert.assertTrue( "specified catalog", service.validateResource("/db/validation/addressbook_valid.xml",
                "/db/validation/xsd/catalog.xml") );
            Assert.assertTrue( "specified grammar", service.validateResource("/db/validation/addressbook_valid.xml",
                "/db/validation/xsd/addressbook.xsd") );
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testXsdInvalidDocument() {
        System.out.println("testXsdInvalidDocument");
        try {
            Assert.assertFalse( "system catalog", service.validateResource("/db/validation/addressbook_invalid.xml") );
            Assert.assertFalse( "specified catalog", service.validateResource("/db/validation/addressbook_invalid.xml",
                "/db/validation/xsd/catalog.xml") );
            Assert.assertFalse( "specified grammar", service.validateResource("/db/validation/addressbook_invalid.xml",
                "/db/validation/xsd/addressbook.xsd") );
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testNonexistingDocument() {
        System.out.println("testNonexistingDocument");
        try {
            Assert.assertFalse( "non existing document", service.validateResource(DBBroker.ROOT_COLLECTION + "/foobar.xml") );
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testDtdValidDocument() {
        System.out.println("testDtdValidDocument");
        try {
            Assert.assertFalse( "system catalog", service.validateResource("/db/validation/hamlet_valid.xml") );
            Assert.assertTrue( "specified catalog", service.validateResource("/db/validation/hamlet_valid.xml",
                "/db/validation/dtd/catalog.xml") );
//            Assert.assertTrue( "specified grammar", service.validateResource("/db/validation/hamlet_valid.xml",
//                "/db/validation/dtd/hamlet.dtd") );
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Ignore("cannot specify dtd as second parameter") @Test
    public void testDtdValidDocument2() {
        System.out.println("testDtdValidDocument");
        try {
            Assert.assertTrue( "specified grammar", service.validateResource("/db/validation/hamlet_valid.xml",
                "/db/validation/dtd/hamlet.dtd") );
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testDtdInvalidDocument() {
        System.out.println("testDtdInvalidDocument");
        try {
            Assert.assertFalse( "system catalog", service.validateResource("/db/grammar/hamlet_invalid.xml") );
            
            Assert.assertFalse( "specified catalog", service.validateResource("/db/validation/hamlet_invalid.xml",
                "/db/validation/dtd/catalog.xml") );
            
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
            Assert.assertFalse( "system catalog", service.validateResource("/db/validation/hamlet_nodoctype.xml") );
            
            Assert.assertFalse( "specified catalog", service.validateResource("/db/validation/hamlet_nodoctype.xml",
                "/db/validation/dtd/catalog.xml") );
            
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
            Assert.assertFalse( "system catalog", service.validateResource("/db/validation/hamlet_wrongdoctype.xml") );
            
            Assert.assertFalse( "specified catalog", service.validateResource("/db/validation/hamlet_wrongdoctype.xml",
                "/db/validation/dtd/catalog.xml") );
            
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
}
