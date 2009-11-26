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
import static org.junit.Assert.*;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.XPathQueryService;

/**
 *  Set of Tests for validation:validate($a) and validation:validate($a, $b)
 * regaring validatin using DTD's.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class ValidationFunctions_DTD_Test {
    
    private final static Logger logger = Logger.getLogger(ValidationFunctions_DTD_Test.class);
    
    private static XPathQueryService service;
    private static Collection root = null;
    private static Database database = null;

    @BeforeClass
    public static void init(){
        BasicConfigurator.configure();
    }

    @Before
    public void setUp() throws Exception {
      
        logger.info("setUp");
        
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
        service = (XPathQueryService) root.getService( "XQueryService", "1.0" );
    }
    
    // ===========================================================
    
    private void clearGrammarCache() {
        logger.info("Clearing grammar cache");
        @SuppressWarnings("unused")
		ResourceSet result = null;
        try {
            result = service.query("validation:clear-grammar-cache()");
            
        } catch (Exception e) {
            logger.error(e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    // ===========================================================

    @Test
    public void validateUsingSystemCatalog() {
        
        logger.info("validateUsingSystemCatalog");
        
        ResourceSet result = null;
        String r = null;
        try {
            // DTD for hamlet_valid.xml is registered in system catalog.
            // result should be "document is valid"
            result = service.query(
                "validation:validate( xs:anyURI('"+TestTools.VALIDATION_TMP+"/hamlet_valid.xml') )");
            r = (String) result.getResource(0).getContent();
            assertEquals( "hamlet_valid.xml in systemcatalog", "true", r );
            
        } catch (Exception e) {
            logger.error(e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    @Test
    public void specifiedCatalog() {
        
        logger.info("specifiedCatalog");
        
        clearGrammarCache();
        
        ResourceSet result = null;
        String r = null;
        try {
            logger.info("Test1");
            result = service.query(
                "validation:validate( xs:anyURI('"+TestTools.VALIDATION_HOME+"/hamlet_valid.xml') ,"
                +" xs:anyURI('/db/validation/dtd/catalog.xml') )");
            r = (String) result.getResource(0).getContent();
            assertEquals("valid document", "true", r );
            
            clearGrammarCache();
            
            logger.info("Test2");
            result = service.query(
                "validation:validate( xs:anyURI('/db/validation/hamlet_invalid.xml') ,"
                +" xs:anyURI('/db/validation/dtd/catalog.xml') )");
            r = (String) result.getResource(0).getContent();
            assertEquals( "invalid document", "false", r );
            
            clearGrammarCache();
            
            logger.info("Test3");
            result = service.query(
                "validation:validate( xs:anyURI('/db/validation/hamlet_valid.xml'), "
                +" xs:anyURI('/db/validation/xsd/catalog.xml') )");
            r = (String) result.getResource(0).getContent();
            assertEquals("wrong catalog", "false", r );
            
            clearGrammarCache();
            
            logger.info("Test4");
            result = service.query(
                "validation:validate( xs:anyURI('/db/validation/hamlet_invalid.xml'), "
                +" xs:anyURI('/db/validation/xsd/catalog.xml') )");
            r = (String) result.getResource(0).getContent();
            assertEquals("wrong catalog, invalid document", "false", r );
            
        } catch (Exception e) {
            logger.error(e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void specifiedGrammar() {
        
        logger.info("specifiedGrammar");
        
        clearGrammarCache();
        
        ResourceSet result = null;
        String r = null;
        try {
            logger.info("Test1");
            result = service.query(
                "validation:validate( xs:anyURI('/db/validation/hamlet_valid.xml'), "
                +" xs:anyURI('/db/validation/dtd/hamlet.dtd') )");
            r = (String) result.getResource(0).getContent();
            assertEquals("valid document", "true", r );
            
            clearGrammarCache();
            
            logger.info("Test2");
            result = service.query(
                "validation:validate( xs:anyURI('/db/validation/hamlet_invalid.xml'), "
                +" xs:anyURI('/db/validation/dtd/hamlet.dtd') )");
            r = (String) result.getResource(0).getContent();
            assertEquals( "invalid document", "false", r );
            
            clearGrammarCache();
            
            
        } catch (Exception e) {
            logger.error(e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void searchedGrammar() {
        
        logger.info("searchedGrammar");
        
        clearGrammarCache();
        
        ResourceSet result = null;
        String r = null;
        try {
            
            logger.info("Test1");
            result = service.query(
                "validation:validate( xs:anyURI('/db/validation/hamlet_valid.xml'), "
                +" xs:anyURI('/db/validation/dtd/') )");
            r = (String) result.getResource(0).getContent();
            assertEquals("valid document", "true", r );
            
            clearGrammarCache();
            
            logger.info("Test2");
            result = service.query(
                "validation:validate( xs:anyURI('/db/validation/hamlet_valid.xml'), "
                +" xs:anyURI('/db/validation/xsd/') )");
            r = (String) result.getResource(0).getContent();
            assertEquals( "valid document, not found", "false", r );
            
            clearGrammarCache();
            
            logger.info("Test3");
            result = service.query(
                "validation:validate( xs:anyURI('/db/validation/hamlet_valid.xml'), "
                +" xs:anyURI('/db/') )");
            r = (String) result.getResource(0).getContent();
            assertEquals("valid document", "true", r );
            
            clearGrammarCache();
            
            logger.info("Test4");
            result = service.query(
                "validation:validate( xs:anyURI('/db/validation/hamlet_invalid.xml'), "
                +" xs:anyURI('/db/') )");
            r = (String) result.getResource(0).getContent();
            assertEquals( "invalid document", "false", r );
            
            clearGrammarCache();
            
            
        } catch (Exception e) {
            logger.error(e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    
    @AfterClass
    public static void shutdown() throws Exception {

        logger.info("shutdown");
        
        DatabaseManager.deregisterDatabase(database);
        DatabaseInstanceManager dim =
            (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();
        
    }
    
}
