/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
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
 */
package org.exist.indexing.lucene;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.memtree.ElementImpl;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.junit.*;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SerializeAttrMatchesTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String COLLECTION_CONFIG =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
                    "	<index>" +
                    "       <lucene>" +
                    "           <text qname=\"@lemma\"/>" +
                    "       </lucene>" +
                    "	</index>" +
                    "</collection>";

    private static final String XML =
            "<s><w xml:id=\"VSK.P13.t1.p3.w231\">нас</w>\n" +
            "<w xml:id=\"VSK.P13.t1.p3.w233\">свакога</w>\n" +
            "<w xml:id=\"VSK.P13.t1.p3.w235\">Божића</w>\n" +
            "<w xml:id=\"VSK.P13.t1.p3.w237\">новом</w>\n" +
            "<w xml:id=\"VSK.P13.t1.p3.w239\" lemma=\"књига\">књигом</w>\n" +
            "<w xml:id=\"VSK.P13.t1.p3.w241\">пешкешите</w>. –</s>";

    private Collection test = null;

    @Test
    public void expandAttr() throws CollectionConfigurationException, LockException, IOException, SAXException, PermissionDeniedException, EXistException, XPathException {
        configureAndStore(COLLECTION_CONFIG, XML, "test1.xml");

        //query and expand
        final String query = "for $hit in collection(\"" + TestConstants.TEST_COLLECTION_URI.toString() + "\")//w[ft:query(@lemma, <query><regex>књиг.*</regex></query>)]\n" +
        "return util:expand($hit, \"highlight-matches=both\")";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final Sequence seq = xquery.execute(broker, query, null);

            assertEquals(1, seq.getItemCount());

            final Item item = seq.itemAt(0);
            assertTrue(item instanceof ElementImpl);
            assertEquals("lemma", ((ElementImpl)item).getAttribute("exist:matches"));
        }
    }

    private DocumentSet configureAndStore(final String configuration, final String data, final String docName) throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, TriggerException, LockException, IOException {
        final MutableDocumentSet docs = new DefaultDocumentSet();
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            if (configuration != null) {
                final CollectionConfigurationManager mgr = pool.getConfigurationManager();
                mgr.addConfiguration(transaction, broker, test, configuration);
            }

            final IndexInfo info = test.validateXMLResource(transaction, broker, XmldbURI.create(docName), data);
            assertNotNull(info);
            test.store(transaction, broker, info, data);

            docs.add(info.getDocument());
            transact.commit(transaction);
        }

        return docs;
    }

    @Before
    public void setup() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            broker.saveCollection(transaction, test);

            transact.commit(transaction);
        }
    }

    @After
    public void cleanup() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            final Collection collConfig = broker.getOrCreateCollection(transaction,
                    XmldbURI.create(XmldbURI.CONFIG_COLLECTION + "/db"));
            broker.removeCollection(transaction, collConfig);

            if (test != null) {
                broker.removeCollection(transaction, test);
            }
            transact.commit(transaction);
        }
    }

    @AfterClass
    public static void cleanupDb() throws LockException, TriggerException, PermissionDeniedException, EXistException, IOException {
        TestUtils.cleanupDB();
    }
}
