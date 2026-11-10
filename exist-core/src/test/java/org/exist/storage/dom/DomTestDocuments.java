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

import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.IStoredNode;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.btree.BTree;
import org.exist.storage.txn.Txn;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * A document that nests elements three levels below the index depth and holds a comment, a processing instruction,
 * text, an attribute and a CDATA section at each of them, and a comment and a processing instruction before and after
 * the root element, for the tests of the DOM B-tree entries.
 */
final class DomTestDocuments {

    /** An index depth above the code default of 1, so that the rules for a stored and a rebuilt entry differ for the nodes in the document. */
    static final int INDEX_DEPTH = 3;

    private DomTestDocuments() {
    }

    /**
     * Returns the configuration for an embedded server that uses {@link #INDEX_DEPTH}.
     *
     * @return the configuration properties
     */
    static Properties configuration() {
        final Properties configuration = new Properties();
        configuration.put("indexer.index-depth", INDEX_DEPTH);
        return configuration;
    }

    static String document(final int levels) {
        final StringBuilder xml = new StringBuilder("<!--before--><?before x?>");
        for (int l = 1; l <= levels; l++) {
            xml.append("<l").append(l).append(" a=\"").append(l).append("\"><!--c").append(l).append("-->t").append(l)
                    .append("<?pi").append(l).append(" x?><![CDATA[cdata").append(l).append("]]>");
        }
        for (int l = levels; l >= 1; l--) {
            xml.append("u").append(l).append("</l").append(l).append(">");
        }
        return xml.append("<!--after--><?after x?>").toString();
    }

    /**
     * Stores the document as {@code d.xml} in a new collection.
     *
     * @param pool the broker pool
     * @param broker the broker
     * @param collection the path of the collection
     * @throws Exception if the document cannot be stored
     */
    static void store(final BrokerPool pool, final DBBroker broker, final String collection) throws Exception {
        final int depth = ((NativeBroker) broker).getEntryPolicy().getIndexDepth();
        try (final Txn txn = pool.getTransactionManager().beginTransaction()) {
            final Collection col = broker.getOrCreateCollection(txn, XmldbURI.create(collection));
            broker.saveCollection(txn, col);
            broker.storeDocument(txn, XmldbURI.create("d.xml"), new StringInputSource(document(depth + 3)), MimeType.XML_TYPE, col);
            txn.commit();
        }
    }

    /**
     * Lists the nodes of a document in document order, each element followed by its attributes.
     *
     * @param doc the document
     * @return the stored nodes
     */
    static List<IStoredNode<?>> nodesOf(final DocumentImpl doc) {
        final List<IStoredNode<?>> nodes = new ArrayList<>();
        for (Node node = doc.getFirstChild(); node != null; node = node.getNextSibling()) {
            collect(node, nodes);
        }
        return nodes;
    }

    private static void collect(final Node node, final List<IStoredNode<?>> out) {
        out.add((IStoredNode<?>) node);
        final NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                out.add((IStoredNode<?>) attributes.item(i));
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            collect(child, out);
        }
    }

    static boolean hasEntry(final DBBroker broker, final DocumentImpl doc, final IStoredNode<?> node) throws Exception {
        final DOMFile domDb = ((NativeBroker) broker).getDOMFile();
        return domDb.findValue(new NativeBroker.NodeRef(doc.getDocId(), node.getNodeId())) != BTree.KEY_NOT_FOUND;
    }

    static String describe(final IStoredNode<?> node) {
        return "node " + node.getNodeId() + " of type " + node.getNodeType() + " at level " + node.getNodeId().getTreeLevel();
    }
}
