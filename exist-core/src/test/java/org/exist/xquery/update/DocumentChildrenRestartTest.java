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

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

import static org.exist.test.Util.executeQuery;
import static org.exist.test.Util.withCompiledQuery;
import static org.junit.Assert.assertEquals;

/**
 * Nodes inserted around the document element of a stored document are still there, in order, after the
 * database is restarted. Inserting before the document element of a document without a prolog moves the
 * document element's own record after the new nodes.
 */
public class DocumentChildrenRestartTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final XmldbURI DOCUMENT_URI = XmldbURI.create("document-children.xml");
    private static final String DOC = "doc('" + TestConstants.TEST_COLLECTION_URI.append(DOCUMENT_URI) + "')";

    @Test
    public void insertedNodesSurviveARestart() throws EXistException, PermissionDeniedException, IOException,
            XPathException, DatabaseConfigurationException, TriggerException, LockException, SAXException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction();
                final Collection collection = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI)) {
            broker.storeDocument(transaction, DOCUMENT_URI, new StringInputSource("<document><p>Contains stuff.</p></document>"),
                    MimeType.XML_TYPE, collection);
            transaction.commit();
        }

        query("update insert (processing-instruction a { 'x' }, comment { 'c' }) preceding " + DOC + "/document");
        query("update insert comment { 'z' } following " + DOC + "/document");

        existEmbeddedServer.restart();

        assertEquals("<?a x?><!--c--><document><p>Contains stuff.</p></document><!--z-->", query("serialize(" + DOC + ")"));
        assertEquals("Contains stuff.", query(DOC + "/document/p/string()"));
        assertEquals("c", query(DOC + "/comment()[1]/string()"));
    }

    private String query(final String query) throws EXistException, PermissionDeniedException, XPathException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final String result = withCompiledQuery(broker, new StringSource(query), compiledQuery -> {
                final Sequence results = executeQuery(broker, compiledQuery);
                return results.isEmpty() ? "" : results.getStringValue();
            });
            transaction.commit();
            return result;
        }
    }
}
