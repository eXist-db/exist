/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.dom.persistent;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.ClassRule;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertFalse;

public class CommentTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void commentContentNotEscaped() throws EXistException, PermissionDeniedException, LockException, SAXException, IOException {
        final XmldbURI docUri = XmldbURI.create("comments.xml");
        final String xml = "<root><!-- text <a> &lt;b&gt;  --></root>";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction();
             final Collection collection = broker.openCollection(XmldbURI.ROOT_COLLECTION_URI, Lock.LockMode.WRITE_LOCK)) {

            final IndexInfo indexInfo = collection.validateXMLResource(transaction, broker, docUri, xml);
            collection.store(transaction, broker, indexInfo, xml);

            transaction.commit();
        }


        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            try (final LockedDocument lockedDocument = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append(docUri), Lock.LockMode.READ_LOCK)) {
                final Document doc = lockedDocument.getDocument();

                final Diff diff = DiffBuilder.compare(xml)
                        .withTest(doc)
                        .checkForSimilar()
                        .build();

                assertFalse(diff.toString(), diff.hasDifferences());
            }

            transaction.commit();
        }
    }
}
