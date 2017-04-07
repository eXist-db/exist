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
import java.util.Optional;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.Configuration;
import org.exist.util.LockException;

import org.junit.*;

import static org.exist.util.PropertiesBuilder.propertiesBuilder;
import static org.junit.Assert.*;

import org.exist.collections.IndexInfo;

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
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
 * Set of Tests for validation:validate($a) and validation:validate($a, $b)
 * regarding validating using DTD's.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class ValidationFunctions_DTD_Test {
    
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

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(
            propertiesBuilder()
                    .set(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "auto")
                    .build(),
            true,
            false);

    @BeforeClass
    public static void startup() throws Exception {
        //create the collections we need for these tests
        createTestCollections();

        //create the documents we need for the tests
        createTestDocuments();

        //get xmldb xpath query service
        service = getXPathService();
    }

    @Before
    public void clearGrammarCache() throws XMLDBException {
        service.query("validation:clear-grammar-cache()");
    }

    @AfterClass
    public static void shutdown() throws Exception {
        removeTestCollections();
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

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().authenticate(ADMIN_UID, ADMIN_PWD)));
            final Txn txn = transact.beginTransaction()) {

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
        }
    }

    private static void createTestDocuments() throws Exception {

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Configuration config = pool.getConfiguration();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getGuestSubject()));
            final Txn txn = transact.beginTransaction()) {

            /** create necessary documents  */

            //hamlet
            String sb = new String(TestUtils.readHamletSampleXml());
            sb = sb.replaceAll("\\Q<!\\E.*DOCTYPE.*\\Q-->\\E",
                "<!DOCTYPE PLAY PUBLIC \"-//PLAY//EN\" \"play.dtd\">" );

            org.exist.collections.Collection tmpCol = broker.getCollection(XmldbURI.create(VALIDATION_HOME_COLLECTION_URI + "/" + TestTools.VALIDATION_TMP_COLLECTION));
            storeDocument(broker, txn, tmpCol, "hamlet_valid.xml", sb);
            broker.saveCollection(txn, tmpCol);

            config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "no");

            org.exist.collections.Collection dtdCol = broker.getCollection(XmldbURI.create(VALIDATION_HOME_COLLECTION_URI + "/" + TestTools.VALIDATION_DTD_COLLECTION));
            storeTextDocument(broker, txn, dtdCol, "hamlet.dtd", new String(TestUtils.readSample("validation/dtd/hamlet.dtd")));
            storeDocument(broker, txn, dtdCol, "catalog.xml", new String(TestUtils.readSample("validation/dtd/catalog.xml")));
            broker.saveCollection(txn, dtdCol);

            org.exist.collections.Collection homeCol = broker.getCollection(XmldbURI.create(VALIDATION_HOME_COLLECTION_URI));
            storeDocument(broker, txn, homeCol, "hamlet_valid.xml", new String(TestUtils.readSample("validation/dtd/hamlet_valid.xml")));
            storeDocument(broker, txn, homeCol, "hamlet_invalid.xml", new String(TestUtils.readSample("validation/dtd/hamlet_invalid.xml")));
            broker.saveCollection(txn, homeCol);

            config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "yes");

            transact.commit(txn);
        }
    }

    private static void storeDocument(DBBroker broker, Txn txn, org.exist.collections.Collection collection, String name, String data) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        XmldbURI docUri  = XmldbURI.create(name);
        IndexInfo info = collection.validateXMLResource(txn, broker, docUri, data);
        collection.store(txn, broker, info, data);
    }

    private static void storeTextDocument(DBBroker broker, Txn txn, org.exist.collections.Collection collection, String name, String data) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        XmldbURI docUri  = XmldbURI.create(name);
        collection.addBinaryResource(txn, broker, docUri, data.getBytes(), "text/plain");
    }

    private static void removeTestCollections() throws Exception {

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().authenticate(ADMIN_UID, ADMIN_PWD)));
             final Txn txn = transact.beginTransaction()) {

            org.exist.collections.Collection testCollection = broker.getOrCreateCollection(txn, XmldbURI.create(VALIDATION_HOME_COLLECTION_URI));
            broker.removeCollection(txn, testCollection);

            transact.commit(txn);
        }
    }
}
