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
package org.exist.storage.dom;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.IStoredNode;
import org.exist.dom.persistent.LockedDocument;
import org.exist.dom.persistent.NodeProxy;
import org.exist.numbering.NodeId;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.w3c.dom.Node;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every node of a stored document is found by its id when its address is not known, and an id that does not exist is not
 * found. A node without an entry in the DOM B-tree is found by walking the document from its nearest ancestor that has one.
 */
public class DomLookupTest {

    @RegisterExtension
    static final ExistEmbeddedServer SERVER = new ExistEmbeddedServer(DomTestDocuments.configuration(), true, true);

    @Test
    void everyNodeIsFoundByItsIdWithoutAnAddress() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            DomTestDocuments.store(pool, broker, "/db/entries-find");
            try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create("/db/entries-find/d.xml"), LockMode.READ_LOCK)) {
                final DocumentImpl doc = locked.getDocument();
                final List<IStoredNode<?>> nodes = DomTestDocuments.nodesOf(doc);
                assertTrue(nodes.size() > 4 * (DomTestDocuments.INDEX_DEPTH + 3), "the document has nodes of every kind: " + nodes.size());
                for (final IStoredNode<?> node : nodes) {
                    final IStoredNode<?> found = broker.objectWith(new NodeProxy(null, doc, node.getNodeId(), node.getNodeType()));
                    assertNotNull(found, DomTestDocuments.describe(node));
                    assertEquals(node.getNodeId(), found.getNodeId(), DomTestDocuments.describe(node));
                    assertEquals(node.getNodeType(), found.getNodeType(), DomTestDocuments.describe(node));
                    assertEquals(node.getNodeValue(), found.getNodeValue(), DomTestDocuments.describe(node));
                }
            }
        }
    }

    @Test
    void aMissingIdIsNotFound() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            DomTestDocuments.store(pool, broker, "/db/entries-missing");
            try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create("/db/entries-missing/d.xml"), LockMode.READ_LOCK)) {
                final DocumentImpl doc = locked.getDocument();
                IStoredNode<?> deepest = null;
                for (final IStoredNode<?> node : DomTestDocuments.nodesOf(doc)) {
                    if (deepest == null || node.getNodeId().getTreeLevel() > deepest.getNodeId().getTreeLevel()) {
                        deepest = node;
                    }
                }
                assertNotNull(deepest, "the document has nodes");
                final NodeId missing = deepest.getNodeId().newChild();
                assertNull(broker.objectWith(new NodeProxy(null, doc, missing, Node.ELEMENT_NODE)), "a child of a leaf node does not exist");
            }
        }
    }
}
