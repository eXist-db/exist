/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.EXistCollectionManagementService;
import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XUpdateQueryService;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;

/**
 * Tests recovery of XUpdate operations.
 * 
 * @author wolf
 *
 */
public class UpdateRecoverTest {
    
    private static String TEST_XML =
        "<?xml version=\"1.0\"?>" +
        "<products>" +
        "   <product id=\"0\">" +
        "       <description>Milk</description>" +
        "       <price>22.50</price>" +
        "   </product>" +
        "</products>";

    // we don't use @ClassRule/@Rule as we want to force corruption in some tests
    private ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void storeAndRead() throws IllegalAccessException, PermissionDeniedException, DatabaseConfigurationException, InstantiationException, SAXException, XMLDBException, EXistException, ClassNotFoundException, LockException, ParserConfigurationException, XPathException, IOException {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDb();
        store(pool);

        BrokerPool.FORCE_CORRUPTION = false;
        pool = restartDb();

        read(pool);
    }

    @Test
    public void storeAndReadXmldb() throws IllegalAccessException, DatabaseConfigurationException, InstantiationException, XMLDBException, EXistException, ClassNotFoundException, IOException {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = startDb();
        xmldbStore(pool);

        BrokerPool.FORCE_CORRUPTION = false;
        pool = restartDb();

        xmldbRead(pool);
    }

