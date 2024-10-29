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
package org.exist.xquery.update;

import com.evolvedbinary.j8fu.function.Consumer2E;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.security.PermissionDeniedException;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.exist.test.Util.executeQuery;
import static org.exist.test.Util.withCompiledQuery;
import static org.exist.util.PropertiesBuilder.propertiesBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UpdateInsertTriggersDefragTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(propertiesBuilder().put(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR, -1).build(), true, true);

    @Before
    public void setUp() throws Exception {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            // store the test document in the test collection
            try (final Collection testCollection = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI)) {
                broker.storeDocument(transaction, TestConstants.TEST_XML_URI, new StringInputSource("<list><item>initial</item></list>"), MimeType.XML_TYPE, testCollection);
            }

            transaction.commit();
        }
    }

    @Test
    public void triggerDefragAfterUpdate() throws Exception {
        final String updateQuery = "update insert <item>new node</item> into doc('" + TestConstants.TEST_COLLECTION_URI + "/" + TestConstants.TEST_XML_URI + "')//list";
        assertQuery(updateQuery, updateResults ->
            assertTrue("Update expression returns an empty sequence", updateResults.isEmpty())
        );

        final String searchQuery = "doc('" + TestConstants.TEST_COLLECTION_URI + "/" + TestConstants.TEST_XML_URI + "')//item";
        assertQuery(searchQuery, searchResults ->
            assertEquals("Both items are returned", 2, searchResults.getItemCount())
        );
    }

    private void assertQuery(final String query, final Consumer2E<Sequence, XPathException, PermissionDeniedException> assertions) throws EXistException, XPathException, PermissionDeniedException, IOException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            withCompiledQuery(broker, new StringSource(query), compiledQuery -> {
                final Sequence results = executeQuery(broker, compiledQuery);
                assertions.accept(results);
                return null;
            });

            transaction.commit();
        }
    }
}
