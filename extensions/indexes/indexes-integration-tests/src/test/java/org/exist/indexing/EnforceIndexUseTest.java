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
package org.exist.indexing;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.triggers.TriggerException;
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
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Optional;

import static org.exist.util.PropertiesBuilder.propertiesBuilder;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class EnforceIndexUseTest {
    @Parameterized.Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "always", 1 },
                { "strict", 3 }
        });
    }

    @Parameterized.Parameter
    public String enforceIndexUseValue;
    // always means the database will only get results from the collections indexed by range index it will ignore un-indexed collections
    // strict means it will get results from all the collections and will not ignore un-indexed

    @Parameterized.Parameter(value = 1)
    public int expectedSearchCount;

    private ExistEmbeddedServer existEmbeddedServer;
    private static final String OLD_RANGE_COLLECTION_CONFIG =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">\n" +
                    "    <index xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                    "        <create qname=\"@bar\" type=\"xs:string\"/>\n" +
                    "    </index>\n" +
                    "</collection>";
    private static final String NEW_RANGE_COLLECTION_CONFIG =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">\n" +
                    "    <index xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                    "        <range>\n" +
                    "            <create qname=\"@bar\" type=\"xs:string\"/>\n" +
                    "        </range>\n" +
                    "    </index>\n" +
                    "</collection>";

    private static final String XML =
            "<root>\n" +
                    "<foo bar=\"baz\"/>\n" +
                    "</root>";

    @Test
    public void matchesWithDiffrentIndexStyles() throws PermissionDeniedException, EXistException, XPathException {
        //query and expand
        final String query = "for $hit in collection(\"" + TestConstants.TEST_COLLECTION_URI.toString() + "\")//foo[matches(@bar, \"^b\")]\n" +
                "return $hit";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final Sequence seq = xquery.execute(broker, query, null);
            assertEquals(expectedSearchCount, seq.getItemCount());
        }
    }

    private DocumentSet configureAndStore(final String configuration, final String data, final String docName , String path) throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, LockException, IOException, URISyntaxException {
        final MutableDocumentSet docs = new DefaultDocumentSet();
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction();
            final Collection collection = broker.getOrCreateCollection(transaction, XmldbURI.xmldbUriFor(TestConstants.TEST_COLLECTION_URI + path))) {


            if (configuration != null) {
                final CollectionConfigurationManager mgr = pool.getConfigurationManager();
                mgr.addConfiguration(transaction, broker, collection, configuration);
            }
            broker.storeDocument(transaction, XmldbURI.create(docName), new StringInputSource(data), MimeType.XML_TYPE, collection);

            docs.add(collection.getDocument(broker, XmldbURI.create(docName)));
            transaction.commit();
        }

        return docs;
    }


    @Before
    public void setup() throws Throwable {
        existEmbeddedServer = new ExistEmbeddedServer(
                propertiesBuilder()
                        .put(XQueryContext.PROPERTY_ENFORCE_INDEX_USE, enforceIndexUseValue).build()
                ,true
                ,true);

        existEmbeddedServer.startDb();

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();

        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            try(final Collection test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                final Collection newRange = broker.getOrCreateCollection(transaction, XmldbURI.xmldbUriFor(TestConstants.TEST_COLLECTION_URI + "new-range-index"));
                final Collection oldRange = broker.getOrCreateCollection(transaction, XmldbURI.xmldbUriFor(TestConstants.TEST_COLLECTION_URI + "old-range-index"));
                final Collection noRange = broker.getOrCreateCollection(transaction, XmldbURI.xmldbUriFor(TestConstants.TEST_COLLECTION_URI + "no-range-index"))){

                broker.saveCollection(transaction, newRange);
                broker.saveCollection(transaction, test);
                broker.saveCollection(transaction, oldRange);
                broker.saveCollection(transaction, noRange);
            }

            transaction.commit();
        }

        // store the xml data and configure it
        configureAndStore(NEW_RANGE_COLLECTION_CONFIG, XML, "test.xml", "/new-range-index");
        configureAndStore(OLD_RANGE_COLLECTION_CONFIG, XML, "test.xml", "/old-range-index");
        configureAndStore(null, XML, "test.xml", "/no-range-index");

    }

    @After
    public void cleanup() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction();
            final Collection collConfig = broker.getOrCreateCollection(transaction, XmldbURI.create(XmldbURI.CONFIG_COLLECTION + "/db"))) {

            broker.removeCollection(transaction, collConfig);
            try(Collection test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI)) {
                if (test != null) {
                    broker.removeCollection(transaction, test);
                }
            }

            transaction.commit();
        }

        existEmbeddedServer.stopDb(true);
        existEmbeddedServer = null;
    }
}