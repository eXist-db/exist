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
package org.exist.xquery.value;

import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.ReferenceNode;
import org.exist.dom.persistent.NodeProxy;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.Cardinality;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.junit.ClassRule;
import org.junit.Test;

import javax.xml.XMLConstants;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link SequenceType#checkType(Item)} of nodes whose type is only known as {@link Type#NODE}:
 * an in-memory reference to a stored node, and a stored node whose kind has not been read yet.
 * Their kind comes from {@link org.w3c.dom.Node#getNodeType()}, which is a DOM node kind, not a
 * {@link Type} code.
 */
public class SequenceTypeNodeKindTest {

    @ClassRule
    public static final ExistEmbeddedServer SERVER = new ExistEmbeddedServer(true, true);

    private static final String STORE_AND_SELECT =
            "xmldb:create-collection('/db', 'sequence-type-node-kind'), "
            + "xmldb:store('/db/sequence-type-node-kind', 'test.xml', <r><a>x</a></r>), "
            + "doc('/db/sequence-type-node-kind/test.xml')/r/a";

    private static SequenceType element(final String name) {
        final SequenceType type = new SequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE);
        if (name != null) {
            type.setNodeName(new QName(name, XMLConstants.NULL_NS_URI));
        }
        return type;
    }

    private static NodeProxy storedElement(final DBBroker broker) throws Exception {
        final XQuery xquery = broker.getBrokerPool().getXQueryService();
        final Sequence result = xquery.execute(broker, STORE_AND_SELECT, null);
        return (NodeProxy) result.itemAt(result.getItemCount() - 1);
    }

    @Test
    public void referenceToAStoredElement() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final NodeProxy stored = storedElement(broker);
            final MemTreeBuilder builder = new MemTreeBuilder(new XQueryContext(pool));
            builder.startDocument();
            builder.addReferenceNode(stored);
            builder.endDocument();
            final DocumentImpl doc = builder.getDocument();
            final ReferenceNode reference = (ReferenceNode) doc.getNode(1);
            assertEquals(Type.NODE, reference.getType());

            // failed with ArrayIndexOutOfBoundsException: the reference node's kind, 100, was used as a Type code
            assertTrue(element(null).checkType((Item) reference));
            assertTrue(element("a").checkType((Item) reference));
            assertFalse(element("b").checkType((Item) reference));
            assertFalse(new SequenceType(Type.TEXT, Cardinality.EXACTLY_ONE).checkType((Item) reference));
        }
    }

    @Test
    public void storedElementOfUnknownKind() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final NodeProxy stored = storedElement(broker);
            final NodeProxy unknownKind = new NodeProxy(null, stored.getOwnerDocument(), stored.getNodeId());
            assertEquals(Type.NODE, unknownKind.getType());

            // failed: the element's DOM kind, 1, was taken for Type.ITEM
            assertTrue(element(null).checkType((Item) unknownKind));
            assertTrue(element("a").checkType((Item) unknownKind));
            assertFalse(new SequenceType(Type.TEXT, Cardinality.EXACTLY_ONE).checkType((Item) unknownKind));
        }
    }
}
