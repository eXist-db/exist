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
 * $Id: DatabaseInsertResources_NoValidation_Test.java 5986 2007-06-03 15:39:39Z dizzzz $
 */
package org.exist.validation;

import java.io.IOException;
import org.exist.collections.Collection;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *  Insert documents for validation tests.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class DatabaseInsertResources_NoValidation_Test {
    
    private static Configuration config;

    private final static String TEST_COLLECTION = "testNoValidationInsert";

    private final static String ADMIN_UID = "admin";
    private final static String ADMIN_PWD = "";

    private final static String GUEST_UID = "guest";

    private final static String VALIDATION_HOME_COLLECTION_URI = "/db/" + TEST_COLLECTION + "/" + TestTools.VALIDATION_HOME_COLLECTION;
    

    /**
     * Insert all documents into database, switch of validation.
     */
    @Test
    public void insertValidationResources_xsd() throws IOException {
        config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "no");
        String addressbook = TestTools.getEXistHome() + "/samples/validation/addressbook";

        TestTools.insertDocumentToURL(addressbook + "/addressbook.xsd",
            "xmldb:exist://" + VALIDATION_HOME_COLLECTION_URI + "/" + TestTools.VALIDATION_XSD_COLLECTION + "/addressbook.xsd");


        TestTools.insertDocumentToURL(addressbook+"/catalog.xml",
            "xmldb:exist://" + VALIDATION_HOME_COLLECTION_URI + "/" + TestTools.VALIDATION_XSD_COLLECTION + "/catalog.xml");

        TestTools.insertDocumentToURL(addressbook+"/addressbook_valid.xml",
            "xmldb:exist://" + VALIDATION_HOME_COLLECTION_URI + "/addressbook_valid.xml");

        TestTools.insertDocumentToURL(addressbook+"/addressbook_invalid.xml",
            "xmldb:exist://" + VALIDATION_HOME_COLLECTION_URI + "/addressbook_invalid.xml");
    }

    @Test
    public void insertValidationResources_dtd() throws IOException {
        config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "no");
        String hamlet = TestTools.getEXistHome() + "/samples/validation/dtd";

        TestTools.insertDocumentToURL(hamlet+"/hamlet.dtd",
            "xmldb:exist://" + VALIDATION_HOME_COLLECTION_URI + "/" + TestTools.VALIDATION_DTD_COLLECTION + "/hamlet.dtd");

        TestTools.insertDocumentToURL(hamlet+"/catalog.xml",
            "xmldb:exist://" + VALIDATION_HOME_COLLECTION_URI + "/" + TestTools.VALIDATION_DTD_COLLECTION + "/catalog.xml");

        TestTools.insertDocumentToURL(hamlet+"/hamlet_valid.xml",
            "xmldb:exist://" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_valid.xml");

        TestTools.insertDocumentToURL(hamlet+"/hamlet_invalid.xml",
            "xmldb:exist://" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_invalid.xml");
    }

    @Test
    public void insertValidationResource_dtd_badDocType() throws IOException {
        config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "no");
        String hamlet = TestTools.getEXistHome() + "/samples/validation/dtd";

        TestTools.insertDocumentToURL(hamlet + "/hamlet_nodoctype.xml",
            "xmldb:exist://" + VALIDATION_HOME_COLLECTION_URI +"/hamlet_nodoctype.xml");

        TestTools.insertDocumentToURL(hamlet + "/hamlet_wrongdoctype.xml",
            "xmldb:exist://" + VALIDATION_HOME_COLLECTION_URI +"/hamlet_wrongdoctype.xml");
    }

    @BeforeClass
    public static void startup() throws Exception {
        config = new Configuration();
        config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "auto");

        BrokerPool.configure(1, 5, config);

        //create the collections we need for these tests
        createTestCollections();
    }

    @AfterClass
    public static void shutdown() throws Exception {
        removeTestCollections();
        BrokerPool.stopAll(true);
    }

    private static void createTestCollections() throws Exception {

        final BrokerPool pool = BrokerPool.getInstance();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().authenticate(ADMIN_UID, ADMIN_PWD));
                final Txn txn = transact.beginTransaction()) {

            /** create nessecary collections if they dont exist */
            Collection testCollection = broker.getOrCreateCollection(txn, XmldbURI.create(VALIDATION_HOME_COLLECTION_URI));
            testCollection.getPermissions().setOwner(GUEST_UID);
            broker.saveCollection(txn, testCollection);

            Collection col = broker.getOrCreateCollection(txn, XmldbURI.create(VALIDATION_HOME_COLLECTION_URI + "/" + TestTools.VALIDATION_DTD_COLLECTION));
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

    private static void removeTestCollections() throws Exception {

        final BrokerPool pool = BrokerPool.getInstance();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().authenticate(ADMIN_UID, ADMIN_PWD));
            final Txn txn = transact.beginTransaction()) {

            /** create nessecary collections if they dont exist */
            Collection testCollection = broker.getOrCreateCollection(txn, XmldbURI.create(VALIDATION_HOME_COLLECTION_URI));
            broker.removeCollection(txn, testCollection);

            transact.commit(txn);
        }
    }
}