    private void store(final BrokerPool pool) throws EXistException, PermissionDeniedException, IOException, SAXException, LockException, ParserConfigurationException, XPathException {
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            DocumentImpl doc;
            try(final Txn transaction = transact.beginTransaction()) {

                final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(root);
                broker.saveCollection(transaction, root);

                final Collection test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
                assertNotNull(test2);
                broker.saveCollection(transaction, test2);

                broker.storeDocument(transaction, TestConstants.TEST_XML_URI, new StringInputSource(TEST_XML), MimeType.XML_TYPE, test2);
                doc = test2.getDocument(broker, TestConstants.TEST_XML_URI);
                //TODO : unlock the collection here ?

                transact.commit(transaction);
            }
            
            try(final Txn transaction = transact.beginTransaction()) {

                final MutableDocumentSet docs = new DefaultDocumentSet();
                docs.add(doc);
                final XUpdateProcessor proc = new XUpdateProcessor(broker, docs);
                assertNotNull(proc);
                // insert some nodes
                for (int i = 1; i <= 200; i++) {
                    final String xupdate =
                            "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                                    "   <xu:insert-before select=\"/products/product[1]\">" +
                                    "       <product>" +
                                    "           <description>Product " + i + "</description>" +
                                    "           <price>" + (i * 2.5) + "</price>" +
                                    "           <stock>" + (i * 10) + "</stock>" +
                                    "       </product>" +
                                    "   </xu:insert-before>" +
                                    "</xu:modifications>";
                    proc.setBroker(broker);
                    proc.setDocumentSet(docs);
                    final Modification modifications[] = proc.parse(new InputSource(new StringReader(xupdate)));
                    assertNotNull(modifications);
                    modifications[0].process(transaction);
                    proc.reset();
                }

                // add attribute
                for (int i = 1; i <= 200; i++) {
                    final String xupdate =
                            "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                                    "   <xu:append select=\"/products/product[" + i + "]\">" +
                                    "         <xu:attribute name=\"id\">" + i + "</xu:attribute>" +
                                    " </xu:append>" +
                                    "</xu:modifications>";
                    proc.setBroker(broker);
                    proc.setDocumentSet(docs);
                    final Modification modifications[] = proc.parse(new InputSource(new StringReader(xupdate)));
                    assertNotNull(modifications);
                    modifications[0].process(transaction);
                    proc.reset();
                }

                // replace some
                for (int i = 1; i <= 100; i++) {
                    final String xupdate =
                            "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                                    "   <xu:replace select=\"/products/product[" + i + "]\">" +
                                    "     <product id=\"" + i + "\">" +
                                    "         <description>Replaced product</description>" +
                                    "         <price>" + (i * 0.75) + "</price>" +
                                    "     </product>" +
                                    " </xu:replace>" +
                                    "</xu:modifications>";
                    proc.setBroker(broker);
                    proc.setDocumentSet(docs);
                    final Modification modifications[] = proc.parse(new InputSource(new StringReader(xupdate)));
                    assertNotNull(modifications);
                    long mods = modifications[0].process(transaction);
                    proc.reset();
                }

                // remove some
                for (int i = 1; i <= 100; i++) {
                    final String xupdate =
                            "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                                    "   <xu:remove select=\"/products/product[last()]\"/>" +
                                    "</xu:modifications>";
                    proc.setBroker(broker);
                    proc.setDocumentSet(docs);
                    final Modification modifications[] = proc.parse(new InputSource(new StringReader(xupdate)));
                    assertNotNull(modifications);
                    modifications[0].process(transaction);
                    proc.reset();
                }

                for (int i = 1; i <= 100; i++) {
                    final String xupdate =
                            "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                                    "   <xu:append select=\"/products\">" +
                                    "       <product>" +
                                    "           <xu:attribute name=\"id\"><xu:value-of select=\"count(/products/product) + 1\"/></xu:attribute>" +
                                    "           <description>Product " + i + "</description>" +
                                    "           <price>" + (i * 2.5) + "</price>" +
                                    "           <stock>" + (i * 10) + "</stock>" +
                                    "       </product>" +
                                    "   </xu:append>" +
                                    "</xu:modifications>";
                    proc.setBroker(broker);
                    proc.setDocumentSet(docs);
                    final Modification modifications[] = proc.parse(new InputSource(new StringReader(xupdate)));
                    assertNotNull(modifications);
                    modifications[0].process(transaction);
                    proc.reset();
                }

                // rename element "description" to "descript"
                String xupdate =
                        "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                                "   <xu:rename select=\"/products/product/description\">descript</xu:rename>" +
                                "</xu:modifications>";
                proc.setBroker(broker);
                proc.setDocumentSet(docs);
                Modification modifications[] = proc.parse(new InputSource(new StringReader(xupdate)));
                assertNotNull(modifications);
                modifications[0].process(transaction);
                proc.reset();

                // update attribute values
                for (int i = 1; i <= 200; i++) {
                    xupdate =
                            "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                                    "   <xu:update select=\"/products/product[" + i + "]/@id\">" + i + "u</xu:update>" +
                                    "</xu:modifications>";
                    proc.setBroker(broker);
                    proc.setDocumentSet(docs);
                    modifications = proc.parse(new InputSource(new StringReader(xupdate)));
                    assertNotNull(modifications);
                    long mods = modifications[0].process(transaction);
                    proc.reset();
                }

                // append new element to records
                for (int i = 1; i <= 200; i++) {
                    xupdate =
                            "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                                    "   <xu:append select=\"/products/product[" + i + "]\">" +
                                    "       <date><xu:value-of select=\"current-dateTime()\"/></date>" +
                                    "   </xu:append>" +
                                    "</xu:modifications>";
                    proc.setBroker(broker);
                    proc.setDocumentSet(docs);
                    modifications = proc.parse(new InputSource(new StringReader(xupdate)));
                    assertNotNull(modifications);
                    modifications[0].process(transaction);
                    proc.reset();
                }

                // update element content
                for (int i = 1; i <= 200; i++) {
                    xupdate =
                            "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                                    "   <xu:update select=\"/products/product[" + i + "]/price\">19.99</xu:update>" +
                                    "</xu:modifications>";
                    proc.setBroker(broker);
                    proc.setDocumentSet(docs);
                    modifications = proc.parse(new InputSource(new StringReader(xupdate)));
                    assertNotNull(modifications);
                    long mods = modifications[0].process(transaction);
                    proc.reset();
                }

                transact.commit(transaction);
            }
        }
    }

    private void read(final BrokerPool pool) throws IllegalAccessException, DatabaseConfigurationException, InstantiationException, ClassNotFoundException, XMLDBException, EXistException, PermissionDeniedException, SAXException {
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Serializer serializer = broker.borrowSerializer();

            try(final LockedDocument lockedDoc = broker.getXMLResource(TestConstants.TEST_COLLECTION_URI2.append(TestConstants.TEST_XML_URI), LockMode.READ_LOCK)) {
                assertNotNull("Document '" + XmldbURI.ROOT_COLLECTION + "/test/test2/test.xml' should not be null", lockedDoc);
                final String data = serializer.serialize(lockedDoc.getDocument());
                assertNotNull(data);
            } finally {
                broker.returnSerializer(serializer);
            }
        }
    }

    private void xmldbStore(final BrokerPool pool) throws IllegalAccessException, DatabaseConfigurationException, InstantiationException, ClassNotFoundException, XMLDBException, EXistException {
        final org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        assertNotNull(root);
        final EXistCollectionManagementService mgr = root.getService(EXistCollectionManagementService.class);
        assertNotNull(mgr);
        org.xmldb.api.base.Collection test = root.getChildCollection("test");
        if(test == null) {
            test = mgr.createCollection(TestConstants.TEST_COLLECTION_URI.toString());
        }
        assertNotNull(test);
        org.xmldb.api.base.Collection test2 = test.getChildCollection("test2");
        if(test2 == null) {
            test2 = mgr.createCollection(TestConstants.TEST_COLLECTION_URI2.toString());
        }
        assertNotNull(test2);
        final Resource res = test2.createResource("test_xmldb.xml", XMLResource.class);
        assertNotNull(res);
        res.setContent(TEST_XML);
        test2.storeResource(res);

        final XUpdateQueryService service = test2.getService(XUpdateQueryService.class);
        assertNotNull(service);

        // insert some nodes
        for(int i = 1; i <= 200; i++) {
            final String xupdate =
                "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                    "   <xu:insert-before select=\"/products/product[1]\">" +
                    "       <product>" +
                    "           <description>Product " + i + "</description>" +
                    "           <price>" + (i * 2.5) + "</price>" +
                    "           <stock>" + (i * 10) + "</stock>" +
                    "       </product>" +
                    "   </xu:insert-before>" +
                    "</xu:modifications>";
            service.updateResource("test_xmldb.xml", xupdate);
        }

        // add attribute
        for(int i = 1; i <= 200; i++) {
            final String xupdate =
                "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                    "   <xu:append select=\"/products/product[" + i + "]\">" +
                    "         <xu:attribute name=\"id\">" + i + "</xu:attribute>" +
                    " </xu:append>" +
                    "</xu:modifications>";
            service.updateResource("test_xmldb.xml", xupdate);
        }

        // replace some
        for(int i = 1; i <= 100; i++) {
            final String xupdate =
                "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                    "   <xu:replace select=\"/products/product[" + i + "]\">" +
                    "     <product id=\"" + i + "\">" +
                    "         <description>Replaced product</description>" +
                    "         <price>" + (i * 0.75) + "</price>" +
                    "     </product>" +
                    " </xu:replace>" +
                    "</xu:modifications>";
            service.updateResource("test_xmldb.xml", xupdate);
        }

        // remove some
        for(int i = 1; i <= 100; i++) {
            final String xupdate =
                "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                    "   <xu:remove select=\"/products/product[last()]\"/>" +
                    "</xu:modifications>";
            service.updateResource("test_xmldb.xml", xupdate);
        }

        for(int i = 1; i <= 100; i++) {
            final String xupdate =
                "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                    "   <xu:append select=\"/products\">" +
                    "       <product>" +
                    "           <xu:attribute name=\"id\"><xu:value-of select=\"count(/products/product) + 1\"/></xu:attribute>" +
                    "           <description>Product " + i + "</description>" +
                    "           <price>" + (i * 2.5) + "</price>" +
                    "           <stock>" + (i * 10) + "</stock>" +
                    "       </product>" +
                    "   </xu:append>" +
                    "</xu:modifications>";
            service.updateResource("test_xmldb.xml", xupdate);
        }

        // rename element "description" to "descript"
        String xupdate =
            "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                "   <xu:rename select=\"/products/product/description\">descript</xu:rename>" +
                "</xu:modifications>";
        service.updateResource("test_xmldb.xml", xupdate);

        // update attribute values
        for(int i = 1; i <= 200; i++) {
            xupdate =
                "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                    "   <xu:update select=\"/products/product[" + i + "]/@id\">" + i + "u</xu:update>" +
                    "</xu:modifications>";
            service.updateResource("test_xmldb.xml", xupdate);
        }

        // append new element to records
        for(int i = 1; i <= 200; i++) {
            xupdate =
                "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                    "   <xu:append select=\"/products/product[" + i + "]\">" +
                    "       <date><xu:value-of select=\"current-dateTime()\"/></date>" +
                    "   </xu:append>" +
                    "</xu:modifications>";
            service.updateResource("test_xmldb.xml", xupdate);
        }

        // update element content
        for(int i = 1; i <= 200; i++) {
            xupdate =
                "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                    "   <xu:update select=\"/products/product[" + i + "]/price\">19.99</xu:update>" +
                    "</xu:modifications>";
            service.updateResource("test_xmldb.xml", xupdate);
        }
    }

    private void xmldbRead(final BrokerPool pool) throws XMLDBException {
        final org.xmldb.api.base.Collection test2 = DatabaseManager.getCollection("xmldb:exist://" + TestConstants.TEST_COLLECTION_URI2, "admin", "");
        assertNotNull(test2);
        final Resource res = test2.getResource("test_xmldb.xml");
        assertNotNull("Document should not be null", res);

        final org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        assertNotNull(root);
        final EXistCollectionManagementService mgr = root.getService(EXistCollectionManagementService.class);
        assertNotNull(mgr);
        mgr.removeCollection("test");
    }

    private BrokerPool startDb() throws EXistException, IOException, DatabaseConfigurationException, XMLDBException {
        existEmbeddedServer.startDb();

        final Database database = new DatabaseImpl();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        return existEmbeddedServer.getBrokerPool();
    }

    private BrokerPool restartDb() throws DatabaseConfigurationException, IOException, EXistException {
        existEmbeddedServer.restart(false);
        return existEmbeddedServer.getBrokerPool();
    }

    @After
    public void stopDb() {
        existEmbeddedServer.stopDb();
    }

}
