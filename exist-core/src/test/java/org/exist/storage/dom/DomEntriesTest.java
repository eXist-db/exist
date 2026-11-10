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
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.w3c.dom.Node;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which nodes of a stored document have an entry in the DOM B-tree, after a store, after a reindex that stores and after
 * an update, written out per node kind and level. The rules for a stored and a rebuilt entry differ for comments and
 * processing instructions below the root level, and the test shows it on a real document.
 *
 * <p>The document ({@link DomTestDocuments}) has an index depth of 3 and nests elements three levels below it, with an
 * attribute, a comment, text, a processing instruction and a CDATA section at every level, and a comment and a
 * processing instruction before and after the root element. A node is named by its kind and level, {@code comment@2}
 * for a comment that is a child of the root element.</p>
 */
public class DomEntriesTest {

    /** The deepest level of the document: the index depth and the three levels below it. */
    private static final int LEVELS = DomTestDocuments.INDEX_DEPTH + 3;

    private static final List<String> KINDS = List.of("element", "attribute", "comment", "text", "pi", "cdata");

    /**
     * After a store: elements, comments and processing instructions down to level 3 have an entry; nothing below level 3
     * has one, and text, attributes and CDATA sections never do.
     */
    private static final Set<String> AFTER_STORE = Set.of(
            "element@1", "element@2", "element@3",
            "comment@1", "comment@2", "comment@3",
            "pi@1", "pi@2", "pi@3");

    /**
     * After a reindex that stores, and for nodes inserted by an update: everything at level 1 (#1691) and elements down
     * to level 3 have an entry; a comment or processing instruction below level 1 has none, within the index depth or not.
     */
    private static final Set<String> AFTER_REINDEX = Set.of(
            "element@1", "element@2", "element@3",
            "comment@1", "pi@1");

    @RegisterExtension
    static final ExistEmbeddedServer SERVER = new ExistEmbeddedServer(DomTestDocuments.configuration(), true, true);

    private static void update(final BrokerPool pool, final DBBroker broker, final String query) throws Exception {
        pool.getXQueryService().execute(broker, query, null);
    }

    private static String kind(final IStoredNode<?> node) {
        return switch (node.getNodeType()) {
            case Node.ELEMENT_NODE -> "element";
            case Node.ATTRIBUTE_NODE -> "attribute";
            case Node.COMMENT_NODE -> "comment";
            case Node.TEXT_NODE -> "text";
            case Node.PROCESSING_INSTRUCTION_NODE -> "pi";
            case Node.CDATA_SECTION_NODE -> "cdata";
            default -> throw new AssertionError("unexpected node type " + node.getNodeType() + " at " + node.getNodeId());
        };
    }

    private static String name(final IStoredNode<?> node) {
        return kind(node) + "@" + node.getNodeId().getTreeLevel();
    }

    /**
     * Checks that exactly the expected nodes have an entry, and returns the names of the nodes that were checked.
     */
    private static Set<String> checkEntries(final DBBroker broker, final DocumentImpl doc, final List<IStoredNode<?>> nodes,
            final Set<String> expected) throws Exception {
        final Set<String> seen = new HashSet<>();
        for (final IStoredNode<?> node : nodes) {
            final String name = name(node);
            assertEquals(expected.contains(name), DomTestDocuments.hasEntry(broker, doc, node), name + ": " + DomTestDocuments.describe(node));
            seen.add(name);
        }
        return seen;
    }

    /**
     * Checks that the document has every kind of node at every level, so that each line of the expectations was tested.
     */
    private static void assertWholeDocumentSeen(final Set<String> seen) {
        final Set<String> wanted = new HashSet<>();
        for (int level = 1; level <= LEVELS; level++) {
            for (final String kind : KINDS) {
                // an element's attribute, comment, text, instruction and CDATA section are one level below it
                if (level > 1 || kind.equals("element") || kind.equals("comment") || kind.equals("pi")) {
                    wanted.add(kind + "@" + level);
                }
            }
        }
        wanted.removeAll(seen);
        assertTrue(wanted.isEmpty(), "every kind of node at every level was checked, missing: " + wanted);
    }

    @Test
    void storingGivesEntriesByTheStoreRule() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            assertEquals(DomTestDocuments.INDEX_DEPTH, ((NativeBroker) broker).getEntryPolicy().getIndexDepth(), "the configured index depth took effect");
            DomTestDocuments.store(pool, broker, "/db/entries-store");
            try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create("/db/entries-store/d.xml"), LockMode.READ_LOCK)) {
                final DocumentImpl doc = locked.getDocument();
                assertWholeDocumentSeen(checkEntries(broker, doc, DomTestDocuments.nodesOf(doc), AFTER_STORE));
            }
        }
    }

    @Test
    void aReindexThatStoresGivesEntriesByTheReindexRule() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            assertEquals(DomTestDocuments.INDEX_DEPTH, ((NativeBroker) broker).getEntryPolicy().getIndexDepth(), "the configured index depth took effect");
            DomTestDocuments.store(pool, broker, "/db/entries-reindex");
            update(pool, broker, "xmldb:reindex('/db/entries-reindex')");
            try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create("/db/entries-reindex/d.xml"), LockMode.READ_LOCK)) {
                final DocumentImpl doc = locked.getDocument();
                assertWholeDocumentSeen(checkEntries(broker, doc, DomTestDocuments.nodesOf(doc), AFTER_REINDEX));
            }
        }
    }

    @Test
    void anUpdateGivesTheInsertedNodesEntriesByTheReindexRule() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            DomTestDocuments.store(pool, broker, "/db/entries-update");
            // the inserted element becomes the last child of the root, at level 2; what follows it at level 1 is the comment and the instruction after the root
            update(pool, broker, "update insert <added a=\"x\"><!--c-->t<?p x?><inner><!--d-->u</inner></added> into doc('/db/entries-update/d.xml')/l1");
            try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create("/db/entries-update/d.xml"), LockMode.READ_LOCK)) {
                final DocumentImpl doc = locked.getDocument();
                final List<IStoredNode<?>> nodes = DomTestDocuments.nodesOf(doc);
                int first = -1;
                for (int i = 0; i < nodes.size() && first < 0; i++) {
                    if (nodes.get(i).getNodeType() == Node.ELEMENT_NODE && "added".equals(nodes.get(i).getNodeName())) {
                        first = i;
                    }
                }
                assertTrue(first >= 0, "the inserted element is in the document");
                final List<IStoredNode<?>> inserted = nodes.subList(first, nodes.size()).stream()
                        .filter(node -> node.getNodeId().getTreeLevel() >= 2).toList();
                final Set<String> seen = checkEntries(broker, doc, inserted, AFTER_REINDEX);
                assertEquals(Set.of("element@2", "attribute@3", "comment@3", "text@3", "pi@3", "element@3", "comment@4", "text@4"), seen,
                        "the inserted element and all of its content were checked");
            }
        }
    }
}
