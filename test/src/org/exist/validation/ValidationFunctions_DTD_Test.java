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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import org.junit.*;

import static org.junit.Assert.*;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import org.exist.external.org.apache.commons.io.output.ByteArrayOutputStream;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;

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
    
    private static String eXistHome = ConfigurationHelper.getExistHome().getAbsolutePath();
    private static BrokerPool pool = null;
    private static Configuration config = null;

    private static XPathQueryService service;
    private static Collection root = null;
    private static Database database = null;

    @BeforeClass
    public static void startup() {
        BasicConfigurator.configure();

        DBBroker broker = null;
        TransactionManager transact = null;
        Txn txn = null;
        try {
            config = new Configuration();
            config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "auto");
            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();

            broker = pool.get(pool.getSecurityManager().getGuestSubject());
            transact = pool.getTransactionManager();
            txn = transact.beginTransaction();

            /** create nessecary collections if they dont exist */

            org.exist.collections.Collection col = broker.getOrCreateCollection(txn, XmldbURI.create(TestTools.VALIDATION_DTD));
            broker.saveCollection(txn, col);

            col = broker.getOrCreateCollection(txn, XmldbURI.create(TestTools.VALIDATION_XSD));
            broker.saveCollection(txn, col);

            col = broker.getOrCreateCollection(txn, XmldbURI.create(TestTools.VALIDATION_TMP));
            broker.saveCollection(txn, col);

            transact.commit(txn);

        } catch(Exception e) {
            if(transact != null && txn != null)
                transact.abort(txn);

            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if(broker != null)
                pool.release(broker);
        }
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

        try {
            File file = new File(eXistHome, "samples/shakespeare/hamlet.xml");
            InputStream fis = new FileInputStream(file);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            TestTools.copyStream(fis, baos);
            fis.close();
            
            String sb = new String(baos.toByteArray());
            sb=sb.replaceAll("\\Q<!\\E.*DOCTYPE.*\\Q-->\\E",
                "<!DOCTYPE PLAY PUBLIC \"-//PLAY//EN\" \"play.dtd\">" );
            InputStream is = new ByteArrayInputStream(sb.getBytes());
            
            // -----
            
            URL url = new URL("xmldb:exist://" + TestTools.VALIDATION_TMP + "/hamlet_valid.xml");
            URLConnection connection = url.openConnection();
            OutputStream os = connection.getOutputStream();
            
            TestTools.copyStream(is, os);
            
            is.close();
            os.close();
            
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.error(ex);
            fail(ex.getMessage());
        }

        try{
            config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "no");

            String hamlet = eXistHome + "/samples/validation/dtd";

            TestTools.insertDocumentToURL(hamlet+"/hamlet.dtd",
                "xmldb:exist://"+TestTools.VALIDATION_DTD+"/hamlet.dtd");
            TestTools.insertDocumentToURL(hamlet+"/catalog.xml",
                "xmldb:exist://"+TestTools.VALIDATION_DTD+"/catalog.xml");

            TestTools.insertDocumentToURL(hamlet+"/hamlet_valid.xml",
                "xmldb:exist://"+TestTools.VALIDATION_HOME+"/hamlet_valid.xml");
            TestTools.insertDocumentToURL(hamlet+"/hamlet_invalid.xml",
                "xmldb:exist://"+TestTools.VALIDATION_HOME+"/hamlet_invalid.xml");

            config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "yes");

        } catch (Exception ex) {
            ex.printStackTrace();
            logger.error(ex);
            fail(ex.getMessage());
        }
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
