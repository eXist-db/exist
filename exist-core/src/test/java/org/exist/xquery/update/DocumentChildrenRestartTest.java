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
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.exist.test.Util.executeQuery;
import static org.exist.test.Util.withCompiledQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nodes inserted around the document element of a stored document are still there, in order, after the
 * database is restarted. Inserting before the document element of a document without a prolog moves the
 * document element's own record after the new nodes. The DOM's sibling navigation reaches the inserted nodes,
 * and an update that fails part way leaves the document it already changed consistent.
 */
public class DocumentChildrenRestartTest {

    @RegisterExtension
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

    /**
     * Nodes inserted between two children of the document get node ids in between theirs: getNextSibling()
     * and getPreviousSibling() must reach them, in both directions.
     */
    @Test
    public void insertedNodesAreReachedBySiblingNavigation() throws Exception {
        storeDocument(XmldbURI.create("sibling-navigation.xml"), "<?p x?><document/><!--z-->");
        final String doc = "doc('" + TestConstants.TEST_COLLECTION_URI.append("sibling-navigation.xml") + "')";
        query("update insert comment { 'b' } preceding " + doc + "/document");
        query("update insert comment { 'a' } following " + doc + "/document");

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Collection collection = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.READ_LOCK);
                final LockedDocument lockedDoc = collection.getDocumentWithLock(broker, XmldbURI.create("sibling-navigation.xml"), LockMode.READ_LOCK)) {
            final DocumentImpl document = lockedDoc.getDocument();
            final List<String> forwards = new ArrayList<>();
            for(Node node = document.getFirstChild(); node != null; node = node.getNextSibling()) {
                forwards.add(describe(node));
            }
            final List<String> backwards = new ArrayList<>();
            for(Node node = document.getLastChild(); node != null; node = node.getPreviousSibling()) {
                backwards.add(describe(node));
            }
            assertEquals(List.of("pi:p", "comment:b", "element:document", "comment:a", "comment:z"), forwards);
            assertEquals(List.of("comment:z", "comment:a", "element:document", "comment:b", "pi:p"), backwards);
        }
    }

    /**
     * eXist does not undo the changes of an aborted transaction while it is running: an update that fails on
     * its second target keeps what it did to the first, for document-level nodes as for element content. What
     * matters is that the document stays consistent: its record of its children agrees with what is stored,
     * before and after a restart.
     */
    @Test
    public void anUpdateFailingPartWayLeavesTheDocumentConsistent() throws Exception {
        final String collection = TestConstants.TEST_COLLECTION_URI.toString();
        query("(sm:create-account('document-children-user', 'pw', ()), "
                + "for $name in ('changed.xml', 'read-only.xml') return xmldb:store('" + collection + "', $name, <a><b/></a>), "
                + "sm:chown(xs:anyURI('" + collection + "/changed.xml'), 'document-children-user'), "
                + "sm:chmod(xs:anyURI('" + collection + "/read-only.xml'), 'r--r--r--'))");
        final String changed = "doc('" + collection + "/changed.xml')";
        final String readOnly = "doc('" + collection + "/read-only.xml')";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Subject user = pool.getSecurityManager().authenticate("document-children-user", "pw");
        final XPathException e = assertThrows(XPathException.class, () ->
                query(user, "update insert comment { 'c' } preceding (" + changed + "/a, " + readOnly + "/a)"));
        assertTrue(e.getMessage().contains("does not have permission"), e.getMessage());

        final String state = "serialize(" + changed + ") || ' ' || count(" + changed + "/node()) || ' ' || count(" + changed + "/a/b)";
        assertEquals("<!--c--><a><b/></a> 2 1", query(state));
        assertEquals("<a><b/></a>", query("serialize(" + readOnly + ")"));
        existEmbeddedServer.restart();
        assertEquals("<!--c--><a><b/></a> 2 1", query(state));
    }

    private void storeDocument(final XmldbURI name, final String content) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction();
                final Collection collection = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI)) {
            broker.storeDocument(transaction, name, new StringInputSource(content), MimeType.XML_TYPE, collection);
            transaction.commit();
        }
    }

    private static String describe(final Node node) {
        return switch (node.getNodeType()) {
            case Node.COMMENT_NODE -> "comment:" + node.getNodeValue();
            case Node.PROCESSING_INSTRUCTION_NODE -> "pi:" + node.getNodeName();
            case Node.ELEMENT_NODE -> "element:" + node.getNodeName();
            default -> "other";
        };
    }

    private String query(final String query) throws EXistException, PermissionDeniedException, XPathException, IOException {
        return query(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject(), query);
    }

    private String query(final Subject subject, final String query) throws EXistException, PermissionDeniedException, XPathException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(subject));
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
