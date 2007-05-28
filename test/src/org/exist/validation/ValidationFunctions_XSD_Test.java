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

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.apache.log4j.Logger;

import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xquery.XPathException;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.XPathQueryService;

/**
 *  Set of Tests for validation:validate($a) and validation:validate($a, $b)
 * regaring validatin using XSD's.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class ValidationFunctions_XSD_Test extends TestCase {
    
    
    private final static Logger logger = Logger.getLogger(ValidationFunctions_XSD_Test.class);
    
    
    private static XPathQueryService service;
    private static Collection root = null;
    private static Database database = null;
    
    public static void main(String[] args) throws XPathException {
        TestRunner.run(ValidationFunctions_XSD_Test.class);
    }
    
    public ValidationFunctions_XSD_Test(String arg0) {
        super(arg0);
    }
    
    public void testsetUp() throws Exception {
        
        // initialize driver
        System.out.println(this.getName());
        logger.info(this.getName());
        
        Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
        service = (XPathQueryService) root.getService( "XQueryService", "1.0" );
    }

    // ===========================================================
    
    private void clearGrammarCache() {
        logger.info("Clearing grammar cache");
        ResourceSet result = null;
        try {
            result = service.query("validation:grammar-cache-clear()");
            
        } catch (Exception e) {
            logger.error(e);
            e.printStackTrace();
            fail(e.getMessage());
        }
        
    }
    
    public void testXSD_NotInSystemCatalog() {
        
        System.out.println(this.getName());
        logger.info(this.getName());
        
        clearGrammarCache();
        
        ResourceSet result = null;
        String r = null;
        try {
            // XSD for addressbook_valid.xml is *not* registered in system catalog.
            // result should be "document is invalid"
            result = service.query(
                "validation:validate('/db/validationtest/addressbook_valid.xml')");
            r = (String) result.getResource(0).getContent();
            assertEquals( "addressbook_valid.xml not in systemcatalog", "false", r );
            
            clearGrammarCache();
            
        } catch (Exception e) {
            logger.error(e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    public void testXSD_SpecifiedCatalog() {
        
        System.out.println(this.getName());
        logger.info(this.getName());
        
        clearGrammarCache();
        
        ResourceSet result = null;
        String r = null;
        try {
            logger.info("Test1");
            result = service.query(
                "validation:validate('/db/validationtest/addressbook_valid.xml',"
                +"'/db/validationtest/xsd/catalog.xml')");
            r = (String) result.getResource(0).getContent();
            assertEquals("valid document", "true", r );
            
            clearGrammarCache();
            
            logger.info("Test2");
            result = service.query(
                "validation:validate('/db/validationtest/addressbook_invalid.xml',"
                +"'/db/validationtest/xsd/catalog.xml')");
            r = (String) result.getResource(0).getContent();
            assertEquals( "invalid document", "false", r );
            
            clearGrammarCache();
            
            logger.info("Test3");
            result = service.query(
                "validation:validate('/db/validationtest/addressbook_valid.xml',"
                +"'/db/validationtest/dtd/catalog.xml')");
            r = (String) result.getResource(0).getContent();
            assertEquals("wrong catalog", "false", r );
            
            clearGrammarCache();
            
            logger.info("Test4");
            result = service.query(
                "validation:validate('/db/validationtest/addressbook_invalid.xml',"
                +"'/db/validationtest/dtd/catalog.xml')");
            r = (String) result.getResource(0).getContent();
            assertEquals("wrong catalog, invalid document", "false", r );
            
        } catch (Exception e) {
            logger.error(e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    public void testXSD_SpecifiedGrammar() {
        
        System.out.println(this.getName());
        logger.info(this.getName());
        
        clearGrammarCache();
        
        ResourceSet result = null;
        String r = null;
        try {
            logger.info("Test1");
            result = service.query(
                "validation:validate('/db/validationtest/addressbook_valid.xml',"
                +"'/db/validationtest/xsd/addressbook.xsd')");
            r = (String) result.getResource(0).getContent();
            assertEquals("valid document", "true", r );
            
            clearGrammarCache();
            
            logger.info("Test2");
            result = service.query(
                "validation:validate('/db/validationtest/addressbook_invalid.xml',"
                +"'/db/validationtest/xsd/addressbook.xsd')");
            r = (String) result.getResource(0).getContent();
            assertEquals( "invalid document", "false", r );
            
            clearGrammarCache();
            
            
        } catch (Exception e) {
            logger.error(e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    public void testXSD_SearchedGrammar() {
        
        System.out.println(this.getName());
        logger.info(this.getName());
        
        clearGrammarCache();
        
        ResourceSet result = null;
        String r = null;
        try {
            
            logger.info("Test1");
            result = service.query(
                "validation:validate('/db/validationtest/addressbook_valid.xml',"
                +"'/db/validationtest/xsd/')");
            r = (String) result.getResource(0).getContent();
            assertEquals("valid document", "true", r );
            
            clearGrammarCache();
            
            logger.info("Test2");
            result = service.query(
                "validation:validate('/db/validationtest/addressbook_valid.xml',"
                +"'/db/validationtest/dtd/')");
            r = (String) result.getResource(0).getContent();
            assertEquals( "valid document, not found", "false", r );
            
            clearGrammarCache();
            
            logger.info("Test3");
            result = service.query(
                "validation:validate('/db/validationtest/addressbook_valid.xml',"
                +"'/db/')");
            r = (String) result.getResource(0).getContent();
            assertEquals("valid document", "true", r );
            
            clearGrammarCache();
            
            logger.info("Test4");
            result = service.query(
                "validation:validate('/db/validationtest/addressbook_invalid.xml',"
                +"'/db/')");
            r = (String) result.getResource(0).getContent();
            assertEquals( "invalid document", "false", r );
            
            clearGrammarCache();
            
            
        } catch (Exception e) {
            logger.error(e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    // DTDs
    
    
    public void testtearDown() throws Exception {
        
        System.out.println(this.getName());
        logger.info(this.getName());
        
        DatabaseManager.deregisterDatabase(database);
        DatabaseInstanceManager dim =
            (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();
        
    }
    
}
