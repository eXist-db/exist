/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist-db Project
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

import java.io.IOException;
import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.util.LockException;

import org.junit.*;

import static org.junit.Assert.*;

import org.apache.log4j.BasicConfigurator;
import org.exist.collections.IndexInfo;

import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.SAXException;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

/**
 *  Set of Tests for validation:validate($a) and validation:validate($a, $b)
 * regaring validatin using DTD's.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class ValidationFunctions_DTD_Test {
    
    private static Configuration config = null;
    
    private final static String TEST_COLLECTION = "testValidationFunctionsDTD";

    private final static String ADMIN_UID = "admin";
    private final static String ADMIN_PWD = "";

    private final static String GUEST_UID = "guest";

    private final static String VALIDATION_HOME_COLLECTION_URI = "/db/" + TEST_COLLECTION + "/" + TestTools.VALIDATION_HOME_COLLECTION;

    private static XPathQueryService service = null;

    private final static String VALIDATION_DTD_COLLECTION_URI = VALIDATION_HOME_COLLECTION_URI + "/" + TestTools.VALIDATION_DTD_COLLECTION;
    private final static String VALIDATION_XSD_COLLECTION_URI = VALIDATION_HOME_COLLECTION_URI + "/" + TestTools.VALIDATION_XSD_COLLECTION;
    private final static String VALIDATION_TMP_COLLECTION_URI = VALIDATION_HOME_COLLECTION_URI + "/" + TestTools.VALIDATION_TMP_COLLECTION;

    @Test
    public void validateUsingSystemCatalog() throws XMLDBException {
        // DTD for hamlet_valid.xml is registered in system catalog.
        // result should be "document is valid"
        ResourceSet result = service.query("validation:validate( xs:anyURI('" + VALIDATION_TMP_COLLECTION_URI + "/hamlet_valid.xml'))");
        String r = (String) result.getResource(0).getContent();
        assertEquals( "hamlet_valid.xml in systemcatalog", "true", r );
    }
    
    @Test
    public void specifiedCatalog_test1() throws XMLDBException {
        ResourceSet result = service.query("validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_valid.xml') ," +" xs:anyURI('" + VALIDATION_DTD_COLLECTION_URI + "/catalog.xml'))");
        String r = (String) result.getResource(0).getContent();
        assertEquals("valid document", "true", r );
    }

    @Test
    public void specifiedCatalog_test2() throws XMLDBException {
        ResourceSet result = service.query("validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_invalid.xml'), xs:anyURI('" + VALIDATION_DTD_COLLECTION_URI + "/catalog.xml'))");
        String r = (String) result.getResource(0).getContent();
        assertEquals( "invalid document", "false", r );
    }
            
    @Test
    public void specifiedCatalog_test3() throws XMLDBException {
        ResourceSet result = service.query("validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_valid.xml'), xs:anyURI('" + VALIDATION_XSD_COLLECTION_URI + "/catalog.xml'))");
        String r = (String) result.getResource(0).getContent();
        assertEquals("wrong catalog", "false", r);
    }

    @Test
    public void specifiedCatalog_test4() throws XMLDBException {
        ResourceSet result = service.query("validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_invalid.xml'), xs:anyURI('" + VALIDATION_XSD_COLLECTION_URI + "/catalog.xml'))");
        String r = (String) result.getResource(0).getContent();
        assertEquals("wrong catalog, invalid document", "false", r );
    }

    @Test
    public void specifiedGrammar_dtd_forValidDoc() throws XMLDBException {
        ResourceSet result = service.query("validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_valid.xml'), xs:anyURI('" + VALIDATION_DTD_COLLECTION_URI + "/hamlet.dtd'))");
        String r = (String) result.getResource(0).getContent();
        assertEquals("valid document", "true", r );
    }

    @Test
    public void specifiedGrammar_dtd_forInvalidDoc() throws XMLDBException {
        ResourceSet result = service.query("validation:validate( xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_invalid.xml'), xs:anyURI('" + VALIDATION_DTD_COLLECTION_URI + "/hamlet.dtd') )");
        String r = (String) result.getResource(0).getContent();
        assertEquals( "invalid document", "false", r );
    }

    @Test
    public void searchedGrammar_valid_dtd() throws XMLDBException {
        ResourceSet result = service.query("validation:validate( xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_valid.xml'), xs:anyURI('" + VALIDATION_DTD_COLLECTION_URI + "/'))");
        String r = (String) result.getResource(0).getContent();
        assertEquals("valid document", "true", r );
    }

    @Test
    public void searchedGrammar_valid_xsd() throws XMLDBException {
        ResourceSet result = service.query("validation:validate( xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_valid.xml'), xs:anyURI('" + VALIDATION_XSD_COLLECTION_URI + "/') )");
        String r = (String) result.getResource(0).getContent();
        assertEquals( "valid document, not found", "false", r );
    }
            
    @Test
    public void searchedGrammar_valid() throws XMLDBException {
            ResourceSet result = service.query("validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_valid.xml'), xs:anyURI('/db/'))");
            String r = (String) result.getResource(0).getContent();
            assertEquals("valid document", "true", r );
    }
            
    @Test
    public void searchedGrammar_invalid() throws XMLDBException {
        ResourceSet result = service.query("validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_invalid.xml'), xs:anyURI('/db/'))");
        String r = (String) result.getResource(0).getContent();
        assertEquals( "invalid document", "false", r );
    }

    @Before
    public void clearGrammarCache() throws XMLDBException {
        service.query("validation:clear-grammar-cache()");
    }
    

    @BeforeClass
    public static void startup() throws Exception {
        BasicConfigurator.configure();
        config = new Configuration();
        config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "auto");

        BrokerPool.configure(1, 5, config);

        //create the collections we need for these tests
        createTestCollections();

        //create the documents we need for the tests
        createTestDocuments();

        //get xmldb xpath query service
        service = getXPathService();
    }


    @AfterClass
    public static void shutdown() throws Exception {
        removeTestCollections();
        BrokerPool.stopAll(true);
    }

    private static XPathQueryService getXPathService() throws Exception {
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        Database database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        return (XPathQueryService) root.getService( "XQueryService", "1.0" );
    }

    private static void createTestCollections() throws Exception {

        BrokerPool pool = BrokerPool.getInstance();
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn txn = null;
        try {
            Subject admin = pool.getSecurityManager().authenticate(ADMIN_UID, ADMIN_PWD);

            broker = pool.get(admin);

            transact = pool.getTransactionManager();
            txn = transact.beginTransaction();

            /** create nessecary collections if they dont exist */
            org.exist.collections.Collection testCollection = broker.getOrCreateCollection(txn, XmldbURI.create(VALIDATION_HOME_COLLECTION_URI));
            testCollection.getPermissions().setOwner(GUEST_UID);
            broker.saveCollection(txn, testCollection);

            org.exist.collections.Collection col = broker.getOrCreateCollection(txn, XmldbURI.create(VALIDATION_HOME_COLLECTION_URI + "/" + TestTools.VALIDATION_DTD_COLLECTION));
            col.getPermissions().setOwner(GUEST_UID);
            broker.saveCollection(txn, col);

            col = broker.getOrCreateCollection(txn, XmldbURI.create(VALIDATION_HOME_COLLECTION_URI + "/" + TestTools.VALIDATION_XSD_COLLECTION));
            col.getPermissions().setOwner(GUEST_UID);
            broker.saveCollection(txn, col);

            col = broker.getOrCreateCollection(txn, XmldbURI.create(VALIDATION_HOME_COLLECTION_URI + "/" + TestTools.VALIDATION_TMP_COLLECTION));
            col.getPermissions().setOwner(GUEST_UID);
            broker.saveCollection(txn, col);

            transact.commit(txn);

        } catch (Exception e) {
            if(transact != null && txn != null) {
                transact.abort(txn);
            }
            throw e;
        } finally {
            if(broker != null) {
                pool.release(broker);
            }
        }
    }

    private static void createTestDocuments() throws Exception {

        BrokerPool pool = BrokerPool.getInstance();
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn txn = null;
        try {

            broker = pool.get(pool.getSecurityManager().getGuestSubject());

            transact = pool.getTransactionManager();
            txn = transact.beginTransaction();

            /** create necessary documents  */

            //hamlet
            String sb = new String(TestTools.getHamlet());
            sb = sb.replaceAll("\\Q<!\\E.*DOCTYPE.*\\Q-->\\E",
                "<!DOCTYPE PLAY PUBLIC \"-//PLAY//EN\" \"play.dtd\">" );

            org.exist.collections.Collection tmpCol = broker.getCollection(XmldbURI.create(VALIDATION_HOME_COLLECTION_URI + "/" + TestTools.VALIDATION_TMP_COLLECTION));
            storeDocument(broker, txn, tmpCol, "hamlet_valid.xml", sb);
            broker.saveCollection(txn, tmpCol);

            config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "no");

            org.exist.collections.Collection dtdCol = broker.getCollection(XmldbURI.create(VALIDATION_HOME_COLLECTION_URI + "/" + TestTools.VALIDATION_DTD_COLLECTION));
            storeTextDocument(broker, txn, dtdCol, "hamlet.dtd", new String(TestTools.loadSample("validation/dtd/hamlet.dtd")));
            storeDocument(broker, txn, dtdCol, "catalog.xml", new String(TestTools.loadSample("validation/dtd/catalog.xml")));
            broker.saveCollection(txn, dtdCol);

            org.exist.collections.Collection homeCol = broker.getCollection(XmldbURI.create(VALIDATION_HOME_COLLECTION_URI));
            storeDocument(broker, txn, homeCol, "hamlet_valid.xml", new String(TestTools.loadSample("validation/dtd/hamlet_valid.xml")));
            storeDocument(broker, txn, homeCol, "hamlet_invalid.xml", new String(TestTools.loadSample("validation/dtd/hamlet_invalid.xml")));
            broker.saveCollection(txn, homeCol);

            config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "yes");

            transact.commit(txn);
        } catch (Exception e) {
            if(transact != null && txn != null) {
                transact.abort(txn);
            }
            throw e;
        } finally {
            if(broker != null) {
                pool.release(broker);
            }
        }
    }

    private static void storeDocument(DBBroker broker, Txn txn, org.exist.collections.Collection collection, String name, String data) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        XmldbURI docUri  = XmldbURI.create(name);
        IndexInfo info = collection.validateXMLResource(txn, broker, docUri, data);
        collection.store(txn, broker, info, data, false);
    }

    private static void storeTextDocument(DBBroker broker, Txn txn, org.exist.collections.Collection collection, String name, String data) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        XmldbURI docUri  = XmldbURI.create(name);
        collection.addBinaryResource(txn, broker, docUri, data.getBytes(), "text/plain");
    }

    private static void removeTestCollections() throws Exception {

        BrokerPool pool = BrokerPool.getInstance();
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn txn = null;
        try {
            Subject admin = pool.getSecurityManager().authenticate(ADMIN_UID, ADMIN_PWD);

            broker = pool.get(admin);

            transact = pool.getTransactionManager();
            txn = transact.beginTransaction();

            org.exist.collections.Collection testCollection = broker.getOrCreateCollection(txn, XmldbURI.create(VALIDATION_HOME_COLLECTION_URI));
            broker.removeCollection(txn, testCollection);

            transact.commit(txn);
        } catch (Exception e) {
            if(transact != null && txn != null) {
                transact.abort(txn);
            }
            throw e;
        } finally {
            if(broker != null) {
                pool.release(broker);
            }
        }
    }
    /*

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

    @AfterClass
    public static void shutdownDB() throws Exception {

        logger.info("shutdownDB");
        
        DatabaseManager.deregisterDatabase(database);
        DatabaseInstanceManager dim =
            (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdownDB();
        
    }*/
    
}
